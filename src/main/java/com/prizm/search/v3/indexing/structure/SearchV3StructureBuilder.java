package com.prizm.search.v3.indexing.structure;

import com.prizm.document.entity.DocumentFileType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Builds page-aware B3 logical artifacts with generation-global order. */
public final class SearchV3StructureBuilder {

    private final StructuralBlockParser parser;
    private final StructuralEvidenceChildBuilder childBuilder;
    private final StructuralRetrievalPassageBuilder passageBuilder;

    public SearchV3StructureBuilder() {
        this(
                new StructuralBlockParser(),
                new StructuralEvidenceChildBuilder(),
                new StructuralRetrievalPassageBuilder());
    }

    SearchV3StructureBuilder(
            StructuralBlockParser parser,
            StructuralEvidenceChildBuilder childBuilder,
            StructuralRetrievalPassageBuilder passageBuilder) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.childBuilder = Objects.requireNonNull(childBuilder, "childBuilder");
        this.passageBuilder = Objects.requireNonNull(passageBuilder, "passageBuilder");
    }

    public SearchV3Structure build(ExtractedDocumentSource document) {
        Objects.requireNonNull(document, "document");
        return build(document.sourceUnits());
    }

    public SearchV3Structure build(List<StructuralSourceUnit> sourceUnits) {
        Objects.requireNonNull(sourceUnits, "sourceUnits");
        validateSourceUnits(sourceUnits);

        List<SearchV3Structure.PassageArtifact> passages = new ArrayList<>();
        List<SearchV3Structure.ChildArtifact> children = new ArrayList<>();
        Set<String> passageKeys = new HashSet<>();
        Set<String> childKeys = new HashSet<>();

        for (StructuralSourceUnit sourceUnit : sourceUnits) {
            List<StructuralBlock> blocks = parser.parse(sourceUnit);
            List<EvidenceChild> unitChildren = childBuilder.build(blocks);
            List<RetrievalPassage> unitPassages = passageBuilder.build(unitChildren);
            for (RetrievalPassage passage : unitPassages) {
                if (!passageKeys.add(passage.passageId())) {
                    throw new IllegalStateException("duplicate generation Passage key: " + passage.passageId());
                }
                int passageOrder = passages.size();
                SourceProvenance first = passage.firstChildProvenance();
                SourceProvenance last = passage.lastChildProvenance();
                passages.add(new SearchV3Structure.PassageArtifact(
                        passage.passageId(),
                        passageOrder,
                        passage.sourceText(),
                        passage.retrievalText(),
                        passage.retrievalTextSha256(),
                        passage.sourcePath(),
                        passage.pageNo(),
                        first.lineStart(),
                        last.lineEnd(),
                        first.codePointStart(),
                        last.codePointEnd(),
                        passage.parentAnnotationCandidateId(),
                        first.documentSourceSha256(),
                        passage.evidenceChildIds(),
                        passage.sourceBlockIds(),
                        passage.contextSourceBlockIds()));

                for (int passageChildOrder = 0;
                        passageChildOrder < passage.evidenceChildren().size();
                        passageChildOrder++) {
                    EvidenceChild child = passage.evidenceChildren().get(passageChildOrder);
                    if (!childKeys.add(child.childId())) {
                        throw new IllegalStateException("duplicate generation Child key: " + child.childId());
                    }
                    SourceProvenance source = child.provenance();
                    children.add(new SearchV3Structure.ChildArtifact(
                            child.childId(),
                            children.size(),
                            passageChildOrder,
                            passage.passageId(),
                            child.sourceBlockType(),
                            child.sourceText(),
                            child.sourceTextSha256(),
                            source.sourcePath(),
                            source.pageNo(),
                            source.lineStart(),
                            source.lineEnd(),
                            source.codePointStart(),
                            source.codePointEnd(),
                            source.sourceBlockId(),
                            source.parentAnnotationCandidateId(),
                            source.documentSourceSha256(),
                            child.sourceBlockIds(),
                            child.contextSourceBlockIds()));
                }
            }
        }

        if (passages.isEmpty() || children.isEmpty()) {
            throw new SearchV3StructureException(
                    SearchV3StructureException.Reason.EMPTY_STRUCTURE,
                    "extracted document produced no searchable B3 Passage or EvidenceChild");
        }
        String documentSourceSha256 = sourceUnits.get(0).documentSourceSha256();
        validateArtifactMembership(passages, children, documentSourceSha256);
        return new SearchV3Structure(documentSourceSha256, passages, children);
    }

    private void validateSourceUnits(List<StructuralSourceUnit> sourceUnits) {
        if (sourceUnits.isEmpty()) {
            throw new SearchV3StructureException(
                    SearchV3StructureException.Reason.EMPTY_STRUCTURE,
                    "extracted document has no structural source unit");
        }
        StructuralSourceUnit first = Objects.requireNonNull(sourceUnits.get(0), "source unit");
        DocumentFileType fileType = first.pageNo() == null
                ? DocumentFileType.TXT
                : DocumentFileType.PDF;
        new ExtractedDocumentSource(
                first.documentId(),
                first.documentVersionId(),
                first.sourcePath(),
                fileType,
                first.documentSourceSha256(),
                sourceUnits);
    }

    private void validateArtifactMembership(
            List<SearchV3Structure.PassageArtifact> passages,
            List<SearchV3Structure.ChildArtifact> children,
            String documentSourceSha256) {
        Set<String> expectedChildren = new HashSet<>();
        for (int index = 0; index < passages.size(); index++) {
            SearchV3Structure.PassageArtifact passage = passages.get(index);
            if (passage.passageOrder() != index
                    || !documentSourceSha256.equals(passage.documentSourceSha256())) {
                throw new IllegalStateException("generation Passage order or source hash changed");
            }
            for (String childId : passage.evidenceChildIds()) {
                if (!expectedChildren.add(childId)) {
                    throw new IllegalStateException("EvidenceChild appears in multiple B3 Passages");
                }
            }
        }
        Set<String> actualChildren = new HashSet<>();
        for (int index = 0; index < children.size(); index++) {
            SearchV3Structure.ChildArtifact child = children.get(index);
            if (child.childOrder() != index
                    || !documentSourceSha256.equals(child.documentSourceSha256())
                    || !actualChildren.add(child.childKey())) {
                throw new IllegalStateException("generation Child order, source hash, or identity changed");
            }
        }
        if (!expectedChildren.equals(actualChildren)) {
            throw new IllegalStateException("B3 Passage membership differs from Child inventory");
        }
    }
}
