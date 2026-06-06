package com.vulkantechtt.konvo.knowledge;

import com.vulkantechtt.konvo.auth.EmailVerificationGuard;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.knowledge.dto.CreateTextSourceRequest;
import com.vulkantechtt.konvo.knowledge.dto.CreateUrlSourceRequest;
import com.vulkantechtt.konvo.knowledge.dto.KnowledgeSourceDetailResponse;
import com.vulkantechtt.konvo.knowledge.dto.KnowledgeSourceResponse;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * CRUD for knowledge sources. Creates persist the source as {@code indexing}
 * then hand off to {@link KnowledgeIndexer} which chunks + embeds + flips to
 * {@code ready}. Reads are paginated; deletes cascade chunks via FK.
 */
@Service
public class KnowledgeService {

    /** Upper bound on extracted text we persist; mirrors the extractor's cap. */
    private static final int MAX_CONTENT_CHARS = 200_000;

    private final KnowledgeSourceRepository sources;
    private final KnowledgeIndexer indexer;
    private final TextExtractionService extractor;
    private final EmailVerificationGuard emailVerification;

    public KnowledgeService(
            KnowledgeSourceRepository sources,
            KnowledgeIndexer indexer,
            TextExtractionService extractor,
            EmailVerificationGuard emailVerification) {
        this.sources = sources;
        this.indexer = indexer;
        this.extractor = extractor;
        this.emailVerification = emailVerification;
    }

    @Transactional(readOnly = true)
    public PageResponse<KnowledgeSourceResponse> list(KonvoPrincipal principal, Pageable pageable) {
        return PageResponse.from(sources
                .findByTenantIdOrderByCreatedAtDesc(principal.tenantId(), pageable)
                .map(KnowledgeService::toResponse));
    }

    @Transactional(readOnly = true)
    public KnowledgeSourceDetailResponse get(KonvoPrincipal principal, UUID id) {
        return toDetailResponse(requireOwned(principal, id));
    }

    @Transactional
    public KnowledgeSourceResponse createText(KonvoPrincipal principal, CreateTextSourceRequest req) {
        emailVerification.requireVerified(principal);
        KnowledgeSource source = new KnowledgeSource();
        source.setTenantId(principal.tenantId());
        source.setTitle(req.title());
        source.setType(KnowledgeSourceType.text);
        source.setStatus(KnowledgeSourceStatus.indexing);
        source.setContent(req.content());
        source.setCharCount(req.content().length());
        source.setCreatedByUserId(principal.userId());
        KnowledgeSource saved = sources.save(source);
        dispatchIndexAfterCommit(saved.getId());
        return toResponse(saved);
    }

    /**
     * Ingest an uploaded PDF or spreadsheet. The file's text is extracted
     * synchronously (so the user gets immediate "unreadable file" feedback),
     * then chunk + embed runs in the background like every other source.
     */
    @Transactional
    public KnowledgeSourceResponse createFromFile(KonvoPrincipal principal, String title, MultipartFile file) {
        emailVerification.requireVerified(principal);
        if (file == null || file.isEmpty()) {
            throw KonvoException.badRequest("Choose a file to upload");
        }
        String filename = file.getOriginalFilename();
        KnowledgeSourceType type = detectFileType(file.getContentType(), filename);

        byte[] data;
        try {
            data = file.getBytes();
        } catch (java.io.IOException e) {
            throw KonvoException.badRequest("Could not read the uploaded file");
        }
        String text = extractor.extractFromFile(data, filename, file.getContentType());

        String resolvedTitle = firstNonBlank(title, stripExtension(filename), "Uploaded document");
        return persistExtracted(principal, resolvedTitle, type, text);
    }

    /** Ingest a web page by URL: fetch, strip to text, then index. */
    @Transactional
    public KnowledgeSourceResponse createFromUrl(KonvoPrincipal principal, CreateUrlSourceRequest req) {
        emailVerification.requireVerified(principal);
        String text = extractor.extractFromUrl(req.url());
        String resolvedTitle = firstNonBlank(req.title(), hostOf(req.url()), "Imported page");
        return persistExtracted(principal, resolvedTitle, KnowledgeSourceType.url, text);
    }

    private KnowledgeSourceResponse persistExtracted(
            KonvoPrincipal principal, String title, KnowledgeSourceType type, String text) {
        String content = text.length() > MAX_CONTENT_CHARS ? text.substring(0, MAX_CONTENT_CHARS) : text;
        KnowledgeSource source = new KnowledgeSource();
        source.setTenantId(principal.tenantId());
        source.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        source.setType(type);
        source.setStatus(KnowledgeSourceStatus.indexing);
        source.setContent(content);
        source.setCharCount(content.length());
        source.setCreatedByUserId(principal.userId());
        KnowledgeSource saved = sources.save(source);
        dispatchIndexAfterCommit(saved.getId());
        return toResponse(saved);
    }

    private static KnowledgeSourceType detectFileType(String contentType, String filename) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (ct.contains("pdf") || name.endsWith(".pdf")) {
            return KnowledgeSourceType.pdf;
        }
        if (ct.contains("spreadsheet") || ct.contains("excel") || ct.contains("csv")
                || name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) {
            return KnowledgeSourceType.spreadsheet;
        }
        throw KonvoException.badRequest("Unsupported file type. Upload a PDF, .xlsx, .xls or .csv file.");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String stripExtension(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null ? host : url;
        } catch (RuntimeException e) {
            return url;
        }
    }

    private void dispatchIndexAfterCommit(UUID sourceId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexer.indexAsync(sourceId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                indexer.indexAsync(sourceId);
            }
        });
    }

    @Transactional
    public void delete(KonvoPrincipal principal, UUID id) {
        emailVerification.requireVerified(principal);
        KnowledgeSource source = requireOwned(principal, id);
        sources.delete(source); // chunks cascade
    }

    private KnowledgeSource requireOwned(KonvoPrincipal principal, UUID id) {
        KnowledgeSource source = sources.findById(id)
                .orElseThrow(() -> KonvoException.notFound("Source", id));
        if (!source.getTenantId().equals(principal.tenantId())) {
            throw KonvoException.notFound("Source", id);
        }
        return source;
    }

    static KnowledgeSourceResponse toResponse(KnowledgeSource s) {
        return new KnowledgeSourceResponse(
                s.getId(),
                s.getTitle(),
                s.getType(),
                s.getStatus(),
                s.getCharCount(),
                s.getChunkCount(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }

    static KnowledgeSourceDetailResponse toDetailResponse(KnowledgeSource s) {
        return new KnowledgeSourceDetailResponse(
                s.getId(),
                s.getTitle(),
                s.getType(),
                s.getStatus(),
                s.getCharCount(),
                s.getChunkCount(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getContent());
    }
}
