package com.vulkantechtt.konvo.storage;

import com.vulkantechtt.konvo.common.KonvoException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Local-filesystem stub for dev. Activates when konvo.storage.provider=local.
 * Replace with R2StorageService in production by switching the property.
 */
@Service
@ConditionalOnProperty(prefix = "konvo.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(@Value("${konvo.storage.local.root:./var/storage}") String rootPath) {
        this.root = Paths.get(rootPath).toAbsolutePath();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create local storage root: " + root, e);
        }
    }

    @Override
    public StoredObject put(String key, InputStream content, long contentLength, String contentType) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw KonvoException.badRequest("Invalid storage key");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            long size = Files.size(target);
            return new StoredObject(key, contentType, size, Long.toHexString(size));
        } catch (IOException e) {
            throw new IllegalStateException("Storage write failed for " + key, e);
        }
    }

    @Override
    public InputStream get(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw KonvoException.notFound("storage_object", key);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new IllegalStateException("Storage read failed for " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("Storage delete failed for " + key, e);
        }
    }

    @Override
    public String signedUrl(String key, Duration ttl) {
        // Local storage has no real signed URL; return a stable dev path.
        return "/storage/" + key;
    }
}
