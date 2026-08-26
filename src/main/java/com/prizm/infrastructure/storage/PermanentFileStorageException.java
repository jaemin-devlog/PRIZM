package com.prizm.infrastructure.storage;

/** 같은 입력으로 다시 시도해도 해결되지 않는 저장소 읽기·삭제 실패다. */
public class PermanentFileStorageException extends FileStorageException {

    public PermanentFileStorageException(String message) {
        super(message);
    }

    public PermanentFileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
