package com.prizm.document.controller;

import com.prizm.common.dto.response.ErrorResponse;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.exception.InvalidDocumentVersionStateException;
import com.prizm.document.exception.DocumentUploadErrorCode;
import com.prizm.document.exception.DocumentUploadException;
import com.prizm.document.exception.DocumentThumbnailException;
import com.prizm.document.exception.DocumentManagementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 업로드·조회 실패를 API에서 일관된 오류 응답으로 변환한다. */
@RestControllerAdvice
public class DocumentExceptionHandler {

    @ExceptionHandler(DocumentUploadException.class)
    public ResponseEntity<ErrorResponse> handleUpload(DocumentUploadException exception) {
        return ResponseEntity.status(statusFor(exception.code()))
                .body(new ErrorResponse(exception.code().name(), exception.getMessage()));
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(DocumentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("DOCUMENT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(DocumentVersionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleVersionNotFound(DocumentVersionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("DOCUMENT_VERSION_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(DocumentThumbnailException.class)
    public ResponseEntity<ErrorResponse> handleThumbnail(DocumentThumbnailException exception) {
        HttpStatus status = switch (exception.code()) {
            case UNSUPPORTED_FILE_TYPE, UNREADABLE_PDF -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case ORIGINAL_FILE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ORIGINAL_FILE_READ_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(exception.code().name(), exception.getMessage()));
    }

    @ExceptionHandler(InvalidDocumentVersionStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DOCUMENT_VERSION_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(DocumentManagementException.class)
    public ResponseEntity<ErrorResponse> handleManagement(DocumentManagementException exception) {
        HttpStatus status = switch (exception.code()) {
            case DOCUMENT_PROCESSING -> HttpStatus.CONFLICT;
            case INVALID_TITLE, INVALID_DOCUMENT_TYPE -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(exception.code().name(), exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("FILE_SIZE_EXCEEDED", "file exceeds the configured size limit"));
    }

    private HttpStatus statusFor(DocumentUploadErrorCode code) {
        return switch (code) {
            case FILE_SIZE_EXCEEDED -> HttpStatus.PAYLOAD_TOO_LARGE;
            case FILE_READ_FAILED, FILE_STORAGE_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
