package com.vulkantechtt.konvo.knowledge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.ai.AiEmbeddingProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeIndexerTest {

    @Mock KnowledgeSourceRepository sources;
    @Mock AiEmbeddingProvider embedder;
    @Mock KnowledgeChunkWriter writer;

    private KnowledgeIndexer indexer() {
        return new KnowledgeIndexer(sources, embedder, writer);
    }

    private KnowledgeSource source(UUID id, String content) {
        KnowledgeSource s = new KnowledgeSource();
        s.setId(id);
        s.setTenantId(UUID.randomUUID());
        s.setContent(content);
        return s;
    }

    @Test
    void successfulIndexReplacesChunksAndNeverMarksFailed() {
        UUID id = UUID.randomUUID();
        KnowledgeSource s = source(id, "Hello world. This is indexable content.");
        when(sources.findById(id)).thenReturn(Optional.of(s));
        when(embedder.embed(anyList())).thenAnswer(inv -> {
            List<String> chunks = inv.getArgument(0);
            return chunks.stream().map(c -> new float[]{0.1f}).toList();
        });

        indexer().indexAsync(id);

        verify(writer).replaceChunks(eq(s.getTenantId()), eq(id), anyList(), anyList());
        verify(writer, never()).markFailed(any());
        verify(writer, never()).markEmpty(any());
    }

    @Test
    void emptyContentMarksSourceEmpty() {
        UUID id = UUID.randomUUID();
        when(sources.findById(id)).thenReturn(Optional.of(source(id, "   ")));

        indexer().indexAsync(id);

        verify(writer).markEmpty(id);
        verify(writer, never()).replaceChunks(any(), any(), anyList(), anyList());
        verifyNoInteractions(embedder);
    }

    @Test
    void embeddingFailureMarksSourceFailed() {
        UUID id = UUID.randomUUID();
        when(sources.findById(id)).thenReturn(Optional.of(source(id, "Some real content to embed.")));
        when(embedder.embed(anyList())).thenThrow(new RuntimeException("provider 500"));

        indexer().indexAsync(id);

        verify(writer).markFailed(id);
        verify(writer, never()).replaceChunks(any(), any(), anyList(), anyList());
    }

    @Test
    void embeddingCountMismatchMarksSourceFailed() {
        UUID id = UUID.randomUUID();
        when(sources.findById(id)).thenReturn(Optional.of(source(id, "Paragraph one.\n\nParagraph two.")));
        // Return the wrong number of embeddings to trip the guard.
        when(embedder.embed(anyList())).thenReturn(List.of(new float[]{0f}, new float[]{0f}, new float[]{0f}));

        indexer().indexAsync(id);

        verify(writer).markFailed(id);
        verify(writer, never()).replaceChunks(any(), any(), anyList(), anyList());
    }

    @Test
    void missingSourceDoesNothing() {
        UUID id = UUID.randomUUID();
        when(sources.findById(id)).thenReturn(Optional.empty());

        indexer().indexAsync(id);

        verifyNoInteractions(writer, embedder);
    }
}
