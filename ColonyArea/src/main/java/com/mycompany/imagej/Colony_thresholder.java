package com.mycompany.imagej;

import ij.*;
import ij.process.*;
import ij.text.TextWindow;
import ij.gui.*;
import ij.WindowManager.*;
import ij.io.SaveDialog.*;
import ij.io.Opener;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.*;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Polygon;
import java.awt.Window;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.Rectangle;
import java.awt.image.AffineTransformOp;

import javax.swing.JOptionPane;

import ij.measure.*;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.EDM;
import ij.plugin.filter.Filler;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.plugin.CanvasResizer;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import ij.plugin.Zoom;

import ij.util.ArrayUtil;
import ij.util.Tools;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.GaussianCurveFitter;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

import org.apache.commons.math3.analysis.function.Gaussian;

import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;

//import sc.fiji.analyzeSkeleton.*;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.SkeletonResult;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.Edge;

import com.mycompany.utils.MovingAverageFilter;
import com.mycompany.utils.utils;

/*
 *  Colony_thresolder seuille l'image ouverte 
 */
public class Colony_thresholder { 
	
	ImagePlus 		img;	 // Colony in input
	String 			imgName; // filename for retrieve image by short name 
	int 			proposalThreshold; // Threshold
	
	/**
	 The plug in works with 256 and 65536 gray level images 
	 8-bit (0-255) Gray level range of 0-255.
	 16-bit (0-65535) Gray level range of 0-65535. 
	*/ 
	
	// gray level histograms (histogram[i] = entree pour l'intensite i)
	double[] colonyFractionArea = new double[GRAY_LEVEL_MAX]; // fraction_area     
    
    private static int 		MIN_INTENSITY_THRESHOLD = 40;
	private static int 		MIN_SIZE_CLUSTER = 150; // Élimination des objets de taille inférieure à t pixels. 
	private static double 	MIN_CIRCULARITY = 0.4;
	private static double 	SKELETON_CRITERIA = 30.0;
	private static double 	GAUSSIAN_BLUR_SIGMA = 3.0;
	private static int 		GRAY_LEVEL_MAX = 256 ; // Nb de niveaux de gris de l'image
	
	private static int 		startIntensity;
	private static int 		minSizeCluster;
	private static double 	minCircularity;
	private static double 	skeletonCriteria;
	private static double 	gaussianBluring;
	
	private static ProgressBar progressBar;

	/**
	 * 	Comparator class instance : sort ArrayList of Edge
	 *  Creates the comparator for comparing Edge length
	 */
	class EdgeComparator implements Comparator<Edge> {
	    // override the compare() method
	    public int compare(Edge e1, Edge e2) {
	        if (e1.getLength() == e2.getLength())
	            return 0;
	        else if (e1.getLength() < e2.getLength())
	            return 1;
	        else
	            return -1;
	    }
	}
	
	/**
	 *  Colony_thresholder Constructor
	 */
	Colony_thresholder(ImagePlus img) { 		
		
		System.out.println("-> Colony Thresholder Constructor.");
		
		this.img = img;
		proposalThreshold =0;
		
		ImagePlus image	= WindowManager.getCurrentImage();	// currentImage 
		// Returns the path to the directory that the active image
		String dir = IJ.getDirectory("image");  
		imgName = image.getTitle();
		
		ImageJ ij = IJ.getInstance();
		progressBar = ij.getProgressBar();
		
		// path_to_image
		System.out.println("open image " + dir + imgName);
		if ( IJ.openImage(dir + imgName) == null) 
			System.out.println("Image requiered.");
	}
	
	/**
	 *  Image processing
	 */
	public void processingColony() {
		// Run a Gaussian Blur filter on the image to blur out the “speckle”
		IJ.run(img, "Gaussian Blur...", "sigma=" + gaussianBluring);
	
		// Decalage de l'histogramme vers la droite  pour augmenter la luminance
		YesNoCancelDialog res = new YesNoCancelDialog(IJ.getInstance(),
			"Mapping with LUT HiLo",
			"Used to detect saturated (red) or clipped (Blue) pixels", "Yes", "No");
		if(res.cancelPressed()) {
	         System.out.println("Pressed CANCEL");
	         return;
	    } 
		if (res.yesPressed()) { 
	    	System.out.println("Pressed YES. Shift histogram");
	    	increaseLuminance(img);
	    } else
	    	System.out.println("Pressed NO. No mapping.");
 			
		// Calculate Threshold
		proposalThreshold = calculateThreshold(img.duplicate());
		// Display Calculated Threshold on the plot
		findLocalMinima(proposalThreshold);

		// Apply threshold calculated from 1st derivative with average moving
		IJ.showMessage("Find threshold : " + proposalThreshold);
		ImageProcessor mask = applyThreshold(proposalThreshold, img);

		IJ.selectWindow(imgName);
		ImageCalculator ic = new ImageCalculator();
		ImagePlus imp1 = img.duplicate();
		imp1.show();
		// binarization
		IJ.run(imp1, "Convert to Mask", "  black");	
		IJ.run("Invert");
		// Clean image
		IJ.makeOval(5, 5, imp1.getWidth()-10, imp1.getHeight()-10); 
		IJ.setBackgroundColor(0, 0, 0);
		IJ.run("Clear Outside");
		
		// Ouverture / Dilatation
		IJ.run("Open"); IJ.run("Dilate"); IJ.run("Dilate");
		ImagePlus img2 = new ImagePlus("LPE", WindowManager.getCurrentImage().getProcessor());
		img2.show();
		/*
		 * Pour eviter la sur-segmenation, on applique le watershed sur le mask 
		 * sans les trous. Les lignes de partage sont appliquees apres coup  
		*/
		// sauver le mask avant fill holes
		ImageProcessor ipWithHoles = imp1.getProcessor().duplicate();
		//Affiche image des Mask avant Fill Holes
		ImagePlus imgWithHoles = new ImagePlus("Mask With Holes", ipWithHoles);
		imgWithHoles.show();		
		IJ.selectWindow("LPE");
		IJ.run("Fill Holes");			
		// Wartershed
		IJ.run("Watershed");
		// On replace les trous apres la segmentation par watershed   
		ImagePlus imp3 = ic.run("AND create", img2, imgWithHoles);
		imp3.show();

		// Isolate objects (Measure : Analyze/count colonies)
		analyzeParticles(imp1);
		
		// Extract objects : Apply ROIs from the Manager on original image 
		System.out.println("Apply ROIs from the Manager on image = " + imgName);
		IJ.selectWindow(imgName);
		IJ.run("From ROI Manager");

		// Multi-cropping from ROI
		multiCroppingFromROI();

		// Skeletonize	
		ArrayList<Edge> listEdges = skeletonize();
		
		// close unnecessary windows
		closeWindows();
		
		// Loop through the roiset and display ROI if match with length constraints
		loopThroughROIset(listEdges);
	}

	/**
	 *   Calculate the optimal threshold 
	 */
	public int calculateThreshold(ImagePlus im) {	
		// Set Measurements... 		
		int measurements = Measurements.AREA + Measurements.MEAN + Measurements.AREA_FRACTION;		
		ResultsTable rt = new ResultsTable();
		Analyzer analyzer = new Analyzer(im, measurements, rt);
	
		String imName = im.getTitle();
		//IJ.selectWindow(imName);
		System.out.println("Calculate Threshold 0-"+GRAY_LEVEL_MAX); 
		
		// loop on slices
		int nSlices	= im.getNSlices();
		nSlices = 1;
		int n=1;//for (int n=1; n <= nSlices; n++) {		
		System.out.println("Slice # " + n); 
		    IJ.setSlice(n); // display la slice n
		    String fullname = "Well " + n + " of " + imName;
		    int max_intensity	= 0; 
		    int min_intensity	= MIN_INTENSITY_THRESHOLD;
		  
		    if (!showDialogStartIntensity()) return 0;
		  	    
			// Calcul setThreshold de l'image
		    ImageProcessor ip = im.getProcessor(); //.duplicate();
   
			for (int upper=0; upper < GRAY_LEVEL_MAX; upper++) {		
				if (upper < startIntensity ) {
					colonyFractionArea[upper] = 0.0;
					rt.addValue("X0", upper);
					rt.addValue("Y0", 0.0);
					rt.incrementCounter();
				}
				else {
				    ip.setThreshold(1, upper, ImageProcessor.RED_LUT); 
				    analyzer.measure(); 
				    colonyFractionArea[upper] = rt.getValueAsDouble(ResultsTable.AREA_FRACTION, upper);			    
					System.out.println("setThreshold(1, "+upper+ ") "+IJ.d2s(colonyFractionArea[upper],7));  
				}
				progressBar.show(upper, 256);
			}			
			return thresholdPlotting(); // Threshold and plot
		//} 
	}	

	/**
     *  Affiche l'histogramme derive de l'image
     */
	public int thresholdPlotting() {
	
		// Representation de la courbe des fractions de surface
		plot("Colony Fraction Area %", colonyFractionArea);
		
		// Courbe de la derivee des fractions de surface		
		double[] firstDerivative = derivate(colonyFractionArea, 1); // fraction_area
		plot("1st derivative of colonyFractionArea", firstDerivative);
		
	    // Lissage/Affichage par la methode de la moyenne mobile 
		MovingAverageFilter filter = new MovingAverageFilter(10, false, null);
		double[] resultMovingAverage = filter.Filter(firstDerivative);
		System.out.println("Running/Affichage MovingAverageFilter");
		plot("1st derivative with average moving", resultMovingAverage);
		
		// Recherche indice de la valeur maximum  
		int[] rank = Tools.rank(resultMovingAverage);
		int size = resultMovingAverage.length;
		System.out.println("Sorted list of indices of the double array (idx()=" + rank[size-1]);			
		
		//	Call plugin "Find Peaks" (peaks finder windowTitle: "Plot Values")
		IJ.run("Find Peaks", "min._peak_amplitude=0.10 min._peak_distance=0 min._value=NaN max._value=NaN list");
		
		// Recuperer la ResultsTable Window "Plot Values"			
		Frame frame = WindowManager.getFrame("Plot Values");
		if (frame==null) return 0;
		if (!(frame instanceof TextWindow)) return 0;
		ResultsTable rtPeaks = ((TextWindow)frame).getResultsTable();
		if(rtPeaks == null){
			IJ.showMessage("Find Peaks requires be run first.");
			return 0;
		}		
	    int count = rtPeaks.size();
		System.out.println("Table Peaks size : " + count);
		String columnHeadings = rtPeaks.getColumnHeadings();
		System.out.println("Headers : " + columnHeadings);
		
		// Recuperation des Min/Max dans la ResultsTable
		//Point2D.Double [] Points = getPoints(rtPeaks, "X0", "Y0");
		//dump(Points);
		Point2D.Double [] maxPoints = getPoints(rtPeaks, "X1", "Y1");
		System.out.println(maxPoints.length + " Maxima");
		utils.dump(maxPoints);
		Point2D.Double [] minPoints = getPoints(rtPeaks, "X2", "Y2");
		System.out.println(minPoints.length + "Minima");
		utils.dump(minPoints);		
		
		// Recherche de la valeur du 1er minimum local à gauche du pic maximum
		// Cad l'abscisse du 1er minima à gauche du pic maxi (vallee)
		System.out.println("Recherche le 1er minimum local à gauche du pic maximum.");
		int threshold = findingLeftValley(maxPoints, minPoints);
		System.out.println("Proposal threshol > " + threshold);
		
		return threshold; 
	}
	
	/**
	 *  Loop through the roiset and display ROI if match with length constraints
	 */ 
	public void loopThroughROIset(ArrayList<Edge> listEdges) {	
		// Test coordinate (x,y) contained in the Roi.			
	    RoiManager roiManager = RoiManager.getInstance();
	    Roi[] rois = roiManager.getRoisAsArray();	    
	    // convert to List so we can use indexOf(roi) further below
		List<Roi> rois_list  = Arrays.asList(rois);     			
		// loop through the roiset and match each roi with its corresponding channel
		ArrayList<Integer> massOfColony = new ArrayList<Integer>(); 
		//IJ.selectWindow(image.getTitle());
		IJ.selectWindow(img.getTitle());
		
		System.out.println("Apply length constraints on skeleton candidate :");
		
		String message = "Minimal length > " + skeletonCriteria + "\n";
		// iterations ROI detectees
		for( Edge e : listEdges ) {	    	      
		    double px1 = e.getV1().getPoints().get(0).x;
		    double py1 = e.getV1().getPoints().get(0).y;		    
		    if (e.getLength() > skeletonCriteria) { // AND Area > area min
				for (int i=0; i<roiManager.getCount(); i++) {		
					// boolean containsPoint​(double x, double y) 
					if (rois[i].containsPoint(px1, py1) ) {
						//roiManager.select(i);
						System.out.println("Length ROI # " + (i+1) + " : " 
								+ IJ.d2s(e.getLength(), 2) + " > " + skeletonCriteria);
					    //WaitForUserDialog wait = new WaitForUserDialog("Drawing ROIs from manager", 
					    //			"Please press OK when done.");
					    //wait.show();
						// populate list of ROI > length min
						massOfColony.add(i+1);
						double px2 = e.getV2().getPoints().get(0).x;
					    double py2 = e.getV2().getPoints().get(0).y;	
						message += "\nROI#" + String.valueOf(i+1) + "\t";
						message += " V1(" + px1 + ", " + py1 + ")";
						message += " V2(" + px2 + ", " + py2 + ")";						
					}
				}
		    }
		}
		/* 1) et 2) Detection de potentielles anomalies de segmentation */
		
		/*
		 * 1) Si le squelette est superieur a un seuil declare dans 'skeletonCriteria'
		 *    Le tableau massOfColony contient l'indice des ROIs 
		 */ 
		IJ.showMessage("ROI Length constraints", message);
		System.out.println("Selected ROI contains : " + massOfColony.toString());

		IJ.selectWindow(imgName);
		ImagePlus ci = img.duplicate();
		ci.setTitle("Composite");
		ci.show();
		System.out.println("print ci.getNChannels(): " + ci.getNChannels());
		Overlay overlayImage = ci.getOverlay();
		overlayImage.drawLabels(false);
		// combining ROIs...
		for (int roiSelect : massOfColony) {
			Roi roiSelected = roiManager.getRoi(roiSelect-1);
			roiSelected.setFillColor(Color.green); 
			overlayImage.add(roiSelected);
			//overlayImage.addElement(roiSelected);
			System.out.println("ROI Id to combine : " + roiSelect);
		}	
		/*
		 * 2) Aspect ratio : ψA = xFeret min / xFeret max (0 < ψA≤ 1)
		 *    Ratio of the minimum to the maximum Feret diameter. 
		 *    It gives an indication for the elongation of the particle. 
		 *    Some literature also used 1/ψA as the definition of sphericity.
		 */
		ResultsTable rt = ResultsTable.getResultsTable("Analyze Particles Results Table");
		int count = rt.size();
		Double FeretRatio, Area;
		for (int i=0; i<count; i++) {
			FeretRatio = rt.getValue("FeretRatio", i);
		    Area = rt.getValue("Area", i);
		    System.out.println("FeretRatio = "+ FeretRatio + " Area = " + Area);
		    // Display results
		    if (FeretRatio <= 0.75 && Area > 2000) {
		    	if( !(massOfColony.contains(i+1)) ) {
			    	Roi roiSelected = roiManager.getRoi(i);
					roiSelected.setFillColor(Color.yellow); 
					overlayImage.add(roiSelected);
					System.out.println("ROI Id FeretRatio <= 0.7 && Area > 2000 : " + (i+1));
		    	}
		    }
		}	 
	}
	
	/**
	 * Augmentation de la Luminance (ou brillance) de l’image
	 * luminance = moyenne de tous les pixels de l’image.
	 * On décale l’histogramme à droite en ajoutant une valeur à chaque pixel
	 * NdG de l’image : I (x, y ) = I (x, y ) + b
	 * Cela permet d'avoir tous les pixels > 0
	 */
	public void increaseLuminance(ImagePlus img) {
		// Minimal Value > 0  : 255/65535 - max(Histo image)
		ImageStatistics stats =	img.getStatistics();
		// returns uncalibrated (raw) statistics for the image
		// histogram, area, mean, min and max, standard deviation and mode.
		int newIntensityMin = GRAY_LEVEL_MAX - (int)stats.max;
		if ( newIntensityMin > MIN_INTENSITY_THRESHOLD ) 
			newIntensityMin = MIN_INTENSITY_THRESHOLD;
		IJ.run("Add...", "value=" + newIntensityMin);
		System.out.println("New Intensity Minimal = " + newIntensityMin);

		// Supprime le flou genere autour du perimetre du puit 
		removeSignalOutsideWell(img); // setTool("oval");
		
		// TODO : affichage de l'histogramme modifié
	}
	
	/**
	 *  Process Opening disk r=2 pixels
	 *  
	 *  remove all the detected object that are too far from the cell edges
	 *  Opening using a disk of radius 2 as structuring element
	 *  Objet: supprimer les éléments plus petits que l’élément structurant.
	 */
	public ImageProcessor OpeningMorphology(ImageProcessor mask) {		
		System.out.println("Ouverture avec element structurant disque r=2 pixels");
		ImageProcessor ipMask = Morphology.opening(mask, Strel.Shape.DISK.fromRadius( 2 ) );
		ImagePlus img2 = new ImagePlus("LPE", ipMask);
	    img2.show();
	    
	    return ipMask;
	}
	
	/**
	 *  Analyze/count colonies : tamisage (taille, circularite)
	 *  
	 *  Shape/location measurements to collect for each segmented colony 
	 */
	public void analyzeParticles(ImagePlus watershed) {
		
        RoiManager rm = RoiManager.getInstance();
	
		//int measurements = Measurements.SHAPE_DESCRIPTORS + Measurements.AREA + Measurements.FERET  + Measurements.CIRCULARITY + Measurements.PERIMETER;
		int measurements = Measurements.ALL_STATS;// define Set Measurements 
		
		//Set up particle analyzer 
		int options = ParticleAnalyzer.ADD_TO_MANAGER | ParticleAnalyzer.SHOW_OVERLAY_MASKS | ParticleAnalyzer.CLEAR_WORKSHEET | ParticleAnalyzer.INCLUDE_HOLES | ParticleAnalyzer.DISPLAY_SUMMARY;

		ResultsTable results = new ResultsTable();
		ParticleAnalyzer.setResultsTable(results);
/*		
		IJ.run("Analyze Particles...", "size=" + minSizeCluster + "-Infinity circularity=" + minCircularity + "-1.00 " 
				+ "show=Outlines display clear include summarize overlay add composite");
*/		
		Analyzer.setMeasurements(measurements);
		ParticleAnalyzer analyze = new ParticleAnalyzer(options, measurements, results, 
				(double)minSizeCluster, Double.MAX_VALUE, 
				(double)minCircularity, 1.00); // min and max circularity values (0 and 1)
		
		// analyze(p) sets the ResultsTable instance 'results'
		analyze.analyze(watershed);
		
		// Add the Feret's ratio measurement as a new column
		for (int row=0; row<results.size(); row++) {
			double ratio = results.getValue("MinFeret", row) /
					results.getValue("Feret", row);
		    results.setValue("FeretRatio", row, ratio);
		}
		results.updateResults();
		
		//results.save("/Desktop/test.csv");
		results.show("Analyze Particles Results Table");
	}

	/**
	 * Analysis of 2D and 3D skeleton images
	 */
	public ArrayList<Edge> skeletonize() {
		IJ.selectWindow("LPE");

		IJ.run("Distance Map");
		IJ.run("FeatureJ Edges", "compute smoothing=1.0 lower=[] higher=[]");
		//IJ.setOption("ScaleConversions", true);
	
		IJ.run("8-bit");
		IJ.run("Convert to Mask");

		ImagePlus imLPE = WindowManager.getCurrentImage();
		ImageProcessor ipff = imLPE.getProcessor();
		ipff.setColor(255);
		FloodFiller ff = new FloodFiller(ipff); 
		ff.fill8(1,1); 
		imLPE.updateAndDraw();
		
		IJ.run("Invert"); 
		IJ.run("Dilate"); 
 		
		IJ.run("Skeletonize (2D/3D)");
		IJ.run("Analyze Skeleton (2D/3D)", "prune=none calculate show display");
		IJ.selectWindow("Branch information");	
		
		// analyze skeleton
		AnalyzeSkeleton_ skel = new AnalyzeSkeleton_();
		skel.setup("", imLPE);
		SkeletonResult skelResult = skel.run(AnalyzeSkeleton_.NONE, false, false, null, true, false);

		// create copy of input image
		ImagePlus prunedImage = imLPE.duplicate();

		// get graphs (one per skeleton in the image)
		Graph[] graph = skelResult.getGraph();

		// list of end-points
		ArrayList<Point> endPoints = skelResult.getListOfEndPoints();
		//System.out.println(" List Of End Points length =" + endPoints.size());
		//endPoints.forEach(pt -> {
        //   System.out.println(pt.x + ", " + pt.y);
        //});
			
		// list of filaments
		ArrayList<Double> arrListLength = new ArrayList<Double>();
		ArrayList<Edge> arrListEdges = new ArrayList<Edge>();
	
		System.out.println(" Graph length =" + graph.length);
		for( int i = 0 ; i < graph.length; i++ ) {
			
			ArrayList<Edge> listEdges = graph[i].getEdges();
			//System.out.println(" List Edges length (" + i + ") = " + listEdges.size());
			
		    // go through all branches, remove branches < threshold in duplicate image
			// threshold = len in pixels
		    for( Edge e : listEdges ) {	    	
		    	ArrayList<Point> p1 = e.getV1().getPoints();
		        ArrayList<Point> p2 = e.getV2().getPoints();
		        //boolean v1End = endPoints.contains( p1.get(0) );
		        //boolean v2End = endPoints.contains( p2.get(0) );
		        // if any of the vertices is end-point : v1End || v2End = true
		    	System.out.println("length(e) = " + IJ.d2s(e.getLength(), 2) 
		    		+ " p1.size()=" + p1.size() + " V1(" + p1.get(0).x + ", " + p1.get(0).y + ")"
		    		+ " p2.size()=" + p2.size() + " V2(" + p2.get(0).x + ", " + p2.get(0).y + ")");
		    	arrListLength.add(e.getLength());
		    	arrListEdges.add(e);
		    }
		}
		//prunedImage.setTitle( image.getShortTitle() + "-pruned" );		
		//prunedImage.show();
	    
	    // Sorting ArrayList in ascending Order using Collection.sort() method
        System.out.println("Sorting filaments in Ascending order.");
        //Collections.sort(arrListLength, Collections.reverseOrder());	 
	    // Print the sorted ArrayList
        /*
        arrListLength.forEach(filament -> {
            System.out.println(filament.toString());
        });
        */
	    // call the sort function
	    Collections.sort(arrListEdges, new EdgeComparator());
	    // Display sorted list
	    /*
	    for (Edge iterator: arrListEdges) {
	    	ArrayList<Point> p1 = iterator.getV1().getPoints();
	        ArrayList<Point> p2 = iterator.getV2().getPoints();
	        System.out.println("V1("+p1.get(0).x+", "+p1.get(0).y+") "
	        		         + "V2("+p2.get(0).x+", "+p2.get(0).y+") "
	        		         + IJ.d2s(iterator.getLength(), 2) );
        }
        // Display biggest path
	    Edge biggest = arrListEdges.get(0);
	    double px = biggest.getV1().getPoints().get(0).x;
	    double py = biggest.getV1().getPoints().get(0).y;
	    System.out.println("the biggest path : V1("+px+", "+py+")");	    
	    Point2D.Double pt2D = new Point2D.Double(px, py);  
	    */
	    return arrListEdges;  
	}
	
	/**
	 *  Apply the calculated Threshold and return mask
	 *  Sets the lower and upper threshold levels and displays the image 
	 *  using red to highlight thresholded pixels. 
	 */
	public ImageProcessor applyThreshold(int proposalThreshold, ImagePlus img) {
		ImagePlus imgWell =(new Duplicator()).run(img);
		imgWell.setTitle("Image_duplicated");
		ImagePlus imgMaskWell =(new Duplicator()).run(img);
		imgMaskWell.setTitle("imgMaskWell");
		imgMaskWell.getProcessor().setThreshold(1, proposalThreshold, 
				ImageProcessor.RED_LUT);
		
		// create thresholded mask, Make binary mask 
		// A mask set all the background pixels to 0
		IJ.showStatus("Converting to mask");
		ImageProcessor mask = imgMaskWell.getProcessor().createMask();
		ImagePlus maskImp = new ImagePlus("Mask", mask);
        maskImp.show();

		ImageCalculator ic = new ImageCalculator();
		ImagePlus imp3 = ic.run("AND create", imgWell, maskImp);
		imp3.show();
		IJ.run("Fire"); // affiche fire LUT	
		
		// Affiche image des Mask 
		ImagePlus imp = new Duplicator().run(maskImp);
		imp.setTitle("Mask With Holes");
		
		return mask;
	}  
	
	/**
	 *   Open GenericDialog for select image files
	 */
	private boolean showDialogStartIntensity() {
		GenericDialog gd = new GenericDialog("Plugin Options");
		String msg = "This plugin process a 8-bit image from only one well.\n\n";
		gd.addMessage(msg);
		
		// Start intensity threshold 
		gd.addNumericField("Set start intensity threshold (40-220) ? ", 
				MIN_INTENSITY_THRESHOLD, 0);  
		// Minimal size cluster
		gd.addNumericField("Specify the min size per colony (pixel^2):", 
				MIN_SIZE_CLUSTER, 0);
		// Circularity coef.
		gd.addNumericField("Select circularity (1.0 perfect circle - 0.0 elongated polygon):", 
				MIN_CIRCULARITY, 2);
		// length  coef.
		gd.addNumericField("Select length for over-segmented colony:", 
				SKELETON_CRITERIA, 1);
		// length  coef.
		gd.addNumericField("Select sigma for gaussian bluring:", 
				GAUSSIAN_BLUR_SIGMA, 1);
		
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		
		// Gets methode
		startIntensity = (int)(gd.getNextNumber());
		minSizeCluster = (int)(gd.getNextNumber());
		minCircularity = gd.getNextNumber();
		skeletonCriteria = gd.getNextNumber();
		gaussianBluring = gd.getNextNumber();
		
		return true;
	}

	/**
	 *  Removes all signal outside the ROI circle 
	 *   - after bluring
	 *   - after dilate if objects recover outside well
	 */
	public void removeSignalOutsideWell(ImagePlus img) {
		// setTool("oval");
		IJ.makeOval(5, 5, img.getWidth()-10, img.getHeight()-10);	
		// Force a 0 hors de la surface d'interet 
		IJ.setBackgroundColor(0, 0, 0);
		IJ.run("Clear Outside");
		IJ.run("Select None");
		IJ.run("HiLo");
	}
	
	/**
	 *  Multi-cropping from ROI manager	      
	 */
	public void multiCroppingFromROI() {	
        ImagePlus imp = WindowManager.getCurrentImage();  
        int impId = imp.getID();
        System.out.println("Active window: " + imp.getTitle());
        RoiManager rm = RoiManager.getInstance();
        boolean rmActive = rm!=null;
        System.out.println("Active ROI manager: " + rmActive);
        
        // Retreive ROIs as an array
        Roi[] rois = rm.getRoisAsArray();
        if (rois.length==0) {
            IJ.error("ROI Manager is empty");
            return;
        }
        // rois: this array stores the ROIs
        System.out.println("ROI length = " + rois.length);        
        for (int i=0; i<rois.length; i++) {
            Roi roi = rois[i];
            Rectangle2D r = roi.getFloatBounds();
            System.out.println(" ROI("+(i+1)+"):" + 
        		(int)r.getX()+","+(int)r.getY()+" "+
        		(int)r.getWidth()+" "+(int)r.getHeight());                
        }
        // duplicate image active
        ImagePlus imageForMultiCropping = imp.duplicate();
        // Roi Overlay.get​(int index) : Returns the ROI with the specified index or null if the index is invalid.
        Overlay overlayImage = imageForMultiCropping.getOverlay();
        Roi overlayImageRoi = overlayImage.get(0);
        //ImageRoi overlayImageRoi = (ImageRoi) imp.getOverlay().get(1);
        if (overlayImageRoi == null) {
            IJ.error("Roi index is invalid.");
            return;
        }           
        System.out.println(" ROI type : " + rois[0].getTypeAsString());
                
        ImageProcessor ipOverlay = imageForMultiCropping.getProcessor();      
        Overlay overlay = imageForMultiCropping.getOverlay(); // Returns the current overly, null if this image does not have an overlay.
        if (overlay==null)
          overlay = new Overlay();                
        // Entoure d'un rectangle les objets de la selection en overlay                     
        for(int i=0; i< rois.length;i++) {
        	Rectangle2D.Double boundingRectRoi = rois[i].getFloatBounds();
        	// Create frame 
        	addBoxCrop(imageForMultiCropping, boundingRectRoi);
        }
        imageForMultiCropping.show();
        IJ.selectWindow(imp.getID());
   	} 
	
	/**
	 * Draw box from coordinate in the Z projection image and add the crop number.
	 */
	private void addBoxCrop(ImagePlus imp, Rectangle2D.Double bounds) {
	    ImageProcessor ip = imp.getProcessor();
	    System.out.println(" Rectangle2D.Doubl : " + bounds.toString());
		/* Line color size parameter */
		ip.setColor(Color.white);
		ip.setLineWidth(1);
		ip.setAntialiasedText(false);
		/* Font */
		Font font = new Font("Arial", Font.PLAIN, 30);
		ip.setFont(font);
		/* Draw current box*/
		ip.drawRect((int) bounds.getX(), (int) bounds.getY(), (int) 
					(int) bounds.getWidth(), (int) bounds.getHeight());
		imp.updateAndDraw();
	}

	/**
	 *  Watershed (macro compatible)
	 */
	public void watershedColony(String strImage) {
		
		//Perform watershed segmentation
		IJ.run("Watershed");
		IJ.run("Set Measurements...", "area centroid center perimeter median bounding feret's area_fraction shape redirect=None decimal=3");
		
		IJ.run("Analyze Particles...", "size=" + minSizeCluster + "-Infinity circularity=" + minCircularity + "-1.00 " 
				+ "show=Outlines display clear include summarize overlay "
				+ "add composite");
		IJ.selectWindow(strImage);
		IJ.run("From ROI Manager");
	}
	
	/**
     * Finding indice of first minima before peak maximum (valley)
     */
	public int findingLeftValley(Point2D.Double [] maxPoints, Point2D.Double [] minPoints) {
		// Recherche des coord du point Y maximum
		System.out.println("Recherche des coord du point Y maximum."); 
		double yMax = 0;
		double xMax = 0;
		int idxMax = 0; 
		boolean yMaxFound = false;

		for (int i = 0; i < maxPoints.length; i++) {
			if (maxPoints[i].getY() > yMax) {
				yMax = maxPoints[i].getY();	
				xMax = maxPoints[i].getX();
				idxMax = i;
				yMaxFound = true;
			}
	    }
		if (!yMaxFound) {
			System.out.println("Maximum x not found.");
			return -1;
		}
		System.out.println("(xMax, yMax) : " + xMax + ", " + yMax);
		System.out.println("Maximum x found at index " + idxMax);
		
		// Recherche minimum local a gauche
		// Cree un array trie a partir des abcsisee du tableau des minimas pour une recherche binaire
		double [] arrMinima = new double[minPoints.length];
		for (int i = 0; i < minPoints.length; i++) 
			arrMinima[i] = minPoints[i].getX(); 
		//call sort method in ascending order 
		Arrays.sort(arrMinima); 
		//print altered array 
		System.out.println("Sorted array : " + Arrays.toString(arrMinima)); 
		
		// Recherche 1er minima local a gauche (X2 a gauche de X1) 
		int xMin = (int)xMax; //152
		boolean foundLeftValley = false;
		while (xMin > 0 && !foundLeftValley) {
			xMin--;
			// call binarySearch with key = xMin
			if (Arrays.binarySearch(arrMinima, xMin) >= 0)
				// xMin appartient au tableau MinPoints])
				foundLeftValley = true;		
		}
		System.out.println("Valley found = " + xMin); // + " value " + arrMinima[idxMin]);
	    
		// return the threshold
		return xMin;
	}
	
	/**
	 *  Recupere les coord de points presents dans la table
	 *  Les colonnes sont indiquees par leur header
	 */
	public Point2D.Double [] getPoints(ResultsTable rt, String header_x, String header_y) {		
		int indexOfX = rt.getColumnIndex(header_x);
		int indexOfY = rt.getColumnIndex(header_y);
		if (indexOfX == ResultsTable.COLUMN_NOT_FOUND) 
			throw new IllegalArgumentException("Column not found");
		if (indexOfY == ResultsTable.COLUMN_NOT_FOUND) 
			throw new IllegalArgumentException("Column not found");	
		int count = rt.size();
		ArrayList<Point2D.Double> t = new ArrayList<Point2D.Double>();
		Double x, y;
		for (int i=0; i<count; i++) {
		    x = rt.getValueAsDouble(indexOfX, i);		
		    y = rt.getValueAsDouble(indexOfY, i);	
		    if (x.isNaN() && y.isNaN()) {
		    	i=count;
		    }
		    else {
		    	t.add(new Point2D.Double(x, y));
		    }
		}
		// Retourner un tableau contenant tous les éléments de cette liste
		Object[] arrayOfObjects = t.toArray();
		
		Point2D.Double[] arrayOfPoints = new Point2D.Double[t.size()];
		for(int i=0; i < t.size(); i++) {
			//Convertir les objets en Point2D.Double
			arrayOfPoints[i] = (Point2D.Double) arrayOfObjects[i];
		}
		return arrayOfPoints; 
	}	

	/**
	 *  Close windows
	 */
	public void closeWindows() {
		IJ.selectWindow("Colony Fraction Area %");
	    IJ.run("Close");
	    IJ.selectWindow("1st derivative of colonyFractionArea");
	    IJ.run("Close");
	    IJ.selectWindow("1st derivative with average moving");
	    IJ.run("Close");
	    IJ.selectWindow("Peaks in 1st derivative with average moving");
	    IJ.run("Close");
	    IJ.selectWindow("Result of Image_duplicated");
	    IJ.run("Close");
	    IJ.selectWindow("LPE-labeled-skeletons");
	    IJ.run("Close");
	    IJ.selectWindow("Tagged skeleton");
	    IJ.run("Close");
	    IJ.selectWindow("Summary");
	    IJ.run("Close");
	    IJ.selectWindow("Results");
	    IJ.run("Close");
	    IJ.selectWindow("LPE edges");
	    IJ.run("Close");
	    IJ.selectWindow("Plot Values");
	    IJ.run("Close");
	}
			
	/**
	 *  Calcul de la derivee par la methode numerique de Taylor
	 */
	public double [] derivate(double [] a, int ordre) {
		System.out.println("Approximation numérique de la dérivée.");
        double [] b = new double[a.length];	
        b[0]=0.0;
        switch(ordre) {   
	      	case 1:
	           System.out.println("Formule de Taylor-Young centrée d'ordre 2.");
		   		for (int n=1; n<b.length-1; n++) {
		   			b[n]=(a[n+1]-a[n-1])/2;
		   		}
		   		break;
	      	case 2:
	      		System.out.println("Dérivé seconde d'ordre 1 (formule de Taylor).");
			   	for (int n=1; n<b.length-1; n++) {
			   		b[n] = a[n+1] -2*a[n] + a[n-1];
			   	}
			   	break;
	      	default:
	           System.out.println("Choix incorrect");
	           b=null;
	           break;
        }
		return b;
	}

	/**
	 *  Affichage de la derivee numerique
	 */
	public double [] numericalDerivative(double [] arrayOfDiscreteValues) {
		int h = 1; // pas discret
		double [] arNumDerivative = new double[256]; // fraction_area ;
		DecimalFormat df = new DecimalFormat("0.############E0");
		for (int x = 0; x < 255; x++) {
			arNumDerivative[x] = (arrayOfDiscreteValues[x+h]-arrayOfDiscreteValues[x]) / h;
			//System.out.println( df.format(x) + " " + df.format(firstDerivative[x]));
		}
		return arNumDerivative;
	}

	/**
     * Representation de l'histogramme f
     */
    public void plot(String plotTitle, double[] f) {
    	ArrayList<Double> xvalues = new ArrayList<Double>();
    	ArrayList<Double> yvalues = new ArrayList<Double>();
    	
    	// Fixe les limites du graphe  
        java.util.List<Double> list = Arrays.stream(f).boxed().collect(Collectors.toList());
    	double yMax = Collections.max(list); // find the maximum value from ArrayList  
    	double yMin = Collections.min(list); // find the minimum value from ArrayList  
    	System.out.println("Mode (the number which appears most often) : " + yMax);
             
        // histogram[i] : gray level histograms (entree pour l'intensité i)
        for (int i = 0; i < 256; i++) {
			xvalues.add((double) i); //xvalues[i] = i;
	        yvalues.add((double) f[i]); // yvalues[i] = histogram[i];          
        }  
        
        // Plot histogram
 		String xLabel = "Pixel Intensity";
 	    String yLabel = "Pixel Frequency";
 	    
        Plot plotter = new Plot(plotTitle, xLabel, yLabel);
        
        plotter.setLimits(0.0, 255, yMin, yMax);
     	plotter.setLineWidth(1);
        plotter.addPoints(xvalues, yvalues, Plot.LINE);//  bins, list(hist))
        
	    double xloc = 0.2;
		double yloc = 0.2;

		plotter.setColor(Color.black);
		plotter.addLabel(xloc, yloc, "Distribution");
		plotter.update();

		// Show final graphic
		System.out.println("plotting '"+plotTitle+ "'"); 
		PlotWindow pw = plotter.show();//PlotWindow pw = plotter.show();
		//pw.drawPlot(plotter);
    }
    
    /**
     * Recuperer le plot find peak et marquer le threshold
     */
   	 public void findLocalMinima(int threshold) {	 
   		ImagePlus imp = WindowManager.getCurrentImage();
   		if (imp==null) { 
   			IJ.error("There are no plots open."); 
   			return; 
   		}
		ImageWindow win = imp.getWindow();		
		if (win==null || !(win instanceof PlotWindow)) {
			IJ.error(imp.getTitle() +" is not a plot window.");
			return;
		}	
		PlotWindow pw = (PlotWindow)win;
		double[] pwXvalues = Tools.toDouble(pw.getXValues());
		double[] pwYvalues = Tools.toDouble(pw.getYValues());
				
		String plotTitle = imp.getTitle();
		Plot plot = new Plot("Threshold in "+ plotTitle, "Pixel intensity", "Distribution");
		plot.addPoints(pwXvalues, pwYvalues, Plot.LINE);
		// Maximas 
		plot.setLineWidth(2);
		plot.setColor(Color.RED);
		plot.addLabel(0.00, 0, "Proposal Threshold (left valley) :" + threshold);
		// Minimas
		plot.setColor(Color.BLUE);
		plot.addLabel(0.50, 0, "Y for proposal threshold :" + IJ.d2s(pwYvalues[threshold], 3));

		double [] x = { Double.valueOf(threshold) } ;
  	    double [] y = { pwYvalues[threshold] } ; 	
		plot.setColor(Color.RED);
		plot.addPoints(x, y, Plot.CROSS);
		
		if (plotTitle.startsWith("Threshold in"))
			pw.drawPlot(plot);
		else
			pw = plot.show();
    }
}   
