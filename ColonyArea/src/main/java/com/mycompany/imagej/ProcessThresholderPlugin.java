package com.mycompany.imagej;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import com.mycompany.imagej.*;

/**
 * Ce plugin realise le seuillage d'images de colonies cultivee dans un puit.
 * 
 * Le filtre gere les images 8-bit et 16-bit
 */
public class ProcessThresholderPlugin implements PlugInFilter {
	
	ImagePlus img;

	public int setup(String arg, ImagePlus imp) {
		boolean wrong_version = IJ.versionLessThan("1.46r");
	      if (wrong_version)
	        return DONE;
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}
		
		this.img = imp;
		
		// The plugin analyzes images with 16-bit, 8-bit grayscale
		return DOES_8G | DOES_16;  
	}

	public void run(ImageProcessor ip) {
		IJ.showStatus("Start processing...");
		process();
	}

	/**
	 * Process an image.
	 */
	public void process() {
		
		/** Converts this ImagePlus to 8-bit grayscale. */
		int type = img.getType();
		ImageProcessor ip = img.getProcessor();

		switch ( type ) {
			case ImagePlus.GRAY8 : 
			break ;
			case ImagePlus.GRAY16 : 
				IJ.showStatus("Converting to 8-bit grayscale");	
				img.setProcessor(img.getTitle(), img.getProcessor().duplicate().convertToByte(true));		 	
			break ;
			default : 
				  IJ.error("8-bit or 16-bit grayscale required");
				  throw new IllegalArgumentException("Only gray level images supported.") ;
		}
		
		//IJ.showMessage("Note","Running Colony_thresholder.");
		Colony_thresholder ct = new Colony_thresholder(img); 
		ct.processingColony(); 
	}

	public void showAbout() {
		IJ.showMessage("Process colony thresholder", "ColonyAreaPlugin");
	}

 	
	/**********************************************************************
	 *            INNER CLASSES and METHODS
	 ***********************************************************************/
}


