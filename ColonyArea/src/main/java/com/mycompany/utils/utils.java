package com.mycompany.utils;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.Roi;

public class utils {
	
	/*
	 *  Traversing over array using for each  
	 */
	public static void dump(ArrayList<Point2D.Double> pointsList) {
		//System.out.println(" Points size  = " + pointsList.size());		
		//for(int i=0; i< pointsList.size(); i++) {
		//	System.out.println("Points2D.Double (x,y) : " + 
		//pointsList.stream().forEach((pt)->System.out.println("{"+pt.getX()+ ", "+pt.getY()+"}")); 		
		pointsList.stream().forEach((pt)->System.out.println(pt.toString()));
    }
	public static void dump(Point2D.Double [] pointsList) {
		//System.out.println(" Points size  = " + pointsList.length);		
		 // iterating through an array using the for-each loop
		for (Point2D.Double pt : pointsList) {
			//System.out.println("{"+pt.getX()+ ", "+pt.getY()+"}");
			System.out.println(pt.toString());
		}
    }
	
	/* 
	 * inused...
	*/
	public void deleteIfExistsMethod(String filename) {   
		// Convert File to Path
		File file = new File(filename);
		Path path = file.toPath();
       // Enclose the code in try-catch blocks
	    try { // Delete the file
	    	boolean isDeleted = Files.deleteIfExists(path);
	    	// check the status
	    	if (isDeleted) {
	    		System.out.println("File is successfully deleted!");
	    	} else {
	    		System.out.println("Sorry, the file was not deleted.");
	    	}
	    }  
	    catch (IOException e) {
	      System.out.println("I/O error occurred");
	    } 
	    catch (SecurityException e) {
	      System.out.println("Delete access denied!");
	    }
	}
}
