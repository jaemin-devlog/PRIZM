package com.prizm.document.exception;

/** 업로드 입력 검증이나 원본 저장 실패를 구분해 전달하는 예외다. */
public class DocumentUploadException extends RuntimeException {

    private final DocumentUploadErrorCode code;

    public DocumentUploadException(DocumentUploadErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public DocumentUploadException(DocumentUploadErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public DocumentUploadErrorCode code() {
        return code;
    }
}
