package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.Max_Project;
import spim.fiji.spimdata.explorer.ExplorerWindow;

public class MaxProjectPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public MaxProjectPopup()
	{
		super( "Max-Projection" );

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

			final List< ViewId > viewIds = ApplyTransformationPopup.getSelectedViews( panel );

			if ( SpimData.class.isInstance( panel.getSpimData() ) )
				Max_Project.maxProject( (SpimData)panel.getSpimData(), viewIds, new FloatType() );
			else
			{
				JOptionPane.showMessageDialog(
						null,
						"Applying the calibration is not supported for '" + panel.getSpimData().getClass().getSimpleName() + "', needs to extend SpimData." );
			}
		}
	}
}
