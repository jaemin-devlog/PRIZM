package com.prizm.jobposting.controller;

import com.prizm.common.dto.response.ErrorResponse;
import com.prizm.jobposting.exception.InvalidJobPostingSegmentationException;
import com.prizm.jobposting.exception.JobPostingItemLimitExceededException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 항목 분리 과정에서 발생한 입력 오류를 공통 오류 응답으로 바꾸되 내부 구현 정보는 노출하지 않는다. */
@RestControllerAdvice(assignableTypes = JobPostingSegmentationController.class)
public class JobPostingSegmentationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("job posting content is invalid");
        return invalidContent(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return invalidContent("job posting request body is invalid");
    }

    @ExceptionHandler(JobPostingItemLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleItemLimit(JobPostingItemLimitExceededException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("JOB_POSTING_ITEM_LIMIT_EXCEEDED", exception.getMessage()));
    }

    @ExceptionHandler(InvalidJobPostingSegmentationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSegmentation(
            InvalidJobPostingSegmentationException exception) {
        return invalidContent(exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> invalidContent(String message) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_JOB_POSTING_CONTENT", message));
    }
}
