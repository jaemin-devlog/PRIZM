package com.prizm.infrastructure.storage;

public interface FileStorage {

    String store(long documentId, long versionId, String originalFileName, byte[] content);

    void delete(String storedFilePath);
}
