package com.prizm.documenttag.controller;

import com.prizm.common.dto.response.ErrorResponse;
import com.prizm.documenttag.service.InvalidDocumentTagException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DocumentTagExceptionHandler {

    @ExceptionHandler(InvalidDocumentTagException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTag(InvalidDocumentTagException exception) {
        HttpStatus status = switch (exception.code()) {
            case INVALID_TAG_NAME, INVALID_TAG_SELECTION -> HttpStatus.BAD_REQUEST;
            case TAG_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(exception.code().name(), exception.getMessage()));
    }
}
