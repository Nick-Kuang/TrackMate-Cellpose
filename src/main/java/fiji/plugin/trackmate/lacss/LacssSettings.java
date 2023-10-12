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

	public final double diameter;

	public final boolean useGPU;

	public final boolean simplifyContours;


	public LacssSettings(
			final String lacssPythonPath,
			final PretrainedModel model,
			final String customModelPath,
			final int chan,
			final int chan2,
			final double diameter,
			final boolean useGPU,
			final boolean simplifyContours )
	{
		this.lacssPythonPath = lacssPythonPath;
		this.model = model;
		this.customModelPath = customModelPath;
		this.chan = chan;
		this.chan2 = chan2;
		this.diameter = diameter;
		this.useGPU = useGPU;
		this.simplifyContours = simplifyContours;
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
		if ( useGPU )
			cmd.add( "--use_gpu" );

		// Diameter.
		cmd.add( "--diameter" );
		cmd.add( ( diameter > 0 ) ? "" + diameter : "0" );

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

		private String lacssPythonPath = "/opt/anaconda3/envs/cellpose/bin/python";

		private int chan = 0;

		private int chan2 = -1;

		private PretrainedModel model = PretrainedModel.CYTO;

		private double diameter = 30.;
		
		private boolean useGPU = true;
		
		private boolean simplifyContours = true;

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

		public Builder diameter( final double diameter )
		{
			this.diameter = diameter;
			return this;
		}

		public Builder useGPU( final boolean useGPU )
		{
			this.useGPU = useGPU;
			return this;
		}

		public Builder simplifyContours( final boolean simplifyContours )
		{
			this.simplifyContours = simplifyContours;
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
					diameter,
					useGPU,
					simplifyContours );
		}

	}

	public enum PretrainedModel
	{
		CYTO( "Cytoplasm", "cyto" ),
		NUCLEI( "Nucleus", "nuclei" ),
		CYTO2( "Cytoplasm 2.0", "cyto2" ),
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
