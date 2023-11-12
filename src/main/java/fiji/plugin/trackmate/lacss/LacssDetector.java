package fiji.plugin.trackmate.lacss;

import static fiji.plugin.trackmate.detection.DetectorKeys.DEFAULT_TARGET_CHANNEL;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_TARGET_CHANNEL;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.scijava.Cancelable;

import com.google.protobuf.ByteString;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.detection.LabelImageDetectorFactory;
import fiji.plugin.trackmate.detection.SpotGlobalDetector;
import fiji.plugin.trackmate.util.TMUtils;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.MultiThreaded;
import net.imglib2.converter.RealTypeConverters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.display.imagej.ImgPlusViews;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class LacssDetector< T extends RealType< T > & NativeType< T > > implements SpotGlobalDetector< T >, Cancelable, MultiThreaded
{
	private final static String BASE_ERROR_MESSAGE = "LacssDetector: ";

	protected final ImgPlus< T > img;

	protected final Interval interval;

	private final LacssSettings lacssSettings;

	private final Process pyServer;

	private final Logger logger;

	protected String baseErrorMessage;

	protected String errorMessage;

	protected long processingTime;

	protected SpotCollection spots;

	private String cancelReason;

	private boolean isCanceled;

	public LacssDetector(
			final ImgPlus< T > img,
			final Interval interval,
			final LacssSettings lacssSettings,
			final Logger logger,
			final Process pyServer )
	{
		this.img = img;
		this.interval = interval;
		this.lacssSettings = lacssSettings;
		this.logger = ( logger == null ) ? Logger.VOID_LOGGER : logger;
		this.baseErrorMessage = BASE_ERROR_MESSAGE;
		this.pyServer = pyServer;
	}

	private void writeInput(DataOutputStream st, RandomAccessibleInterval<T> crop, LacssMsg.Settings settings) throws IOException {
		long [] dims = crop.dimensionsAsLongArray();
		long n_ch = 1;
		if (img.dimensionIndex( Axes.CHANNEL ) != -1) {
			n_ch = dims[img.dimensionIndex( Axes.CHANNEL )];
		}
		if (img.dimensionIndex( Axes.Z ) != -1) {
			n_ch = n_ch * dims[img.dimensionIndex( Axes.Z )];
		}
		final long height = dims[img.dimensionIndex( Axes.Y )];
		final long width = dims[img.dimensionIndex( Axes.X )];

		ByteBuffer data = ByteBuffer.allocate((int)(width * height * n_ch * Float.BYTES));
		RandomAccessibleInterval<FloatType> floatImg = RealTypeConverters.convert(crop, new FloatType());
		LoopBuilder.setImages(floatImg).flatIterationOrder().forEachPixel(p -> data.putFloat((Float)p.get()));

		LacssMsg.Image encoded_img = LacssMsg.Image.newBuilder()
			.setWidth(width)
			.setHeight(height)
			.setChannel(n_ch)
			.setData(ByteString.copyFrom(data.array()))
			.build();
		
		LacssMsg.Input msg = LacssMsg.Input.newBuilder()
			.setImage(encoded_img)
			.setSettings(settings)
			.build();

		st.writeInt(msg.getSerializedSize());
		msg.writeTo(st);
	}

	private ArrayImg<ShortType, ShortArray> readResult(DataInputStream st) throws IOException {
		int msg_size = st.readInt();

		byte [] msg_buf = new byte[msg_size];

		st.readFully(msg_buf);

		LacssMsg.Result msg = LacssMsg.Result.parseFrom(msg_buf);

		long [] dims = new long[]{msg.getWidth(), msg.getHeight()};
		short[] data = new short[(int)msg.getHeight() * (int)msg.getWidth()];
		msg.getData().asReadOnlyByteBuffer().asShortBuffer().get(data);

		ArrayImg<ShortType, ShortArray> label = ArrayImgs.shorts(data, dims);
		
		return label;
	}

	private ArrayImg<ShortType, ShortArray> processFrame(RandomAccessibleInterval<T> frame, DataInputStream p_in, DataOutputStream p_out) throws IOException
	{
		LacssMsg.Settings settingMsg = LacssMsg.Settings.newBuilder()
			.setDetectionThreshold((float)lacssSettings.detection_threshold)
			.setSegmentationThreshold((float)lacssSettings.segmentation_threshold)
			.setMinCellArea((float)lacssSettings.min_cell_area)
			.setNmsIou((float)lacssSettings.nms_iou)
			.setScaling((float)lacssSettings.scaling)
			.build();

		writeInput(p_out, frame, settingMsg);
		ArrayImg<ShortType, ShortArray> label = readResult(p_in); //blocking

		return label;
	}

	private Interval getCropInterval() 
	{
		final int zIndex = img.dimensionIndex( Axes.Z );
		final int cIndex = img.dimensionIndex( Axes.CHANNEL );
		final Interval cropInterval;
		if ( zIndex < 0 )
		{
			// 2D
			if ( cIndex < 0 )
				cropInterval = Intervals.createMinMax(
						interval.min( 0 ), interval.min( 1 ),
						interval.max( 0 ), interval.max( 1 ) );
			else
				// Include all channels
				cropInterval = Intervals.createMinMax(
						interval.min( 0 ), interval.min( 1 ), img.min( cIndex ),
						interval.max( 0 ), interval.max( 1 ), img.max( cIndex ) );
		}
		else
		{
			if ( cIndex < 0 )
				cropInterval = Intervals.createMinMax(
						interval.min( 0 ), interval.min( 1 ), interval.min( 2 ),
						interval.max( 0 ), interval.max( 1 ), interval.max( 2 ) );
			else
				cropInterval = Intervals.createMinMax(
						interval.min( 0 ), interval.min( 1 ), interval.min( 2 ), img.min( cIndex ),
						interval.max( 0 ), interval.max( 1 ), interval.max( 2 ), img.max( cIndex ) );
		}

		return cropInterval;
	}

	@Override
	public boolean process()
	{
		final long start = System.currentTimeMillis();
		// final Tailer tailer = Tailer.create( LACSS_LOG_FILE, new LoggerTailerListener( logger ), 200, true );
		final double[] calibration = TMUtils.getSpatialCalibration( img );

		isCanceled = false;
		cancelReason = null;

		DataOutputStream p_out = new DataOutputStream(pyServer.getOutputStream());
		DataInputStream p_in = new DataInputStream(pyServer.getInputStream());

		final Interval cropInterval = getCropInterval(); // construct a crop interval without the t axis

		final int timeIndex = img.dimensionIndex( Axes.TIME );
		Img<ShortType> label;
		
		final long minT, maxT;
		final double frameInterval;

		logger.log( "Running segmentation.\n" );
		if ( timeIndex < 0 ) // single frame
		{
			final IntervalView< T > crop = Views.interval(img, cropInterval );
			try {
				label = processFrame(crop, p_in, p_out);
			} catch (IOException e) {
				errorMessage = e.getLocalizedMessage();
				return false;
			}
			minT = 0;
			maxT = 0;
			frameInterval = 1.0;
		}
		else 
		{
			long [] dims = interval.dimensionsAsLongArray();
			label = ArrayImgs.shorts(
				dims[img.dimensionIndex(Axes.X)],
				dims[img.dimensionIndex(Axes.Y)],
				dims[timeIndex]
			);

			// Hackish
			minT = interval.min( interval.numDimensions() - 1 );
			maxT = interval.max( interval.numDimensions() - 1 );
			frameInterval = img.averageScale( timeIndex );
			
			for ( long t = minT; t <= maxT; t++ )
			{
				if (isCanceled) {
					return false;
				}

				final ImgPlus< T > tp = ImgPlusViews.hyperSlice( img, timeIndex, t );
				final IntervalView< T > crop = Views.interval( tp, cropInterval );
				Img<ShortType> labelFrame;
				try {
					labelFrame = processFrame(crop, p_in, p_out);
				} catch (IOException e) {
					errorMessage = e.getLocalizedMessage();
					return false;
				}

				LoopBuilder
					.setImages(labelFrame, Views.hyperSlice(label, 2, t))
					.forEachPixel( (src, dst) -> dst.set(src.get()) );
			}
		}

		/*
		 * Run in the label detector.
		 */

		logger.log( "Converting masks to spots.\n" );
		final Settings labelImgSettings = new Settings( ImageJFunctions.wrap(label, "labels") );
		final LabelImageDetectorFactory< ? > labeImageDetectorFactory = new LabelImageDetectorFactory<>();
		final Map< String, Object > detectorSettings = labeImageDetectorFactory.getDefaultSettings();
		detectorSettings.put( KEY_TARGET_CHANNEL, DEFAULT_TARGET_CHANNEL );
		// detectorSettings.put( KEY_SIMPLIFY_CONTOURS, remove_out_of_bound );
		labelImgSettings.detectorFactory = labeImageDetectorFactory;
		labelImgSettings.detectorSettings = detectorSettings;

		final TrackMate labelImgTrackMate = new TrackMate( labelImgSettings );
		// labelImgTrackMate.setNumThreads( 1 );
		if ( !labelImgTrackMate.execDetection() )
		{
			errorMessage = BASE_ERROR_MESSAGE + labelImgTrackMate.getErrorMessage();
			return false;
		}
		final SpotCollection tmpSpots = labelImgTrackMate.getModel().getSpots();

		/*
		 * Reposition spots with respect to the interval and time.
		 */
		final List< Spot > slist = new ArrayList<>();
		for ( final Spot spot : tmpSpots.iterable( false ) )
		{
			for ( int d = 0; d < interval.numDimensions() - 1; d++ )
			{
				final double pos = spot.getDoublePosition( d ) + interval.min( d ) * calibration[ d ];
				spot.putFeature( Spot.POSITION_FEATURES[ d ], Double.valueOf( pos ) );
			}
			// Shift in time.
			final int frame = spot.getFeature( Spot.FRAME ).intValue() + (int) minT;
			spot.putFeature( Spot.POSITION_T, frame * frameInterval );
			spot.putFeature( Spot.FRAME, Double.valueOf( frame ) );
			slist.add( spot );
		}
		spots = SpotCollection.fromCollection( slist );

		/*
		 * End.
		 */

		final long end = System.currentTimeMillis();
		this.processingTime = end - start;

		return true;
	}

	@Override
	public SpotCollection getResult()
	{
		return spots;
	}

	@Override
	public boolean checkInput()
	{
		if ( null == img )
		{
			errorMessage = baseErrorMessage + "Image is null.";
			return false;
		}
		if ( img.dimensionIndex( Axes.Z ) >= 0 )
		{
			errorMessage = baseErrorMessage + "Image must be 2D over time, got an image with multiple Z.";
			return false;
		}
		return true;
	}

	@Override
	public String getErrorMessage()
	{
		return errorMessage;
	}

	@Override
	public long getProcessingTime()
	{
		return processingTime;
	}

	/**
	 * Add a hook to delete the content of given path when Fiji quits. Taken
	 * from https://stackoverflow.com/a/20280989/201698
	 * 
	 * @param path
	 */
	// private static void recursiveDeleteOnShutdownHook( final Path path )
	// {
	// 	Runtime.getRuntime().addShutdownHook( new Thread( new Runnable()
	// 	{
	// 		@Override
	// 		public void run()
	// 		{
	// 			try
	// 			{
	// 				Files.walkFileTree( path, new SimpleFileVisitor< Path >()
	// 				{
	// 					@Override
	// 					public FileVisitResult visitFile( final Path file, final BasicFileAttributes attrs ) throws IOException
	// 					{
	// 						Files.delete( file );
	// 						return FileVisitResult.CONTINUE;
	// 					}

	// 					@Override
	// 					public FileVisitResult postVisitDirectory( final Path dir, final IOException e ) throws IOException
	// 					{
	// 						if ( e == null )
	// 						{
	// 							Files.delete( dir );
	// 							return FileVisitResult.CONTINUE;
	// 						}
	// 						throw e;
	// 					}
	// 				} );
	// 			}
	// 			catch ( final IOException e )
	// 			{
	// 				throw new RuntimeException( "Failed to delete " + path, e );
	// 			}
	// 		}
	// 	} ) );
	// }

	// private static class LoggerTailerListener extends TailerListenerAdapter
	// {
	// 	private final Logger logger;

	// 	private final static Pattern PERCENTAGE_PATTERN = Pattern.compile( ".+\\s(\\d*\\.?\\d*)\\%.+" );

	// 	public LoggerTailerListener( final Logger logger )
	// 	{
	// 		this.logger = logger;
	// 	}

	// 	@Override
	// 	public void handle( final String line )
	// 	{		return;

	// 		logger.log( line + '\n' );
	// 		// Do we have percentage?
	// 		final Matcher matcher = PERCENTAGE_PATTERN.matcher( line );
	// 		if ( matcher.matches() )
	// 		{
	// 			final String percent = matcher.group( 1 );
	// 			logger.setProgress( Double.valueOf( percent ) / 100. );
	// 		}
	// 	}
	// }

	// --- org.scijava.Cancelable methods ---

	@Override
	public boolean isCanceled()
	{
		return isCanceled;
	}

	@Override
	public void cancel( final String reason )
	{
		isCanceled = true;
		cancelReason = reason;
	}

	@Override
	public String getCancelReason()
	{
		return cancelReason;
	}

	// --- Multithreaded methods ---

	@Override
	public void setNumThreads()
	{
		// no op
	}

	@Override
	public void setNumThreads( final int numThreads )
	{
		// no op
	}

	@Override
	public int getNumThreads()
	{
		return 1;
	}

}
