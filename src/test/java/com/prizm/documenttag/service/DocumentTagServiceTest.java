package com.prizm.documenttag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.document.entity.Document;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.documenttag.model.DocumentTag;
import com.prizm.documenttag.model.TagSource;
import com.prizm.documenttag.repository.DocumentTagRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentTagServiceTest {

    @Mock DocumentTagRepository tagRepository;
    @Mock DocumentRepository documentRepository;

    DocumentTagService service;

    @BeforeEach
    void setUp() {
        service = new DocumentTagService(tagRepository, documentRepository);
    }

    @Test
    void returnsSystemAndOwnedUserTagsFromGenericSearch() {
        when(tagRepository.searchAccessible(7L, "spring", 20)).thenReturn(List.of(
                tag(1L, "Spring Boot", TagSource.SYSTEM, null),
                tag(2L, "Spring Modulith", TagSource.USER, 7L)));

        assertThat(service.search(7L, " Spring "))
                .extracting(response -> response.name())
                .containsExactly("Spring Boot", "Spring Modulith");
    }

    @Test
    void reusesSystemOrOwnerDuplicateInsteadOfCreatingAnotherTag() {
        DocumentTag system = tag(1L, "Redis", TagSource.SYSTEM, null);
        when(tagRepository.findSystemByNormalizedName("redis")).thenReturn(Optional.of(system));

        var result = service.createUserTag(7L, " redis ");

        assertThat(result.created()).isFalse();
        assertThat(result.tag().tagId()).isEqualTo(1L);
    }

    @Test
    void rejectsForeignOrUnknownTagDuringDocumentAssignment() {
        Document document = Document.create(7L, "Guide");
        ReflectionTestUtils.setField(document, "id", 11L);
        when(documentRepository.findByIdAndOwnerUserIdForUpdate(11L, 7L))
                .thenReturn(Optional.of(document));
        when(tagRepository.findAccessibleByIds(7L, List.of(91L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceDocumentTags(7L, 11L, List.of(91L)))
                .isInstanceOf(InvalidDocumentTagException.class)
                .extracting(exception -> ((InvalidDocumentTagException) exception).code())
                .isEqualTo(DocumentTagErrorCode.TAG_NOT_FOUND);
    }

    @Test
    void replacesDuplicateIdentifiersWithOneDocumentTagLink() {
        Document document = Document.create(7L, "Guide");
        ReflectionTestUtils.setField(document, "id", 11L);
        DocumentTag redis = tag(1L, "Redis", TagSource.SYSTEM, null);
        when(documentRepository.findByIdAndOwnerUserIdForUpdate(11L, 7L))
                .thenReturn(Optional.of(document));
        when(tagRepository.findAccessibleByIds(7L, List.of(1L))).thenReturn(List.of(redis));
        when(tagRepository.findDocumentTags(7L, 11L)).thenReturn(List.of(redis));

        assertThat(service.replaceDocumentTags(7L, 11L, List.of(1L, 1L)))
                .extracting(response -> response.name())
                .containsExactly("Redis");
        verify(tagRepository).replaceDocumentTags(7L, 11L, List.of(1L));
    }

    private DocumentTag tag(Long id, String name, TagSource source, Long ownerUserId) {
        return new DocumentTag(id, name, name.toLowerCase(), source, ownerUserId, Instant.EPOCH);
    }
}
