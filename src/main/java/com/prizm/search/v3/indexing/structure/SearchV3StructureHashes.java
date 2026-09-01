package com.prizm.search.v3.indexing.structure;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic SHA-256 helpers for Search V3 structural inputs and artifacts. */
public final class SearchV3StructureHashes {

    private SearchV3StructureHashes() {
    }

    public static String sha256Utf8(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(CanonicalWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writer.write(output);
            }
            return sha256(bytes.toByteArray());
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not canonicalize Search V3 structural input.", exception);
        }
    }

    static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    @FunctionalInterface
    interface CanonicalWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
