package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

import javax.swing.JMenuItem;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.plugin.apply.ApplyParameters;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import bdv.BigDataViewer;

public class ReorientSamplePopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public ReorientSamplePopup()
	{
		super( "Interactively Reorient Sample ..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			if ( !SpimData.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final List< ViewId > viewIds = ApplyTransformationPopup.getSelectedViews( panel );
					final SpimData data = (SpimData)panel.getSpimData();
		
					final Apply_Transformation t = new Apply_Transformation();
		
					final ApplyParameters params = new ApplyParameters();
					
					params.sameModelAngles = true;
					params.sameModelChannels = true;
					params.sameModelIlluminations = true;
					params.sameModelTimePoints = true;

					params.model = 2; // rigid
					params.applyTo = 2; // apply on top
					params.defineAs = 2; // not necessary, but means using BDV
					
					final Map< ViewDescription, Pair< double[], String > > modelLinks = t.queryBigDataViewer( data, viewIds, params );

					if ( modelLinks == null )
						return;

					AffineTransform3D applied = new AffineTransform3D();
					applied.set( modelLinks.values().iterator().next().getA() );
					applied = applied.inverse();

					t.applyModels( data, params.minResolution, params.applyTo, modelLinks );

					// update registration panel if available
					panel.updateContent();
					
					// reset current orientation of the BDV so it doesn't jump
					final BigDataViewer bdv = panel.bdvPopup().getBDV();
					
					if ( bdv != null && bdv.getViewerFrame().isVisible() )
					{
						AffineTransform3D transform = new AffineTransform3D();
						bdv.getViewer().getState().getViewerTransform( transform );

						transform = transform.concatenate( applied );

						bdv.getViewer().setCurrentViewerTransform( transform );
						panel.bdvPopup().updateBDV();
					}
				}
			} ).start();
		}
	}
}
