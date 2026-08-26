package com.prizm.jobposting.exception;

/** 채용공고 항목을 Search 길이 제한 안에서 손실 없이 나눌 수 없을 때 발생한다. */
public class InvalidJobPostingSegmentationException extends RuntimeException {

    public InvalidJobPostingSegmentationException(String message) {
        super(message);
    }
}
