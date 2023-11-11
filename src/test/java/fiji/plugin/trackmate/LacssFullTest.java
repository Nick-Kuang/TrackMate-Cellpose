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

import java.io.IOException;

import fiji.plugin.trackmate.lacss.LacssDetector;
import fiji.plugin.trackmate.lacss.LacssDetectorFactory;
import fiji.plugin.trackmate.lacss.LacssSettings;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettingsIO;
import fiji.plugin.trackmate.util.TMUtils;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;

public class LacssFullTest
{

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public static void main( final String[] args ) throws IOException, InterruptedException
	{
		ImageJ.main( args );


		final ImagePlus imp = IJ.openImage( "../s1.avi" );
		imp.show();
		
		final LacssSettings cp = LacssSettings.DEFAULT;
		final ImgPlus img = TMUtils.rawWraps( imp );
		img.setAxis(new DefaultLinearAxis(Axes.TIME), 2);

		final LacssDetector detector = new LacssDetector (
			img, img, cp, Logger.DEFAULT_LOGGER, LacssDetectorFactory.getPyServer()
		);	
			
		if ( !detector.checkInput() )
		{
			System.err.println( detector.getErrorMessage() );
			return;
		}
		
		if ( !detector.process() )
		{
			System.err.println( detector.getErrorMessage() );
			return;
		}
		
		System.out.println( String.format( "Done in %.1f s.", detector.getProcessingTime() / 1000. ) );
		final SpotCollection spots = detector.getResult();
		spots.setVisible( true );
		System.out.println( spots );

		final Model model = new Model();
		model.setSpots( spots, false );
		final SelectionModel selectionModel = new SelectionModel( model );
		
		final HyperStackDisplayer displayer = new HyperStackDisplayer( model, selectionModel, imp, DisplaySettingsIO.readUserDefault() );
		displayer.render();
	}
}
