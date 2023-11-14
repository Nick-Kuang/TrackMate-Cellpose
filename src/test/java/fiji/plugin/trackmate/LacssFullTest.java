/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2021 - 2023 TrackMate developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package fiji.plugin.trackmate;

import java.util.ArrayList;
import java.util.Map;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import fiji.plugin.trackmate.detection.LabelImageDetectorFactory;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettingsIO;
import fiji.plugin.trackmate.lacss.LacssDetectorFactory;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.img.display.imagej.ImageJFunctions;

/**
 * Inspired by the BIOP approach.
 */
public class LacssFullTest
{

	public static void main( final String[] args ) throws ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException
	{						  
		UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );

		ImageJ.main( args );

		final ImagePlus imp = IJ.openImage( "../s1.tif" );
		imp.show();

		// TrackMatePlugIn plugin = new TrackMatePlugIn();
		// plugin.run(null);

		final Settings settings = new Settings( imp );
		final LacssDetectorFactory< ? > detectorFactory = new LacssDetectorFactory<>();
		final Map< String, Object > detectorSettings = detectorFactory.getDefaultSettings();
		settings.detectorFactory = detectorFactory;
		settings.detectorSettings = detectorSettings;

		final TrackMate trackMate = new TrackMate( settings );
		// labelImgTrackMate.setNumThreads( 1 );
		if ( !trackMate.execDetection() )
		{
			System.err.println(trackMate.getErrorMessage());
			return;
		}

		final SpotCollection spots = trackMate.getModel().getSpots();

		spots.setVisible( true );
		System.out.println( spots );

		final Model model = new Model();
		model.setSpots( spots, false );
		final SelectionModel selectionModel = new SelectionModel( model );
		
		final HyperStackDisplayer displayer = new HyperStackDisplayer( model, selectionModel, imp, DisplaySettingsIO.readUserDefault() );
		displayer.render();

	}
}
