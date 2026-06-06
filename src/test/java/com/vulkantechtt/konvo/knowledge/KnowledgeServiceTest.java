package com.vulkantechtt.konvo.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.vulkantechtt.konvo.auth.EmailVerificationGuard;
import com.vulkantechtt.konvo.common.KonvoException;
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
    @Mock TextExtractionService extractor;
    @Mock EmailVerificationGuard emailVerification;

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

    @Test
    void createTextRequiresVerifiedEmail() {
        KonvoPrincipal principal = principal();
        doThrow(new KonvoException(org.springframework.http.HttpStatus.FORBIDDEN,
                "email_verification_required", "Verify your email to continue."))
                .when(emailVerification).requireVerified(principal);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createText(principal, request()))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("Verify your email");
        verify(sources, never()).save(any());
    }

    @Test
    void getReturnsSavedSourceContentForTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        KnowledgeSource source = new KnowledgeSource();
        source.setId(sourceId);
        source.setTenantId(tenantId);
        source.setTitle("FAQ");
        source.setType(KnowledgeSourceType.text);
        source.setStatus(KnowledgeSourceStatus.ready);
        source.setContent("We open from 6 am to 10 pm every day.");
        source.setCharCount(source.getContent().length());
        when(sources.findById(sourceId)).thenReturn(java.util.Optional.of(source));

        var response = service.get(principal(tenantId), sourceId);

        assertThat(response.id()).isEqualTo(sourceId);
        assertThat(response.content()).isEqualTo("We open from 6 am to 10 pm every day.");
    }

    private static KonvoPrincipal principal() {
        return principal(UUID.randomUUID());
    }

    private static KonvoPrincipal principal(UUID tenantId) {
        return new KonvoPrincipal(
                UUID.randomUUID(),
                "owner@example.com",
                "Owner",
                tenantId,
                Role.OWNER);
    }

    private static CreateTextSourceRequest request() {
        return new CreateTextSourceRequest(
                "FAQ",
                "We open from 6 am to 10 pm every day.");
    }
}
