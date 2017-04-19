package com.abc.datamining.common.function;

public abstract class DoubleDoubleFunction {
	/**
	   * Apply the function to the arguments and return the result
	   *
	   * @param arg1 a double for the first argument
	   * @param arg2 a double for the second argument
	   * @return the result of applying the function
	   */
	  public abstract double apply(double arg1, double arg2);

	  /**
	   * @return true iff f(x, 0) = x for any x
	   */
	  public boolean isLikeRightPlus() {
	    return false;
	  }

	  /**
	   * @return true iff f(0, y) = 0 for any y
	   */
	  public boolean isLikeLeftMult() {
	    return false;
	  }

	  /**
	   * @return true iff f(x, 0) = 0 for any x
	   */
	  public boolean isLikeRightMult() {
	    return false;
	  }

	  /**
	   * @return true iff f(x, 0) = f(0, y) = 0 for any x, y
	   */
	  public boolean isLikeMult() {
	    return isLikeLeftMult() && isLikeRightMult();
	  }

	  /**
	   * @return true iff f(x, y) = f(y, x) for any x, y
	   */
	  public boolean isCommutative() {
	    return false;
	  }

	  /**
	   * @return true iff f(x, f(y, z)) = f(f(x, y), z) for any x, y, z
	   */
	  public boolean isAssociative() {
	    return false;
	  }

	  /**
	   * @return true iff f(x, y) = f(y, x) for any x, y AND f(x, f(y, z)) = f(f(x, y), z) for any x, y, z
	   */
	  public boolean isAssociativeAndCommutative() {
	    return isAssociative() && isCommutative();
	  }

	  /**
	   * @return true iff f(0, 0) != 0
	   */
	  public boolean isDensifying() {
	    return apply(0.0, 0.0) != 0.0;
	  }
}
