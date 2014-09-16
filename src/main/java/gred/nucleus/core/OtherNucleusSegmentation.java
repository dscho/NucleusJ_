package gred.nucleus.core;

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
		ImagePlus imagePlusSegmentedRescale = resizeImage(generateSegmentedImage (imagePlusInput, computeThreshold(imagePlusInput)));
		//2 DistanceMap
		RadialDistance radialDistance = new RadialDistance();
		ImagePlus ImagePlusDistanceMap = radialDistance.computeDistanceMap(imagePlusSegmentedRescale);
		// DistanceMap thresholding???? => seuillage en soit facil, mais comment faire qqch de cohérent?
		
		//Def de la boule => faire une methode... Comment fait on pour faire une matrice definissant une boule? comment en tenir compte?
		
		// Aprés faut ballader la boule sur le deep kernel => Si voxel = 0 le passer a 255
		// rescle l'image en mode 0.103*0.103*0.2 => faisable
		return ImagePlusDistanceMap;
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
}
