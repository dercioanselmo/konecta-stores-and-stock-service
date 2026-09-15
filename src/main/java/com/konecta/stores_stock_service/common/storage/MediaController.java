package com.konecta.stores_stock_service.common.storage;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stands in for direct-to-S3 uploads/downloads while {@code
 * konecta.storage.provider=local} — see {@link
 * LocalFilesystemObjectStorageService}'s own doc for why this exists.
 * {@code PUT} plays the role of S3's presigned {@code PUT} (the browser
 * uploads bytes straight here, no app auth — same trust model a presigned
 * URL has: possession of the URL/key is the only gate), {@code GET} plays
 * the role of a presigned {@code GET}. Publicly reachable — see
 * SecurityConfig's {@code /media/**} entry.
 */
@RestController
@RequestMapping("/media")
@ConditionalOnProperty(name = "konecta.storage.provider", havingValue = "local", matchIfMissing = true)
public class MediaController {

    private final LocalFilesystemObjectStorageService storage;

    public MediaController(LocalFilesystemObjectStorageService storage) {
        this.storage = storage;
    }

    @PutMapping("/{*key}")
    public ResponseEntity<Void> put(HttpServletRequest request) throws IOException {
        String key = extractKey(request);
        Path path = storage.resolve(key);
        Files.createDirectories(path.getParent());
        try (var in = request.getInputStream()) {
            Files.copy(in, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{*key}")
    public ResponseEntity<Resource> get(HttpServletRequest request) {
        String key = extractKey(request);
        Path path = storage.resolve(key);
        if (!Files.exists(path)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(contentTypeFor(key))
                .body(new FileSystemResource(path));
    }

    /** Strips the leading "/media/" the {@code {*key}} pattern includes verbatim. */
    private String extractKey(HttpServletRequest request) {
        String path = request.getRequestURI();
        String marker = "/media/";
        int idx = path.indexOf(marker);
        String key = idx >= 0 ? path.substring(idx + marker.length()) : path;
        if (key.isBlank()) {
            throw new IllegalArgumentException("Missing media key");
        }
        return key;
    }

    private MediaType contentTypeFor(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.valueOf("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
