package fiji.plugin.trackmate.lacss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fiji.plugin.trackmate.lacss.LacssSettings;

public class LacssSettings 
{

	public final String lacssPythonPath;
	
	public final int chan;

	public final int chan2;

	public final PretrainedModel model;

	public final String customModelPath;

	public final double min_cell_area;

	public final boolean remove_out_of_bound;

	public final double scaling;
	
	public final boolean return_label;




	public LacssSettings(
			final String lacssPythonPath,
			final PretrainedModel model,
			final String customModelPath,
			final int chan,
			final int chan2,
			final double min_cell_area,
			final double scaling,
			final boolean return_label,
			final boolean remove_out_of_bound )
	{
		this.lacssPythonPath = lacssPythonPath;
		this.model = model;
		this.customModelPath = customModelPath;
		this.chan = chan;
		this.chan2 = chan2;
		this.min_cell_area = min_cell_area;
		this.scaling = scaling;
		this.return_label = return_label;
		this.remove_out_of_bound = remove_out_of_bound;
	}

	public List< String > toCmdLine( final String imagesDir )
	{
		final List< String > cmd = new ArrayList<>();

		/*
		 * First decide whether we are calling Cellpose from python, or directly
		 * the Cellpose executable. We check the last part of the path to check
		 * whether this is python or cellpose.
		 */
		final String[] split = lacssPythonPath.replace( "\\", "/" ).split( "/" );
		final String lastItem = split[ split.length - 1 ];
		if ( lastItem.toLowerCase().startsWith( "python" ) )
		{
			// Calling Cellpose from python.
			cmd.add( lacssPythonPath );
			cmd.add( "-m" );
			cmd.add( "lacss" );
		}
		else
		{
			// Calling Cellpose executable.
			cmd.add( lacssPythonPath );
		}

		/*
		 * Cellpose command line arguments.
		 */

		// Target dir.
		cmd.add( "--dir" );
		cmd.add( imagesDir );

		// First channel.
		cmd.add( "--chan" );
		cmd.add( "" + chan );

		// Second channel.
		if ( chan2 >= 0 )
		{
			cmd.add( "--chan2" );
			cmd.add( "" + chan2 );
		}

		// GPU.
		if ( return_label )
			cmd.add( "--use_gpu" );

		// Diameter.
		cmd.add( "--diameter" );
		cmd.add( ( min_cell_area > 0 ) ? "" + min_cell_area : "0" );

		// Model.
		cmd.add( "--pretrained_model" );
		if ( model == PretrainedModel.CUSTOM )
			cmd.add( customModelPath );
		else
			cmd.add( model.path );

		// Export results as PNG.
		cmd.add( "--save_png" );

		// Do not save Numpy files.
		cmd.add( "--no_npy" );

		return Collections.unmodifiableList( cmd );
	}

	public static Builder create()
	{
		return new Builder();
	}

	public static final class Builder
	{

		private String lacssPythonPath = "/Fiji/plugins/TrackMate/lacss/lacss.py";

		private int chan = 0;

		private int chan2 = -1;

		private PretrainedModel model = PretrainedModel.LiveCell;

		private double min_cell_area = 0.;

		private double scaling = 1.;
		
		private boolean return_label = true;
		
		private boolean remove_out_of_bound = false;

		private String customModelPath = "";

		public Builder channel1( final int ch )
		{
			this.chan = ch;
			return this;
		}

		public Builder channel2( final int ch )
		{
			this.chan2 = ch;
			return this;
		}

		public Builder lacssPythonPath( final String lacssPythonPath )
		{
			this.lacssPythonPath = lacssPythonPath;
			return this;
		}

		public Builder model( final PretrainedModel model )
		{
			this.model = model;
			return this;
		}

		public Builder min_cell_area( final double min_cell_area )
		{
			this.min_cell_area = min_cell_area;
			return this;
		}

		public Builder scaling( final double scaling )
		{
			this.scaling = scaling;
			return this;
		}

		public Builder return_label( final boolean return_label)
		{
			this.return_label = return_label;
			return this;
		}

		public Builder remove_out_of_bound( final boolean remove_out_of_bound )
		{
			this.remove_out_of_bound = remove_out_of_bound;
			return this;
		}

		public Builder customModel( final String customModelPath )
		{
			this.customModelPath = customModelPath;
			return this;
		}

		public LacssSettings get()
		{
			return new LacssSettings(
					lacssPythonPath,
					model,
					customModelPath,
					chan,
					chan2,
					min_cell_area,
					scaling,
					return_label,
					remove_out_of_bound );
		}

	}

	public enum PretrainedModel
	{
		LiveCell("LIVECell", "livecell"),
		TissueNet("TissueNet", "tissuenet"),
		CUSTOM( "Custom", "" );

		private final String name;

		private final String path;

		PretrainedModel( final String name, final String path )
		{
			this.name = name;
			this.path = path;
		}

		@Override
		public String toString()
		{
			return name;
		}

		public String lacssName()
		{
			return path;
		}
	}

	public static final LacssSettings DEFAULT = new Builder().get();
    
}
