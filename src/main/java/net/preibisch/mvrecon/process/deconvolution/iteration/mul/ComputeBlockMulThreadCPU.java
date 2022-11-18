/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2022 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.process.deconvolution.iteration.mul;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.process.deconvolution.DeconView;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.ImagePortion;
import util.FFTConvolution;

public class ComputeBlockMulThreadCPU extends ComputeBlockMulThreadAbstract
{
	final ExecutorService service;
	final ArrayList< Callable< Void > > tasks;
	final ArrayList< ImagePortion > portions;
	final ImgFactory< ComplexFloatType > fftFactory;
	final ArrayList< Img< FloatType > > tmp1, tmp2;
	final float lambda;


	public ComputeBlockMulThreadCPU(
			final ExecutorService service,
			final int numViews,
			final float minValue,
			final float lambda,
			final int id,
			final int[] blockSize,
			final ImgFactory< FloatType > blockFactory )
	{
		super( blockFactory, numViews, minValue, blockSize, id );

		this.tmp1 = new ArrayList<>();
		this.tmp2 = new ArrayList<>();
		for ( int i = 0; i < numViews; ++i )
		{
			this.tmp1.add( blockFactory.create( blockSize, new FloatType() ) );
			this.tmp2.add( blockFactory.create( blockSize, new FloatType() ) );
		}

		this.service = service;
		this.tasks = new ArrayList<>();
		this.portions = new ArrayList<>();
		this.lambda = lambda;

		this.portions.addAll( FusionTools.divideIntoPortions( tmp1.get( 0 ).size() ) );
		System.out.println( "num Portions: " + this.portions.size() );
		try { this.fftFactory = blockFactory.imgFactory( new ComplexFloatType() ); } catch ( IncompatibleTypeException e )
		{
			e.printStackTrace();
			throw new RuntimeException( "Cannot transform ImgFactory to ComplexFloatType." );
		}
	}

	@Override
	public IterationStatistics runIteration(
			final List< DeconView > view,
			final List< RandomAccessibleInterval< FloatType > > imgBlock, // out of bounds is ZERO
			final List< RandomAccessibleInterval< FloatType > > weightBlock,
			final List< Float > maxIntensityViews,
			final List< ArrayImg< FloatType, ? > > kernel1,
			final List< ArrayImg< FloatType, ? > > kernel2 )
	{
		//
		// convolve psi (current guess of the image) with the PSF of the current view
		// [psi >> tmp1]
		//
		for ( int i = 0; i < numViews; ++i )
			convolve1( getPsiBlockTmp(), kernel1.get( i ), view.get( i ).getPSF().getKernel1FFT(), tmp1.get( i ) );

		//
		// compute quotient img/psiBlurred
		// [tmp1, img >> tmp1]
		//
		// outofbounds in the original image are already set to quotient==1 since there is no input image
		//
		tasks.clear();
		for ( final ImagePortion portion : portions )
			for ( int j = 0; j < numViews; ++j )
			{
				final int i = j;

				tasks.add( new Callable< Void >()
				{
					@Override
					public Void call() throws Exception
					{
						DeconvolutionMethods.computeQuotient( portion.getStartPosition(), portion.getLoopSize(), tmp1.get( i ), imgBlock.get( i ) );
						return null;
					}
				});
			}

		FusionTools.execTasks( tasks, service, "compute quotient" );

		//
		// blur the residuals image with the kernel
		// (this cannot be don in-place as it might be computed in blocks sequentially,
		// and the input for the n+1'th block cannot be formed by the written back output
		// of the n'th block)
		// [tmp1 >> tmp2]
		//
		for ( int i = 0; i < numViews; ++i )
			convolve2( tmp1.get( i ), kernel2.get( i ), view.get( i ).getPSF().getKernel2FFT(), tmp2.get( i ) );

		//
		// compute final values
		// [psi, weights, tmp2 >> psi]
		//
		final double[][] sumMax = new double[ portions.size() ][ 2 ];
		tasks.clear();

		double maxIntensityView = 0;
		for ( int i = 0; i < maxIntensityViews.size(); ++i )
			maxIntensityView += maxIntensityViews.get( i );
		maxIntensityView /= (double)maxIntensityViews.size();

		final float miv = (float)maxIntensityView;

		for ( int i = 0; i < portions.size(); ++i )
		{
			final ImagePortion portion = portions.get( i );
			final int portionId = i;

			tasks.add( new Callable< Void >()
			{
				@Override
				public Void call() throws Exception
				{
					DeconvolutionMethods.computeFinalValuesMul(
							portion.getStartPosition(),
							portion.getLoopSize(),
							getPsiBlockTmp(),
							tmp2,
							weightBlock,
							lambda,
							getMinValue(),
							miv,
							sumMax[ portionId ] );
					return null;
				}
			});
		}

		FusionTools.execTasks( tasks, service, "compute final values " + view );

		// accumulate the results from the individual threads
		final IterationStatistics is = new IterationStatistics();

		for ( int i = 0; i < portions.size(); ++i )
		{
			is.sumChange += sumMax[ i ][ 0 ];
			is.maxChange = Math.max( is.maxChange, sumMax[ i ][ 1 ] );
		}

		return is;
	}

	public void convolve1(
			final RandomAccessibleInterval< FloatType > image,
			final Img< FloatType > kernel,
			final Img< ComplexFloatType > kernelFFT,
			final Img< FloatType > result )
	{
		final FFTConvolution< FloatType > fftConvolution =
				new FFTConvolution< FloatType >(
						Views.extendMirrorSingle( image ),
						image,
						Views.extendZero( kernel ),
						kernel,
						result,
						fftFactory );
		fftConvolution.setExecutorService( service );
		fftConvolution.setKeepImgFFT( false );
		fftConvolution.setKernelFFT( kernelFFT );
		fftConvolution.convolve();
	}

	public void convolve2(
			final RandomAccessibleInterval< FloatType > image,
			final Img< FloatType > kernel,
			final Img< ComplexFloatType > kernelFFT,
			final Img< FloatType > result )
	{
		final FFTConvolution< FloatType > fftConvolution =
				new FFTConvolution< FloatType >(
						Views.extendValue( image, new FloatType( 1.0f ) ), // ratio outside of the deconvolved space (psi) is 1, shouldn't matter here though
						image,
						Views.extendZero( kernel ),
						kernel,
						result,
						fftFactory );
		fftConvolution.setExecutorService( service );
		fftConvolution.setKeepImgFFT( false );
		fftConvolution.setKernelFFT( kernelFFT );
		fftConvolution.convolve();
	}

}
