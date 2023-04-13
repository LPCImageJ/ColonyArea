package com.mycompany.utils;

import org.apache.commons.math3.exception.DimensionMismatchException;

/**
 * Implements the moving average/mean filter of series data
 * github.com/octavian-h/time-series-math
 */
 
public class MovingAverageFilter {
	
	private final int observations;
	private final boolean symmetric;
	private final double[] weights;
	 
	/**
     * Creates a new instance of this class with the given number 
     *    of observations to smooth from both sides.
     * @param observations the number of observations
     * example:
     *  - new MovingAverageFilter(2, true, new double[]{});
     *  - new MovingAverageFilter(2, false, new double[]{});
     */
	public MovingAverageFilter(int observations) {
		this(observations, true, null);
	}
	
	/**
     * Creates a new instance of this class.
     *
     * @param observations the number of observations
     * @param symmetric    smooth from both sides
     * @param weights      the weights to be used for smoothing; it can be null
     * @throws DimensionMismatchException 
     *     if the length of the weights is not equal to the number of observations
     *     or the size of the window (2 * observations + 1) if the smoothing is symmetric
     */
    public MovingAverageFilter(int observations, boolean symmetric, double[] weights) {
        this.observations = observations;
        this.symmetric = symmetric;
        if (weights != null) {
            if (!symmetric && weights.length != observations) {
                throw new DimensionMismatchException(weights.length, observations);
            }
            if (symmetric && weights.length != 2 * observations + 1) {
                throw new DimensionMismatchException(weights.length, 2 * observations + 1);
            }
        }
        this.weights = weights;
    }
    
    /**
     * Filter the sequence of values.
     *
     * @param values the sequence of values
     * @return the filtered sequence
     */
    public double[] Filter(double[] values) {
        return filterWithoutWeights(values);
    }
    
    /**
     * Do filter the sequence of values without weights.
     *
     * @param values the sequence of values
     * @return the filtered sequence
     */
    private double[] filterWithoutWeights(double[] values) {
        int length = values.length;
        double[] result = new double[length];

        int windowSize;
        int index;
        if (symmetric) {
            windowSize = 2 * observations + 1;
            index = observations;
        } else {
            windowSize = observations;
            index = observations - 1;
        }
        // compute the sum of the first window
        double sum = 0;
        for (int i = 0; i < windowSize && i < length; i++) {
            sum += values[i];
        }

        // copy the beginning non smoothed values to the result
        System.arraycopy(values, 0, result, 0, index);

        for (int i = windowSize - 1; i < length; i++) {
            if (i != windowSize - 1) {
                sum += values[i];
                sum -= values[i - windowSize];
            }
            result[index] = sum / windowSize;
            index++;
        }
        if (symmetric) {
            // copy the end non smoothed values to the result
            System.arraycopy(values, index, result, index, length - index);
        }
        return result;
    }
}