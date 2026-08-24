package com.prizm.jobposting.exception;

/** Safe product error used when segmentation would create excessive Search fan-out. */
public class JobPostingItemLimitExceededException extends RuntimeException {

    public JobPostingItemLimitExceededException(int maximumItemCount) {
        super("job posting must produce at most " + maximumItemCount + " items");
    }
}
