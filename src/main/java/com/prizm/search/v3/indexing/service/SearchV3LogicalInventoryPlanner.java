package com.prizm.search.v3.indexing.service;

import com.prizm.search.v3.indexing.model.SearchV3LogicalInventoryPlan;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.ChildRow;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.PassageRow;
import com.prizm.search.v3.indexing.structure.SearchV3Structure;
import java.util.List;
import org.springframework.stereotype.Component;

/** B3 구조 결과를 PRZ-039 canonical manifest row로 변환한다. */
@Component
public class SearchV3LogicalInventoryPlanner {

    private final SearchV3InventoryVerifier verifier;

    public SearchV3LogicalInventoryPlanner(SearchV3InventoryVerifier verifier) {
        this.verifier = verifier;
    }

    public SearchV3LogicalInventoryPlan plan(SearchV3Structure structure) {
        List<PassageRow> passages = structure.passages().stream()
                .map(passage -> new PassageRow(
                        0L,
                        passage.passageKey(),
                        passage.passageOrder(),
                        passage.sourceText(),
                        passage.retrievalText(),
                        passage.retrievalTextSha256(),
                        passage.sourcePath(),
                        passage.pageNo(),
                        passage.lineStart(),
                        passage.lineEnd(),
                        passage.codePointStart(),
                        passage.codePointEnd(),
                        passage.parentAnnotationCandidateId(),
                        passage.documentSourceSha256(),
                        passage.sourceBlockIds(),
                        passage.contextSourceBlockIds()))
                .toList();
        List<ChildRow> children = structure.children().stream()
                .map(child -> new ChildRow(
                        0L,
                        child.childKey(),
                        child.childOrder(),
                        child.passageChildOrder(),
                        child.passageKey(),
                        child.sourceBlockType().name(),
                        child.sourceText(),
                        child.sourceTextSha256(),
                        child.sourcePath(),
                        child.pageNo(),
                        child.lineStart(),
                        child.lineEnd(),
                        child.codePointStart(),
                        child.codePointEnd(),
                        child.sourceBlockId(),
                        child.parentAnnotationCandidateId(),
                        child.documentSourceSha256(),
                        child.sourceBlockIds(),
                        child.contextSourceBlockIds()))
                .toList();
        return new SearchV3LogicalInventoryPlan(
                passages,
                children,
                verifier.logicalManifestSha256(passages, children));
    }
}
