package com.prizm.jobposting.exception;

/** Search 요청이 지나치게 늘어나지 않도록 정한 항목 수 상한을 넘을 때 발생한다. */
public class JobPostingItemLimitExceededException extends RuntimeException {

    public JobPostingItemLimitExceededException(int maximumItemCount) {
        super("job posting must produce at most " + maximumItemCount + " items");
    }
}
