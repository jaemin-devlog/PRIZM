package com.prizm.search;

import com.prizm.embedding.EmbeddingErrorCode;
import com.prizm.embedding.EmbeddingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
            case EMBEDDING_EMPTY_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case EMBEDDING_DIMENSION_MISMATCH -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public record ErrorResponse(String code, String message) {
    }
}
