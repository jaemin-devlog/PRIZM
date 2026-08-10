package com.prizm.careerkeyword.controller;

import com.prizm.careerkeyword.exception.InvalidCareerKeywordException;
import com.prizm.common.dto.response.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CareerKeywordExceptionHandler {

    @ExceptionHandler(InvalidCareerKeywordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidKeyword(InvalidCareerKeywordException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_CAREER_KEYWORD", exception.getMessage()));
    }
}
