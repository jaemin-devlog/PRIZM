package com.prizm.documenttag.controller;

import com.prizm.common.dto.response.ErrorResponse;
import com.prizm.documenttag.service.InvalidDocumentTagException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 잘못된 태그 입력은 400, 접근할 수 없거나 없는 태그는 404 응답으로 변환한다. */
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
