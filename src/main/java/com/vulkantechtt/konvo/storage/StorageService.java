package com.vulkantechtt.konvo.storage;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {

    StoredObject put(String key, InputStream content, long contentLength, String contentType);

    InputStream get(String key);

    void delete(String key);

    String signedUrl(String key, Duration ttl);

    record StoredObject(String key, String contentType, long size, String etag) {}
}
