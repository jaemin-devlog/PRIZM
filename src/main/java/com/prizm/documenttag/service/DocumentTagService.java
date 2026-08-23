package com.prizm.documenttag.service;

import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.documenttag.dto.TagResponse;
import com.prizm.documenttag.dto.TagUsageResponse;
import com.prizm.documenttag.dto.TaggedDocumentsResponse;
import com.prizm.documenttag.model.DocumentTag;
import com.prizm.documenttag.repository.DocumentTagRepository;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentTagService {

    public static final int MAX_TAGS_PER_DOCUMENT = 20;
    private static final int SEARCH_LIMIT = 20;

    private final DocumentTagRepository tagRepository;
    private final DocumentRepository documentRepository;

    public DocumentTagService(
            DocumentTagRepository tagRepository,
            DocumentRepository documentRepository) {
        this.tagRepository = tagRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional(readOnly = true)
    public List<TagResponse> search(Long ownerUserId, String query) {
        String normalizedQuery = query == null || query.isBlank()
                ? ""
                : TagNameNormalizer.normalizedName(query);
        return tagRepository.searchAccessible(ownerUserId, normalizedQuery, SEARCH_LIMIT).stream()
                .map(TagResponse::from)
                .toList();
    }

    @Transactional
    public CreateResult createUserTag(Long ownerUserId, String requestedName) {
        String name = TagNameNormalizer.displayName(requestedName);
        String normalizedName = TagNameNormalizer.normalizedName(name);
        var systemTag = tagRepository.findSystemByNormalizedName(normalizedName);
        if (systemTag.isPresent()) {
            return new CreateResult(TagResponse.from(systemTag.orElseThrow()), false);
        }
        var existingUserTag = tagRepository.findUserByNormalizedName(ownerUserId, normalizedName);
        if (existingUserTag.isPresent()) {
            return new CreateResult(TagResponse.from(existingUserTag.orElseThrow()), false);
        }
        return new CreateResult(
                TagResponse.from(tagRepository.createUserTag(ownerUserId, name, normalizedName)),
                true);
    }

    @Transactional(readOnly = true)
    public List<TagResponse> getDocumentTags(Long ownerUserId, Long documentId) {
        requireOwnedDocument(ownerUserId, documentId);
        return tagRepository.findDocumentTags(ownerUserId, documentId).stream()
                .map(TagResponse::from)
                .toList();
    }

    @Transactional
    public List<TagResponse> replaceDocumentTags(
            Long ownerUserId,
            Long documentId,
            List<Long> requestedTagIds) {
        requireOwnedDocumentForUpdate(ownerUserId, documentId);
        List<Long> tagIds = requireAccessibleTagIds(ownerUserId, requestedTagIds);
        tagRepository.replaceDocumentTags(ownerUserId, documentId, tagIds);
        return tagRepository.findDocumentTags(ownerUserId, documentId).stream()
                .map(TagResponse::from)
                .toList();
    }

    @Transactional
    public void removeDocumentTag(Long ownerUserId, Long documentId, Long tagId) {
        requireOwnedDocumentForUpdate(ownerUserId, documentId);
        tagRepository.removeDocumentTag(ownerUserId, documentId, tagId);
    }

    @Transactional(readOnly = true)
    public List<TagUsageResponse> getUsage(Long ownerUserId) {
        return tagRepository.findUsage(ownerUserId);
    }

    @Transactional(readOnly = true)
    public TaggedDocumentsResponse getTaggedDocuments(Long ownerUserId, Long tagId) {
        DocumentTag tag = tagRepository.findAccessibleById(ownerUserId, tagId)
                .orElseThrow(() -> new InvalidDocumentTagException(
                        DocumentTagErrorCode.TAG_NOT_FOUND,
                        "Tag was not found."));
        return new TaggedDocumentsResponse(
                TagResponse.from(tag),
                tagRepository.findTaggedDocuments(ownerUserId, tagId));
    }

    @Transactional(readOnly = true)
    public List<Long> requireAccessibleTagIds(Long ownerUserId, List<Long> requestedTagIds) {
        List<Long> tagIds = distinctTagIds(requestedTagIds);
        List<DocumentTag> tags = tagRepository.findAccessibleByIds(ownerUserId, tagIds);
        if (tags.size() != tagIds.size()) {
            throw new InvalidDocumentTagException(
                    DocumentTagErrorCode.TAG_NOT_FOUND,
                    "One or more tags were not found.");
        }
        return tags.stream().map(DocumentTag::id).toList();
    }

    @Transactional
    public void attachToNewDocument(Long ownerUserId, Long documentId, List<Long> validatedTagIds) {
        if (!validatedTagIds.isEmpty()) {
            tagRepository.replaceDocumentTags(ownerUserId, documentId, validatedTagIds);
        }
    }

    private List<Long> distinctTagIds(List<Long> requestedTagIds) {
        if (requestedTagIds == null || requestedTagIds.isEmpty()) {
            return List.of();
        }
        if (requestedTagIds.size() > MAX_TAGS_PER_DOCUMENT) {
            throw new InvalidDocumentTagException(
                    DocumentTagErrorCode.INVALID_TAG_SELECTION,
                    "A document can have at most 20 tags.");
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long tagId : requestedTagIds) {
            if (tagId == null || tagId <= 0) {
                throw new InvalidDocumentTagException(
                        DocumentTagErrorCode.INVALID_TAG_SELECTION,
                        "tagIds must contain positive identifiers.");
            }
            distinct.add(tagId);
        }
        return List.copyOf(distinct);
    }

    private void requireOwnedDocument(Long ownerUserId, Long documentId) {
        if (documentRepository.findByIdAndOwnerUserId(documentId, ownerUserId).isEmpty()) {
            throw new DocumentNotFoundException(documentId);
        }
    }

    private void requireOwnedDocumentForUpdate(Long ownerUserId, Long documentId) {
        if (documentRepository.findByIdAndOwnerUserIdForUpdate(documentId, ownerUserId).isEmpty()) {
            throw new DocumentNotFoundException(documentId);
        }
    }

    public record CreateResult(TagResponse tag, boolean created) {
    }
}
