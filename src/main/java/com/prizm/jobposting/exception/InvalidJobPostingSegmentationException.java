package com.prizm.jobposting.exception;

/** Safe product error for content that cannot satisfy the bounded item contract without loss. */
public class InvalidJobPostingSegmentationException extends RuntimeException {

    public InvalidJobPostingSegmentationException(String message) {
        super(message);
    }
}
