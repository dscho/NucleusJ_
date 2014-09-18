package gred.nucleus.core;

import gred.nucleus.utils.Histogram;
import ij.*;
import ij.plugin.Resizer;
import ij.process.*;
import ij.measure.*;
import ij.process.AutoThresholder.Method;


public class OtherNucleusSegmentation
{
	public OtherNucleusSegmentation (){	}
	
	/**
	 * 
	 * 
	 * @param imagePlusInput
	 * @return
	 */
	public ImagePlus run (ImagePlus imagePlusInput)
	{
		/**1 compute Otsu threshold => create binary image with this threshold
		 * 2 rescale image and do distance Map => obtain image with istrope voxel
		 * 3 thresholded the distance Map image to creat the "deep kernel". The threshold value is inferior or equal at the "rayon de courbure "????
		 *     => comment j estime un rayon de courbure?
		 * 4 en chaque voxel du deep kernel (peut etre prendre que les voxel exterieur gain de temps?), parcourir tout les voxels appartenant
		 *  a la boule (v,s)
		 *    s => threshold de la distance map 
		 *    v => ??
		 *    si le voxel sur l'image binaire et a 0 le passer a un sinon rien faire :
		 *    		travailler sur une image binaire rescale
		 *    	=> repasse en voxel anistrope l'image final et retourner cette image
		 * 5 eliminer les objer surnumreraire
		 */
		Calibration calibration = imagePlusInput.getCalibration();
		final double xCalibration = calibration.pixelWidth;
		final double yCalibration = calibration.pixelHeight;
		final double zCalibration = calibration.pixelDepth;
	
		
		// 1 Otsu => image binaire => rescale
		ImagePlus imagePlusSegmented = generateSegmentedImage (imagePlusInput, computeThreshold(imagePlusInput));
		ImagePlus imagePlusOutput = imagePlusSegmented.duplicate();
		ImagePlus imagePlusSegmentedRescale = resizeImage(imagePlusSegmented);
		//2 DistanceMap
		RadialDistance radialDistance = new RadialDistance();
		ImagePlus imagePlusDistanceMap = radialDistance.computeDistanceMap(imagePlusSegmentedRescale);
		imagePlusDistanceMap.setTitle("dM");
		imagePlusDistanceMap.show();
		// DistanceMap thresholding???? => seuillage en soit facil, mais comment faire qqch de cohérent?
		Histogram histogram = new Histogram();
		histogram.run(imagePlusSegmentedRescale);
		double s_threshold = 1;
		ImageStack imageStackSegmentedRescale = imagePlusSegmentedRescale.getStack();
		ImageStack imageStackOutput = imagePlusOutput.getStack();
		for (int k = 0; k < imagePlusSegmentedRescale.getNSlices(); ++k)
			for (int i = 0; i < imagePlusSegmentedRescale.getWidth(); ++i)
				for (int j = 0; j < imagePlusSegmentedRescale.getHeight(); ++j)
				{
					double voxelValue =  imageStackSegmentedRescale.getVoxel(i, j, k); 
					if (voxelValue >= s_threshold)
					{
						
						int inf_k = (int)(float)(k*(xCalibration/zCalibration)-s_threshold);
						if (inf_k < 0) inf_k=0; 
						int sup_k = (int)(float)(k*(xCalibration/zCalibration)+s_threshold);
						if (sup_k > imagePlusSegmented.getNSlices()) sup_k = imagePlusSegmented.getNSlices();
						int inf_i = (int)(float)(i-s_threshold);
						if (inf_i < 0) inf_i=0; 
						int sup_i = (int)(float)(i+s_threshold);
						if (sup_i > imagePlusSegmented.getWidth()) sup_i = imagePlusSegmented.getWidth();
						int inf_j = (int)(float)(j-s_threshold);
						if (inf_j < 0) inf_j=0; 
						int sup_j = (int)(float)(j+s_threshold);
						if (sup_j > imagePlusSegmented.getHeight()) sup_j = imagePlusSegmented.getHeight();
						IJ.log("plop"+inf_k+" "+ inf_j+" "+ inf_i );
						for (int kk = inf_k; kk < sup_k ; ++kk)
							for (int ii =  inf_i; ii < sup_i; ++ii)
								for (int jj =  inf_j; jj < sup_j; ++jj)
								{
									
									if (imageStackOutput.getVoxel(ii,jj,kk) == 0)
										{	
											
											imageStackOutput.setVoxel(ii,jj,kk,255);
										}
								}
					}
				}
					
		
		//Def de la boule => faire une methode... Comment fait on pour faire une matrice definissant une boule? comment en tenir compte?
		
		// Aprés faut ballader la boule sur le deep kernel => Si voxel = 0 le passer a 255
		// rescle l'image en mode 0.103*0.103*0.2 => faisable
		imagePlusSegmented.setTitle("otsu");
		imagePlusSegmented.show();
	    imagePlusOutput.setTitle("plop");
		return imagePlusOutput;
	}
	
	/**
	 * 
	 * @param imagePlusInput
	 * @return
	 */
	private int computeThreshold (ImagePlus imagePlusInput)
	{
		AutoThresholder autoThresholder = new AutoThresholder();
		ImageStatistics imageStatistics = new StackStatistics(imagePlusInput);
		int [] tHisto = imageStatistics.histogram;
		return autoThresholder.getThreshold(Method.Otsu,tHisto);
	}
	/**
	 * 
	 * @param imagePlus
	 * @return
	 */
	private ImagePlus resizeImage (ImagePlus imagePlus)
	{
		Resizer resizer = new Resizer();
		Calibration calibration = imagePlus.getCalibration();
		double xCalibration = calibration.pixelWidth;
		double zCalibration = calibration.pixelDepth;
		double rescaleZFactor = zCalibration/xCalibration;
		ImagePlus imagePlusRescale = resizer.zScale(imagePlus,(int)(imagePlus.getNSlices()*rescaleZFactor), 0);
		return imagePlusRescale;
	}
	/**
	 * 
	 * @param imagePlusInput
	 * @param threshold
	 * @return
	 */
	private ImagePlus generateSegmentedImage (ImagePlus imagePlusInput, int threshold)
	{
		ImageStack imageStackInput = imagePlusInput.getStack();
		ImagePlus imagePlusSegmented = imagePlusInput.duplicate();
		ImageStack imageStackSegmented = imagePlusSegmented.getStack();
		for(int k = 0; k < imagePlusInput.getStackSize(); ++k)
			for (int i = 0; i < imagePlusInput.getWidth(); ++i )
				for (int j = 0; j < imagePlusInput.getHeight(); ++j )
				{
					double voxelValue = imageStackInput.getVoxel(i,j,k);
					if (voxelValue >= threshold) imageStackSegmented.setVoxel(i,j,k,255);
					else imageStackSegmented.setVoxel(i,j,k,0);
				}
		return imagePlusSegmented;
	}
	
	/**
	 * 
	 * 
	 */
	
}
