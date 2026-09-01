package com.prizm.search.v3.indexing.structure;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.ingestion.service.PageText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Canonical extracted text and page-aware parser inputs for one immutable document version. */
public record ExtractedDocumentSource(
        long documentId,
        long documentVersionId,
        String sourcePath,
        DocumentFileType fileType,
        String documentSourceSha256,
        List<StructuralSourceUnit> sourceUnits) {

    private static final String HASH_FORMAT = "PRIZM_SEARCH_V3_EXTRACTED_DOCUMENT_SOURCE_V1";

    public ExtractedDocumentSource {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(fileType, "fileType");
        Objects.requireNonNull(documentSourceSha256, "documentSourceSha256");
        Objects.requireNonNull(sourceUnits, "sourceUnits");
        sourceUnits = List.copyOf(sourceUnits);
        if (documentId < 1 || documentVersionId < 1) {
            throw new IllegalArgumentException("document lineage IDs must be positive");
        }
        if (sourcePath.isBlank() || sourceUnits.isEmpty()) {
            throw new IllegalArgumentException("source path and units must be non-empty");
        }
        if (sourcePath.length() > 500) {
            throw new IllegalArgumentException("sourcePath exceeds the Search V3 storage limit");
        }
        requireSha256(documentSourceSha256);
        validateUnits(
                documentId,
                documentVersionId,
                sourcePath,
                fileType,
                documentSourceSha256,
                sourceUnits);
    }

    public static ExtractedDocumentSource from(
            long documentId,
            long documentVersionId,
            String sourcePath,
            DocumentFileType fileType,
            List<PageText> pages) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(fileType, "fileType");
        Objects.requireNonNull(pages, "pages");
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("extracted document must contain at least one source unit");
        }
        if (fileType == DocumentFileType.TXT && pages.size() != 1) {
            throw new IllegalArgumentException("TXT extraction must contain exactly one source unit");
        }
        if (fileType == DocumentFileType.TXT && pages.get(0).pageNumber() != 1) {
            throw new IllegalArgumentException("TXT extraction must use source page number 1");
        }

        validatePageOrder(pages);
        String documentHash = canonicalDocumentHash(fileType, pages);
        List<StructuralSourceUnit> units = new ArrayList<>(pages.size());
        for (int index = 0; index < pages.size(); index++) {
            PageText page = Objects.requireNonNull(pages.get(index), "page");
            String text = Objects.requireNonNull(page.text(), "page text");
            Integer pageNo = fileType == DocumentFileType.TXT ? null : page.pageNumber();
            String key = fileType == DocumentFileType.TXT
                    ? txtSourceUnitKey(documentId, documentVersionId)
                    : pdfSourceUnitKey(documentId, documentVersionId, page.pageNumber());
            units.add(new StructuralSourceUnit(
                    documentId,
                    documentVersionId,
                    key,
                    index,
                    sourcePath,
                    pageNo,
                    text,
                    SearchV3StructureHashes.sha256Utf8(text),
                    documentHash));
        }
        return new ExtractedDocumentSource(
                documentId, documentVersionId, sourcePath, fileType, documentHash, units);
    }

    private static void validatePageOrder(List<PageText> pages) {
        int previous = 0;
        Set<Integer> pageNumbers = new HashSet<>();
        for (PageText page : pages) {
            Objects.requireNonNull(page, "page");
            Objects.requireNonNull(page.text(), "page text");
            if (page.pageNumber() < 1
                    || page.pageNumber() <= previous
                    || !pageNumbers.add(page.pageNumber())) {
                throw new IllegalArgumentException(
                        "extracted page numbers must be unique, one-based, and strictly increasing");
            }
            previous = page.pageNumber();
        }
    }

    private static String canonicalDocumentHash(DocumentFileType fileType, List<PageText> pages) {
        return SearchV3StructureHashes.sha256(output -> {
            SearchV3StructureHashes.writeString(output, HASH_FORMAT);
            SearchV3StructureHashes.writeString(output, fileType.name());
            output.writeInt(pages.size());
            for (PageText page : pages) {
                output.writeInt(page.pageNumber());
                SearchV3StructureHashes.writeString(output, page.text());
            }
        });
    }

    private static void validateUnits(
            long documentId,
            long documentVersionId,
            String sourcePath,
            DocumentFileType fileType,
            String documentSourceSha256,
            List<StructuralSourceUnit> sourceUnits) {
        if (fileType == DocumentFileType.TXT && sourceUnits.size() != 1) {
            throw new IllegalArgumentException("TXT extraction must contain exactly one source unit");
        }

        List<PageText> canonicalPages = new ArrayList<>(sourceUnits.size());
        int previousPageNo = 0;
        Set<Integer> pageNumbers = new HashSet<>();
        for (int index = 0; index < sourceUnits.size(); index++) {
            StructuralSourceUnit unit = Objects.requireNonNull(sourceUnits.get(index), "source unit");
            if (unit.documentId() != documentId
                    || unit.documentVersionId() != documentVersionId
                    || !unit.sourcePath().equals(sourcePath)
                    || !unit.documentSourceSha256().equals(documentSourceSha256)) {
                throw new IllegalArgumentException(
                        "source units must match extracted document lineage and source hash");
            }
            if (unit.sourceUnitOrder() != index) {
                throw new IllegalArgumentException(
                        "source units must have contiguous generation order");
            }

            int canonicalPageNo;
            String expectedKey;
            if (fileType == DocumentFileType.TXT) {
                if (unit.pageNo() != null) {
                    throw new IllegalArgumentException("TXT source unit pageNo must be null");
                }
                canonicalPageNo = 1;
                expectedKey = txtSourceUnitKey(documentId, documentVersionId);
            }
            else {
                if (unit.pageNo() == null) {
                    throw new IllegalArgumentException("PDF source unit pageNo must be one-based");
                }
                canonicalPageNo = unit.pageNo();
                if (canonicalPageNo <= previousPageNo || !pageNumbers.add(canonicalPageNo)) {
                    throw new IllegalArgumentException(
                            "PDF source unit page numbers must be unique and strictly increasing");
                }
                previousPageNo = canonicalPageNo;
                expectedKey = pdfSourceUnitKey(documentId, documentVersionId, canonicalPageNo);
            }
            if (!unit.sourceUnitKey().equals(expectedKey)) {
                throw new IllegalArgumentException("source unit key is not canonical: " + expectedKey);
            }
            canonicalPages.add(new PageText(canonicalPageNo, unit.sourceText()));
        }

        if (!canonicalDocumentHash(fileType, canonicalPages).equals(documentSourceSha256)) {
            throw new IllegalArgumentException(
                    "documentSourceSha256 does not match canonical extracted source units");
        }
    }

    private static String txtSourceUnitKey(long documentId, long documentVersionId) {
        return "D%d-V%d-TXT".formatted(documentId, documentVersionId);
    }

    private static String pdfSourceUnitKey(
            long documentId,
            long documentVersionId,
            int pageNumber) {
        return "D%d-V%d-P%04d".formatted(documentId, documentVersionId, pageNumber);
    }

    private static void requireSha256(String value) {
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("documentSourceSha256 must be lowercase SHA-256");
        }
    }
}
