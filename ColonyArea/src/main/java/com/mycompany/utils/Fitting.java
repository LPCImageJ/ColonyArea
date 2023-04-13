package com.mycompany.utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.analysis.function.Gaussian;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.GaussianCurveFitter;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

import ij.gui.Plot;

/*
 * Fits a Gaussian function to the observed points. 
 * Performs Gaussian fit on XYSeries
 
 */
 
public class Fitting {
	/* 
	 *  curvePoints the data (normalement List<DataPoint>)
	 *  gMin/gmax lower/upper bound of Gaussian fit
	 *  return a array of double[] coefFitResult
	 */
	public double[] fitGaussian(double[] curvePoints, double gMin, double gMax) {
	    WeightedObservedPoints fitData = new WeightedObservedPoints();
	    // initialiser la liste 
	    List<Double> dataSet = new ArrayList<Double>(); 
	    for (int i = 0; i < curvePoints.length; i++) 
	    	if (i >= gMin && i <= gMax)
	    		dataSet.add(curvePoints[i]);
	    double xValue = 0.0;
	    // map the input to a format Apache GaussianCurveFitter expects
	    for (double yValue : dataSet) {
	        fitData.add(xValue, yValue);
	        xValue++;
	    }   
		GaussianCurveFitter curveFitter = GaussianCurveFitter.create();
		//curveFitter.withMaxIterations(1);
		
	    //do the curve fit
		try {
	        double[] coefFitResult = curveFitter.fit(fitData.toList()); // fit params 
	        for (double v : coefFitResult) {
	            System.out.println("fitGaussian(secondDerivative) {normFactor, mean, sigma} > " + v);	        
	        }        
	        Gaussian gaussian = new Gaussian(coefFitResult[0], coefFitResult[1], coefFitResult[2]);
	        for(double xvalue : dataSet) {
	            double yvalue= gaussian.value(xvalue);
	            //System.out.println(xvalue + ", " + yvalue);
	        }
	        //System.out.print(coef[0], coef[1], coef[2];
	        return coefFitResult;
	    } catch (final Exception e) {
	        System.out.print("Exception : Cannot fit Gaussian from gMin to gMax.\n" + e);
	        e.printStackTrace();
	        return null;
	    }
	}
	
	/*
	 * Fiiting polynomial (inused)
	 */
	public void polynomialFitterTest(double [] curvePoints) {
		for (int deg=2; deg <= 52; deg+=10)
			polynomialFitter(curvePoints, deg); 
	} 
	private void polynomialFitter(double [] curvePoints, int degree) { 
        PolynomialCurveFitter curveFitter = PolynomialCurveFitter.create(degree);
        WeightedObservedPoints points  = new WeightedObservedPoints();
        
        for (int x = 0; x < 256; x++) { 
        	double f = (double)x;
        	points.add(f, curvePoints[x]);
        }
        double[] fit = curveFitter.fit(points.toList());
        System.out.printf("\nCoefficient %f, %f, %f", fit[0], fit[1], fit[2]);
        
        PolynomialFunction fitted = new PolynomialFunction(fit);
        
        double[] Y = new double[256];
    	DecimalFormat df = new DecimalFormat("0.############E0");
        for (int x = 0; x < 256; x++) { 
        	Y[x] = fitted.value((double)x);
        	//System.out.printf("\nFitting : "+x+"  "+curvePoints[x]+"   "+ Y[x]);
        }          
    }
	
	/*			
	//Derivation de la courbe Moving Average
	// plot("2nd derivative of colonyFractionArea", 2ndDerivative);
	double[] firstDerivativeAverageMoving = new double[NB_LEVEL_GRAY];
	Float x, y;
	for (int i = 0; i < count; i++) { // NB_LEVEL_GRAY
		x = (float)rtPeaks.getValue("X0", i);		
	    y = (float)rtPeaks.getValue("Y0", i);
	    firstDerivativeAverageMoving[i] = (double)y;
	    //System.out.println(i + " yvalues=" + yvalues.get(i));              
	}			

	// Smooth with a moving average window of width "WindowWidth"

	double[] secondDerivativeAverageMoving;
	secondDerivativeAverageMoving = derivate(firstDerivativeAverageMoving, 1); // fraction_area
	// With plotting
	plot("2nd derivative with moving average", secondDerivativeAverageMoving);

	//secondDerivative = numericalDerivative(firstDerivative); // fraction_area
	secondDerivative = derivate(colonyFractionArea, 2); // fraction_area
	plot("2nd derivative of colonyFractionArea", secondDerivative);

	//polynomialFitterTest(secondDerivative);	
	// Affichage secondDerivative
	// for (double v : secondDerivative) { System.out.println("secondDerivative > " + v); }  
					
	// perform the gaussian fit
	double[] fitResult = fitGaussian(secondDerivative, 0.0, 255.0);
	*/
}
