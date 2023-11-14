package fiji.plugin.trackmate.lacss;

import static fiji.plugin.trackmate.detection.DetectorKeys.DEFAULT_TARGET_CHANNEL;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_TARGET_CHANNEL;
import static fiji.plugin.trackmate.detection.ThresholdDetectorFactory.KEY_SIMPLIFY_CONTOURS;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.scijava.Cancelable;

import com.google.protobuf.ByteString;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.detection.LabelImageDetectorFactory;
import fiji.plugin.trackmate.detection.MaskUtils;
import fiji.plugin.trackmate.detection.SpotDetector;
import fiji.plugin.trackmate.detection.SpotGlobalDetector;
import fiji.plugin.trackmate.util.TMUtils;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealTypeConverters;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.display.imagej.ImgPlusViews;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class LacssDetector< T extends RealType< T > & NativeType< T > > implements SpotDetector< T >, Cancelable
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

	protected List<Spot> spots;

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

	@Override
	public boolean process()
	{
		final long start = System.currentTimeMillis();
		final double[] calibration = TMUtils.getSpatialCalibration( img );

		isCanceled = false;
		cancelReason = null;

		DataOutputStream p_out = new DataOutputStream(pyServer.getOutputStream());
		DataInputStream p_in = new DataInputStream(pyServer.getInputStream());

		final RandomAccessibleInterval< T > rai = Views.interval( img, interval );
		final Img<ShortType> label;

		try {
			label = processFrame( rai, p_in, p_out);

			final AtomicInteger max = new AtomicInteger( 0 );
			Views.iterable( label ).forEach( p -> {
				final int val = p.getInteger();
				if ( val != 0 && val > max.get() )
					max.set( val );
			} );
			final List< Integer > indices = new ArrayList<>( max.get() );
			for ( int i = 0; i < max.get(); i++ )
				indices.add( Integer.valueOf( i + 1 ) );

			final ImgLabeling< Integer, ShortType > labeling = ImgLabeling.fromImageAndLabels( label, indices );
			spots = MaskUtils.fromLabelingWithROI(labeling, interval, calibration, false, null );
		}
		catch (IOException e) {
				errorMessage = e.getLocalizedMessage();
				return false;
		}

		/*
		 * End.
		 */

		final long end = System.currentTimeMillis();
		this.processingTime = end - start;

		return true;
	}

	@Override
	public List<Spot> getResult()
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
}
