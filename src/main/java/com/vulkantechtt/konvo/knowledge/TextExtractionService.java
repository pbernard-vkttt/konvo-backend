package com.vulkantechtt.konvo.knowledge;

import com.vulkantechtt.konvo.common.KonvoException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Extracts plain text from uploaded files (PDF, Excel) and scraped web pages
 * using Apache Tika. The standard parser package auto-detects the format, so
 * one {@link Tika#parseToString} call covers every type the knowledge base
 * accepts. Extracted text feeds the same chunk + embed pipeline as pasted text
 * via {@link KnowledgeIndexer}.
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    /** Hard cap on extracted text — keeps a giant PDF from blowing up indexing. */
    private static final int MAX_CONTENT_CHARS = 200_000;
    /** Reject web pages whose declared body exceeds this (Tika still truncates output). */
    private static final long MAX_FETCH_BYTES = 10L * 1024 * 1024;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);

    private final Tika tika;
    private final HttpClient httpClient;

    public TextExtractionService() {
        this.tika = new Tika();
        this.tika.setMaxStringLength(MAX_CONTENT_CHARS);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    /**
     * Extract text from an uploaded file's bytes. {@code filename} and
     * {@code contentType} are hints that help Tika pick the right parser.
     */
    public String extractFromFile(byte[] data, String filename, String contentType) {
        if (data == null || data.length == 0) {
            throw KonvoException.badRequest("The uploaded file is empty");
        }
        Metadata metadata = new Metadata();
        if (filename != null && !filename.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        if (contentType != null && !contentType.isBlank()) {
            metadata.set(HttpHeaders.CONTENT_TYPE, contentType);
        }
        try (InputStream in = new ByteArrayInputStream(data)) {
            return requireText(tika.parseToString(in, metadata));
        } catch (IOException | TikaException e) {
            log.warn("Tika failed to parse upload {} ({}): {}", filename, contentType, e.toString());
            throw KonvoException.badRequest(
                    "Could not read text from this file. Is it a valid, text-based PDF or spreadsheet?");
        }
    }

    /** Fetch a URL and extract its readable text (HTML stripped to body text). */
    public String extractFromUrl(String url) {
        byte[] body = fetch(url);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_LOCATION, url);
        try (InputStream in = new ByteArrayInputStream(body)) {
            return requireText(tika.parseToString(in, metadata));
        } catch (IOException | TikaException e) {
            log.warn("Tika failed to parse page {}: {}", url, e.toString());
            throw KonvoException.badRequest("Could not read any text from that page");
        }
    }

    private byte[] fetch(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw KonvoException.badRequest("That doesn't look like a valid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            throw KonvoException.badRequest("Only http and https URLs can be scraped");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("User-Agent", "KonvoKnowledgeBot/1.0 (+https://konvelo.com)")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw KonvoException.badRequest("The page returned HTTP " + response.statusCode());
            }
            byte[] body = response.body();
            if (body.length > MAX_FETCH_BYTES) {
                throw KonvoException.badRequest("That page is too large to import (over 10 MB)");
            }
            return body;
        } catch (IOException e) {
            log.warn("Failed to fetch {} for knowledge import: {}", url, e.toString());
            throw KonvoException.badRequest("Could not reach that URL");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw KonvoException.badRequest("Fetching that URL timed out");
        }
    }

    private static String requireText(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            throw KonvoException.badRequest(
                    "No readable text was found. Scanned/image-only files aren't supported yet.");
        }
        return trimmed;
    }
}
