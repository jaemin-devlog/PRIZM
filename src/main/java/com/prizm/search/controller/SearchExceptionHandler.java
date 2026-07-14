package com.prizm.search.controller;

import com.prizm.common.dto.response.ErrorResponse;
import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.search.exception.InvalidSearchQueryException;
import com.prizm.search.exception.SearchResultNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 검색 입력·임베딩·결과 없음 오류를 HTTP 응답으로 변환한다. */
@RestControllerAdvice
public class SearchExceptionHandler {

    @ExceptionHandler(EmbeddingException.class)
    public ResponseEntity<ErrorResponse> handleEmbedding(EmbeddingException exception) {
        return ResponseEntity.status(statusFor(exception.code()))
                .body(new ErrorResponse(exception.code().name(), exception.getMessage()));
    }

    @ExceptionHandler({InvalidSearchQueryException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> handleInvalidQuery(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validationException
                ? validationException.getBindingResult().getAllErrors().get(0).getDefaultMessage()
                : exception.getMessage();
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_SEARCH_QUERY", message));
    }

    @ExceptionHandler(SearchResultNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResult(SearchResultNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SEARCH_NO_RESULT", exception.getMessage()));
    }

    private HttpStatus statusFor(EmbeddingErrorCode code) {
        return switch (code) {
            case OLLAMA_UNAVAILABLE, OLLAMA_MODEL_NOT_INSTALLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case EMBEDDING_EMPTY_RESPONSE, EMBEDDING_INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case EMBEDDING_DIMENSION_MISMATCH -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
