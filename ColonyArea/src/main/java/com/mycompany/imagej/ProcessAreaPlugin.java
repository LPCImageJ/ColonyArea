package com.mycompany.imagej;

/*
 *  ProcessAreaAreaPlugin.java
 */
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import ij.plugin.ChannelSplitter;
import ij.plugin.Colors;
import ij.plugin.ImageCalculator.*;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.WindowManager.*;
import ij.io.SaveDialog.*;
import ij.measure.Calibration;
import ij.io.DirectoryChooser;
import ij.io.OpenDialog;
import ij.io.SaveDialog;

// call from Process_Pixels  
public class ProcessAreaPlugin implements PlugInFilter {
	
	// Declare dimensions constants
	
	// cercle radius = 17.5mm
	private static final double DIAMETER_WELL = 35.0;
	// Internal well 
	private static final int SIZE_PLATE = 6;
	// Distance between cell centers: 39mm
	private static final double DIST_BETWEEN_CENTERS = 39.0;
	// Culture Area: 962mm2
	private static final double WELL_AREA = 962; 
	
	// Matrix Microplate Data Sheet
	
    /** The flags specifying the capabilities and needs */
    private int flags = DOES_ALL|SUPPORTS_MASKING;
		
	protected ImagePlus image;
	String 				fileName;
	String 				directory;
	static RoiManager	roiManager;
	//static Calibration  calibrationImage;
	
	// Rectangle dimensions of the roi  
    int roiX, roiY, roiWidth, roiHeight;
    // calibrer l’image pour obtenir les valeurs réelles de l’objet en mm 
    double coefCal; 
    
    public int setup(String arg, ImagePlus imp) {
        if (arg.equals("about")) {
        	showAbout(); 
        	return DONE;
        }
    	boolean wrong_version = IJ.versionLessThan("1.46r");
    	if (wrong_version)
    		return DONE;

    	image = imp;
    	
		return DOES_8G | DOES_RGB | NO_CHANGES;
	}

    /** This method crop 6 wells in a plate 
     * @param ip The image
     */
	public void run(ImageProcessor ip) {
		int type = image.getType();
		if ( !(type==ImagePlus.GRAY8 || type==ImagePlus.COLOR_RGB) )
		    throw new IllegalArgumentException("Unsupported type");
		
		if (image.getStackSize()>1) 
			throw new IllegalArgumentException("Stack not supported");
		
		if (parseCurrentROI() == false) {
			IJ.showMessage("Selection plate well bounds", 
					"This command requires a selection");
			return;
		}
		
		/* Verify position of ROI */
		Roi rVerify = roiManager.getRoi(0);
		System.out.println("Position   Roi: " + rVerify.getPosition());
		System.out.println("X base     Roi: " + rVerify.getXBase()); 
		roiManager.deselect(rVerify);
		
		process(ip);
	}
	
	public void showAbout() {
		IJ.showMessage("Process Colony Area...", 
	        "This plugin filter process 8-bit or RGB images.\n" +
	        "You need to select bounds of a 6 plate wells before calling the plugin.\n" +
	        "Colony Area process a rectangular ROI, to mask \n" +
	        "all the wells in a stack."
            );
	}

	/**
	 * Process an image : MICROPLAQUE DE CULTURE STANDARD 6 PUITS
	 */
	public void process(ImageProcessor ip) {
		
		//ImagePlus currentImage = WindowManager.getCurrentImage();
		fileName = image.getTitle();
		IJ.showMessage("Note","Please select the directory and the postfix to all images.");
		DirectoryChooser dc = new DirectoryChooser("Select the output directory");
		directory = dc.getDirectory();
		if(directory == null) return;
		System.out.println("Postfix directory for saving files: " + directory); // saving files	
		
		// Display canvas (rectangle & circles) using overlay
		Overlay overlay = createOverlay();	
		coefCal = setScalePlateWells();
		drawCanvas();		
		Point2D.Double [] arrayUpperLeftPoints = drawCircles();
	    
		//IJ.run("Select None");
		roiManager.close();
		image.deleteRoi();
		
        int res = JOptionPane.showConfirmDialog(null, "Cropping Ok?\n" + "Continue?");
        if(res != 0) {            
        	overlay.clear(); //IJ.run("Hide Overlay");
            new ij.gui.WaitForUserDialog("Please, reload an image.").show();
        	return;
        }         
		cropAllWells(arrayUpperLeftPoints);
		
		IJ.showMessage("Note", "The image is cropped and the template is constructed.");	
	} 

	/*
	 * Create an Overlay.
	 */
	private Overlay createOverlay() {	
		Overlay overlay = new Overlay();
		image.setOverlay(overlay);
        if (image.getOverlay() == null) {
            System.out.println("Overlay is null");
		}
        return overlay;
	}
	
	/*
     * Display canvas (rectangle & circles) of the well plate on image
     * using the overlay
     */
	private void drawCanvas() {
		Roi rectRoi = new Roi(roiX, roiY, roiWidth, roiHeight);
        rectRoi.setStrokeColor(Color.BLUE);
        Overlay overlay = image.getOverlay();
		overlay.add(rectRoi);
	}
	
	/*
	 * Draw wells and return an array of upper left well points 
	 */
    private Point2D.Double [] drawCircles() {
    		
		// Retrieve coordinates (x,y) upper left points of wells 
        double [] PointsX = {
        	roiX, roiX + DIST_BETWEEN_CENTERS*coefCal, roiX + roiWidth-DIAMETER_WELL*coefCal,
        	roiX, roiX + DIST_BETWEEN_CENTERS*coefCal, roiX + roiWidth-DIAMETER_WELL*coefCal };
        double [] PointsY = {
        	roiY, roiY, roiY, 
	        roiY + roiHeight-DIAMETER_WELL*coefCal, roiY + roiHeight-DIAMETER_WELL*coefCal, roiY + roiHeight-DIAMETER_WELL*coefCal };  
        // Draw circles
        Point2D.Double [] arrayUpperLeftPoints = new Point2D.Double[6];
        int diameter = (int)(DIAMETER_WELL*coefCal);
        for (int i = 0; i < SIZE_PLATE; i++) {
        	arrayUpperLeftPoints[i] = new Point2D.Double (PointsX[i], PointsY[i]);
        	// makeOval and Add Selection...            
            Overlay overlay = image.getOverlay();
            OvalRoi roi = new OvalRoi((int)PointsX[i], (int)PointsY[i], diameter, diameter);
            roi.setStrokeColor(Color.BLUE);
            roi.setStrokeWidth(0.0); // width = 1 pixel
            overlay.add(roi);     
        }       
        return arrayUpperLeftPoints;
	}   
   
	/*
	 * Creates a new stack by cropping this one with circles 
	 * 
	 * @param Upper left coord (X,Y) stored in arrayUpperLeftPoints[]
	 */
	private void cropAllWells(Point2D.Double [] Points) {
        
        //  Loop over wells
    	ImageStack stackfinal = new ImageStack();  // final stack
    	int diameter = (int)(DIAMETER_WELL*coefCal);
        for (int i = 0; i < SIZE_PLATE; i++) {
	        Rectangle r = new Rectangle((int)Points[i].x, (int)Points[i].y, diameter, diameter);
	    	image.setRoi(r);
	    	ImagePlus crop = image.crop();
	    	
	    	ImageProcessor ipCrop = crop.getProcessor();
	        Roi roi = new OvalRoi(0, 0, DIAMETER_WELL*coefCal, DIAMETER_WELL*coefCal);
	        ipCrop.setRoi(roi);
	        ipCrop = ipCrop.crop();     
	        ipCrop.fillOutside(roi); // Clear the outside   			    
			// Add slice current well to Stack
	           
	        stackfinal.addSlice("well "+(i+1)+" of "+SIZE_PLATE, ipCrop.duplicate()); // add current well
	        //crop.changes = false;
	        //crop.close();
        }
        Overlay overlay = image.getOverlay();
        overlay.clear();   //IJ.run("Hide Overlay")
        image.deleteRoi(); //IJ.run("Select None");
		ImagePlus impwell = new ImagePlus("wells_stack_"+image.getTitle(), stackfinal);
		impwell.show(); 
		
		setupCalibration(impwell, DIAMETER_WELL/impwell.getWidth());
		//IJ.run("Set Scale...", "distance="+impwell.getWidth()+" known="+ DIAMETER_WELL +" pixel=1 unit=mm");
		// comment appliquer une barre d'echelle a toute la stack ??
		IJ.run("Scale Bar...", "Width=4");
	   	IJ.saveAs(impwell, "Tiff", directory+"wells_stack_"+image.getTitle());
	}  
	
    private double setScalePlateWells() {

		// Calculate coef for calibration
		double mmPerPixelX = roiWidth / (DIAMETER_WELL + 2*DIST_BETWEEN_CENTERS); 
		double mmPerPixelY = roiHeight / (DIAMETER_WELL + DIST_BETWEEN_CENTERS);
		// Take the mean of the 2 values
		double calibrationCoef = (mmPerPixelX+mmPerPixelY)/2;
		System.out.println("mm per pixels : " + calibrationCoef);

		// scale image 
		double knownDist = DIAMETER_WELL + 2 * DIST_BETWEEN_CENTERS;
		setupCalibration(image, knownDist/roiWidth);
        //IJ.run("Set Scale...", "distance=" + roiWidth + " known=" + knownDist + " pixel=1 unit=mm");
       
        return calibrationCoef;
	}
    
    // Sets up a spatial Calibration for calibrating images
    public void setupCalibration(ImagePlus im, double ratio) {
    	Calibration calibrationImage = im.getCalibration();
		if(calibrationImage == null) 
			calibrationImage = new Calibration(im);
		calibrationImage.setUnit("mm");
		calibrationImage.pixelWidth = calibrationImage.pixelHeight = calibrationImage.pixelDepth = ratio;
		im.setCalibration(calibrationImage);
		//IJ.run(image, "Properties...", "");
		
		System.out.println("Pixel width/height: "+calibrationImage.pixelWidth+"/"+calibrationImage.pixelHeight);
		System.out.println("Pixel depth: "+calibrationImage.pixelDepth);
		System.out.println("XXZ unit: "+calibrationImage.getXUnit()+","+calibrationImage.getYUnit()+","+calibrationImage.getZUnit());
    }
    
	/**
     * If there is a user selected ROI, set the class variables {roiX}
     * and {roiY}, {roiWidth}, {roiHeight} to the corresponding
     * features of the ROI, and return true. Otherwise, return false.
     */
    boolean parseCurrentROI() {
    	// initialize RoiManager
    	roiManager = RoiManager.getInstance();
    	if (roiManager == null) { 
    	    //IJ.showMessage("Error", "Could not get ROI Manager instance.");
    		roiManager = new RoiManager();
    	}
    	    			
        Roi roi = image.getRoi();
        if (roi == null) return false;
		// scan the bounding rectangle of the ROI (java.awt.Rectangle)
        Rectangle r = roi.getBounds();
        roiX = r.x;
        roiY = r.y;
        roiWidth = r.width;
        roiHeight = r.height;
        
        roiManager.addRoi(roi);
        return true;
    }
	  
	/*
	 * Extract Green chanel with best contrast
	 */
	public void extractGreenChannel() {
		//ImageConverter iConv = new ImageConverter(impcrop);
		//iConv.convertToGray8();
		//impcrop.setTitle(fileName);
		//impcrop.show();
		
		// declare and initialize array of channels
		//String RGB[] = {"red", "green", "blue"}; 
	
		ChannelSplitter splitter = new ChannelSplitter();
		ImagePlus[] channels = splitter.split(image);
		int nChannels = channels.length;
		image.setImage(channels[1]); // Replaces this image with the specified ImagePlus
		channels[0].close(); // red		
		channels[1].close(); // green	
		channels[2].close(); // blue
	
		IJ.selectWindow("green"); // Name crop image "green")
		IJ.saveAs("tif", directory + fileName + " (green)");
	}	   
}

// initialize RoiManager
/*
roiManager = RoiManager.getInstance();
if (roiManager == null) {
    //IJ.showMessage("Error", "Could not get ROI Manager instance.");
	roiManager = new RoiManager();
}
*/
/*       
// Lines H
IJ.makeLine(roiX, (int)(roiY+17.5*coef), roiX+roiWidth, (int)(roiY+17.5*coef));
IJ.run("Add Selection...", "stroke=white width=1");
IJ.makeLine(roiX, (int)(roiY+(17.5+39)*coef), roiX+roiWidth, (int)(roiY+((17.5+39)*coef))); 
IJ.run("Add Selection...", "stroke=white width=1");
// Lines V
IJ.makeLine( (int)(roiX+17.5*coef), (int)(roiY), (int)(roiX+17.5*coef), (int)(roiY+((2*17.5+39)*coef))); 
IJ.run("Add Selection...", "stroke=white width=1");
IJ.makeLine( (int)(roiX+((17.5+39)*coef)), (int)(roiY), (int)(roiX+((17.5+39)*coef)), (int)(roiY+((2*17.5+39)*coef))); 
IJ.run("Add Selection...", "stroke=white width=1");
IJ.makeLine( (int)(roiX+((17.5+2*39)*coef)), (int)(roiY), (int)(roiX+((17.5+2*39)*coef)), (int)(roiY+((2*17.5+39)*coef))); 
IJ.run("Add Selection...", "stroke=white width=1");
*/

/*		
// Displays a dialog box that allows the user can select a directory
JFileChooser fc = new JFileChooser();
fc.setCurrentDirectory(new java.io.File("."));
fc.setDialogTitle("Choose output directory");
fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
fc.setAcceptAllFileFilterUsed(false);
if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) { 
	System.out.println("getCurrentDirectory(): " + fc.getCurrentDirectory());
	System.out.println("getSelectedFile() : " + fc.getSelectedFile());
    // System.out.println("Opening: " + file.getAbsolutePath());
}
*/	
