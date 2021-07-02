package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.io.File;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.legacy.LegacyImgLoaderWrapper;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class LightSheet7ImgLoader extends LegacyImgLoaderWrapper< UnsignedShortType, LegacyLightSheet7ImgLoader >
{
    public LightSheet7ImgLoader(
        final File cziFile,
        final ImgFactory< ? extends NativeType< ? > > imgFactory,
        final AbstractSequenceDescription<?, ?, ?> sequenceDescription )
    {
        super( new LegacyLightSheet7ImgLoader( cziFile, imgFactory, sequenceDescription ) );
    }

    public File getCZIFile() { return legacyImgLoader.getCZIFile(); }
	public ImgFactory< ? extends NativeType< ? > > getImgFactory() { return legacyImgLoader.getImgFactory(); }

	@Override
	public String toString() {
		return legacyImgLoader.toString();
	}
}
