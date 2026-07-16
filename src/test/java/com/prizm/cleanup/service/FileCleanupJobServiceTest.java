package com.prizm.cleanup.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.prizm.cleanup.repository.FileCleanupJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileCleanupJobServiceTest {

    @Mock FileCleanupJobRepository repository;
    @InjectMocks FileCleanupJobService service;

    @Test
    void registersOnlyNormalizedRelativeStorageKeys() {
        service.registerPendingCleanup("documents/cleanup/file.txt");
        verify(repository).registerPending("documents/cleanup/file.txt");

        assertThatThrownBy(() -> service.registerPendingCleanup("../file.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registerPendingCleanup("C:\\file.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
