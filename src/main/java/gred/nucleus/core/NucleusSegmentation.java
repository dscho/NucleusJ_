package gred.nucleus.core;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map.Entry;
import gred.nucleus.utils.FillingHoles;
import gred.nucleus.utils.Histogram;
import ij.*;
import ij.plugin.Filters3D;
import ij.process.*;
import ij.measure.*;
import ij.process.AutoThresholder.Method;
import inra.ijpb.binary.ConnectedComponents;

/**
 * this class allows the realization of segmention method in the image in input. This segmentation
 * is based on the method of Otsu, and we add the maximization of the sphericity (shape parameter)
 * of detected object .
 * 
 * @author Poulet Axel
 *
 */
public class NucleusSegmentation
{
	
	private int _bestThreshold = 0;
	/** Segmentation parameters*/
	private double _volumeMin, _volumeMax;
	/** */
	private String _logErrorSeg = "";
  
	/**
	 * Constructor
	 * @param imagePlusInput Image to be segmente
	 */

	public NucleusSegmentation (){	}

	/**
	 * Method which run the process in input image. This image will be segmented, and
	 * the binary image will be saved in a directory. 
	 *  
	 * @param imagePlusInput
	 * @return
	 */
	public ImagePlus run (ImagePlus imagePlusInput)
	{
		IJ.log("Begin segmentation "+imagePlusInput.getTitle());
		ImagePlus imagePlusSegmented = applySegmentation (imagePlusInput);
		IJ.log("End segmentation "+imagePlusInput.getTitle());
		if (_bestThreshold == 0)
		{
			if (_logErrorSeg.length()==0)
			{
				IJ.showMessage("Error Segmentation", "Bad parameter for the segmentation, any object is detected between "
    				  +_volumeMin+" and "+ _volumeMax+" "+ imagePlusInput.getCalibration().getUnit()+"^3");
			}
			else
			{
				File fileLogError = new File (_logErrorSeg);
				BufferedWriter bufferedWriterLogError;
				FileWriter fileWriterLogError;
				try
				{
					fileWriterLogError = new FileWriter(fileLogError, true);
					bufferedWriterLogError = new BufferedWriter(fileWriterLogError);
					bufferedWriterLogError.write(imagePlusInput.getTitle()+"\n");
					bufferedWriterLogError.flush();
					bufferedWriterLogError.close();
				}
				catch (IOException e) { e.printStackTrace(); } 
			}
		}
		return imagePlusSegmented;
	}
	
	/**
	 * Compute of the first threshold of input image with the method of Otsu
	 * From this initial value we will seek the better segmentaion possible:
	 * for this we will take the voxels value superior at the threshold value of method of Otsu :
	 * Then we compute the standard deviation of this values voxel > threshold value
	 * determines which allows range of value we will search the better threshodl value :
	 *   thresholdOtsu-ecartType et thresholdOtsu+ecartType.
	 * For each threshold test; we realize a opening and a closing, then we use 
	 * the holesFilling. To finish we compute the sphericity.
	 * The aim of this method is to maximize the sphericity to obtain the segmented object
	 * nearest of the biological object.
	 * 
	 * @param imagePlusInput
	 * @return
	 */
	public ImagePlus applySegmentation (ImagePlus imagePlusInput)
	{
		Calibration calibration = imagePlusInput.getCalibration();
		final double xCalibration = calibration.pixelWidth;
		final double yCalibration = calibration.pixelHeight;
		final double zCalibration = calibration.pixelDepth;
		final double imageVolume = xCalibration*imagePlusInput.getWidth()*yCalibration*imagePlusInput.getHeight()*zCalibration*imagePlusInput.getStackSize();
		IJ.log(xCalibration+" "+yCalibration+" "+zCalibration+"  volume image :"+imageVolume);
		ImagePlus imagePlusSegmented = new ImagePlus();
		double sphericityMax = -1.0, sphericity, volume;
		ArrayList<Integer> arrayListThreshold = computeMinMaxThreshold(imagePlusInput);		
		IJ.log("Lower limit: "+arrayListThreshold.get(0)+" Upper limit "+arrayListThreshold.get(1));
		for (int t = arrayListThreshold.get(0) ; t <= arrayListThreshold.get(1); ++t)
		{
			ImagePlus imagePlusSegmentedTemp = generateSegmentedImage(imagePlusInput,t);
			morphologicalCorrection (imagePlusSegmentedTemp);
			imagePlusSegmentedTemp = ConnectedComponents.computeLabels(imagePlusSegmentedTemp, 26, 8);
			deleteArtefact(imagePlusSegmentedTemp);
			imagePlusSegmentedTemp.setCalibration(calibration);
			Measure3D measure3D = new Measure3D();
			volume = measure3D.computeVolumeObject(imagePlusSegmentedTemp,255);
			sphericity = measure3D.computeSphericity(volume,measure3D.computeSurfaceObject(imagePlusSegmentedTemp, 255));
			if (sphericity > sphericityMax && volume >= _volumeMin && volume <= _volumeMax && testRelativeObjectVolume(volume,imageVolume))
			{
				_bestThreshold=t;
				sphericityMax = sphericity;
				imagePlusSegmented= imagePlusSegmentedTemp.duplicate();			
			}
		}
		IJ.log ("end of the segmentation "+imagePlusInput.getTitle()+" "+_bestThreshold);
		imagePlusSegmented.setCalibration(calibration);
		return imagePlusSegmented;
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
	 * @param imagePlusInput
	 * @param threshold
	 * @return
	 */
	private double computeMean (ImagePlus imagePlusInput,int threshold)
	{
		double sum = 0, ni_xi = 0;
		Histogram histogram = new Histogram();
		histogram.run(imagePlusInput);
		for(Entry<Double, Integer> entry : histogram.getHistogram().entrySet())
		{
		   double label = entry.getKey();
		   int nbVoxel = entry.getValue();
		   ni_xi = ni_xi+ label*nbVoxel;
		   sum += nbVoxel;
		}
		return ni_xi/sum;
	}


	/**
	 * 
	 * @param imagePlusInput
	 * @param threshold
	 * @return
	 */
	private double computeStandardDeviation (ImagePlus imagePlusInput, int threshold)
	{
		double sumSquareDeviation = 0, sum = 0;
		Histogram histogram = new Histogram ();
		histogram.run(imagePlusInput);
		double mean = computeMean(imagePlusInput,threshold);
		for(Entry<Double, Integer> entry : histogram.getHistogram().entrySet())
		{
		   double label = entry.getKey();
		   int nbVoxel = entry.getValue();
		   sum += nbVoxel;
		   sumSquareDeviation += nbVoxel * ((label - mean) * (label - mean));
		}
		return Math.sqrt(sumSquareDeviation / (sum - 1));
	} 

	
	/**
	 * 
	 * @param imagePlusInput
	 * @param threshold
	 * @return
	 */
	private ImagePlus generateSegmentedImage (ImagePlus imagePlusInput, int threshold)
	{
		int i, j, k;
		ImageStack imageStackInput = imagePlusInput.getStack();
		ImagePlus imagePlusSegmented = imagePlusInput.duplicate();
		ImageStack imageStackSegmented = imagePlusSegmented.getStack();
		for(k = 0; k < imagePlusInput.getStackSize(); ++k)
			for (i = 0; i < imagePlusInput.getWidth(); ++i )
				for (j = 0; j < imagePlusInput.getHeight(); ++j )
				{
					double voxelValue = imageStackInput.getVoxel(i,j,k);
					if (voxelValue >= threshold) imageStackSegmented.setVoxel(i,j,k,255);
					else imageStackSegmented.setVoxel(i,j,k,0);
				}
		return imagePlusSegmented;
	}

	/**
	 * 
	 * @param imagePlusInput
	 * @return
	 */
	private ArrayList<Integer> computeMinMaxThreshold(ImagePlus imagePlusInput)
	{
		ArrayList<Integer> arrayListMinMaxThreshold = new ArrayList<Integer>();
		int threshold = computeThreshold (imagePlusInput);
		double ecartType = computeStandardDeviation(imagePlusInput,threshold);
		double min = threshold - ecartType*2;
		double max = threshold + ecartType;
		if ( min < 0) arrayListMinMaxThreshold.add(1);
		else arrayListMinMaxThreshold.add((int)min);
		arrayListMinMaxThreshold.add((int)max);
		return arrayListMinMaxThreshold;
	
	}

	
	/**
	 * 	 
	 * @param imagePlusSegmented image to be correct
	 */
	private void morphologicalCorrection (ImagePlus imagePlusSegmented)
	{
		FillingHoles holesFilling = new FillingHoles();
		computeOpening(imagePlusSegmented);
		computeClosing(imagePlusSegmented);
		imagePlusSegmented = holesFilling.apply2D(imagePlusSegmented);
	}


	/**
	 * 
	 * @param imagePlusInput
	 */
	private void computeClosing (ImagePlus imagePlusInput)
	{
		ImageStack imageStackInput = imagePlusInput.getImageStack();
		imageStackInput = Filters3D.filter(imageStackInput, Filters3D.MAX,1,1,(float)0.5);
		imageStackInput = Filters3D.filter(imageStackInput, Filters3D.MIN,1,1,(float)0.5);
		imagePlusInput.setStack(imageStackInput);
	}

	/**
	 * 
	 * @param imagePlusInput
	 */
	private void computeOpening (ImagePlus imagePlusInput)
	{
		ImageStack imageStackInput = imagePlusInput.getImageStack();
		imageStackInput = Filters3D.filter(imageStackInput, Filters3D.MIN,1,1,(float)0.5);
		imageStackInput = Filters3D.filter(imageStackInput, Filters3D.MAX,1,1,(float)0.5);
		imagePlusInput.setStack(imageStackInput);
	}
  

	/**
	 * 
	 * @return
	 */
	public int getBestThreshold (){ return _bestThreshold;}
	
	/**
	 * 
	 * @param objectVolume
	 * @return
	 */
	private boolean testRelativeObjectVolume(double objectVolume,double imageVolume)
	{
		final double ratio = (objectVolume/imageVolume)*100;
		if (ratio >= 70) return false;
		else return true;
	}
	
	/**
	 * 
	 * @param volumeMin
	 * @param volumeMax
	 */
	public void setVolumeRange(double volumeMin, double volumeMax)
	{
		_volumeMin = volumeMin;
		_volumeMax = volumeMax;
	}
	 
	/**
	 * 
	 * @param logErrorSeg
	 */
	public void setLogErrorSegmentationFile (String logErrorSeg) {_logErrorSeg = logErrorSeg;}
	
	/**
	 * Preserve the larger object and remove the other
	 *
	 * @param imagePluslab Image labeled
	 */

	public void deleteArtefact (ImagePlus imagePlusInput)
	{
		int i,j,k;
	    double voxelValue;
	    double mode = getLabelOfLargestObject(imagePlusInput);
	    ImageStack imageStackInput = imagePlusInput.getStack();
	    for(k = 0; k < imagePlusInput.getNSlices(); ++k)
	    	for (i = 0; i < imagePlusInput.getWidth(); ++i)
	    		for (j = 0; j < imagePlusInput.getHeight(); ++j)
	    		{
	    			voxelValue = imageStackInput.getVoxel(i,j,k);
	    			if (voxelValue == mode) imageStackInput.setVoxel(i,j,k,255);
	    			else imageStackInput.setVoxel(i,j,k,0);
	    		}
	}

	/**
	 * Browse each object of image and return the label of the larger object
	 * @param imagePluslab Image labeled
	 * @return Label of the larger object
	 */

	public double getLabelOfLargestObject(ImagePlus imagePlusInput)
	{
		Histogram histogram = new Histogram();
		histogram.run(imagePlusInput);
	    double indiceNbVoxelMax = 0, nbVoxelMax = -1;
	    for(Entry<Double, Integer> entry : histogram.getHistogram().entrySet())
	    {
	    	double label = entry.getKey();
	        int nbVoxel = entry.getValue();
	        if (nbVoxel > nbVoxelMax)
	        {
	        	nbVoxelMax = nbVoxel;
	        	indiceNbVoxelMax = label;
	        }
	    }
	    return indiceNbVoxelMax;
	}
}