package com.vulkantechtt.konvo.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.knowledge.dto.CreateTextSourceRequest;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock KnowledgeSourceRepository sources;
    @Mock KnowledgeIndexer indexer;

    @InjectMocks KnowledgeService service;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createTextDispatchesIndexingAfterCommitWhenInTransaction() {
        UUID sourceId = UUID.randomUUID();
        when(sources.save(any(KnowledgeSource.class))).thenAnswer(inv -> {
            KnowledgeSource source = inv.getArgument(0);
            source.setId(sourceId);
            return source;
        });

        TransactionSynchronizationManager.initSynchronization();

        var response = service.createText(principal(), request());

        assertThat(response.id()).isEqualTo(sourceId);
        verify(indexer, never()).indexAsync(any());

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(indexer).indexAsync(sourceId);
    }

    @Test
    void createTextDispatchesImmediatelyWhenNoTransactionSynchronizationIsActive() {
        UUID sourceId = UUID.randomUUID();
        when(sources.save(any(KnowledgeSource.class))).thenAnswer(inv -> {
            KnowledgeSource source = inv.getArgument(0);
            source.setId(sourceId);
            return source;
        });

        var response = service.createText(principal(), request());

        assertThat(response.id()).isEqualTo(sourceId);
        verify(indexer).indexAsync(sourceId);
    }

    private static KonvoPrincipal principal() {
        return new KonvoPrincipal(
                UUID.randomUUID(),
                "owner@example.com",
                "Owner",
                UUID.randomUUID(),
                Role.OWNER);
    }

    private static CreateTextSourceRequest request() {
        return new CreateTextSourceRequest(
                "FAQ",
                "We open from 6 am to 10 pm every day.");
    }
}
