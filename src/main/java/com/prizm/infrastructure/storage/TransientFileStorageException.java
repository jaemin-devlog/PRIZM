package com.prizm.infrastructure.storage;

/** 일시적인 I/O 문제처럼 재시도하면 해결될 수 있는 저장소 읽기·삭제 실패다. */
public class TransientFileStorageException extends FileStorageException {

    public TransientFileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
