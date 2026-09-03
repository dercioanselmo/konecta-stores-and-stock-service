package com.konecta.stores_stock_service.support;

import com.konecta.stores_stock_service.common.storage.ObjectStorageService;
import com.konecta.stores_stock_service.common.storage.PresignedUpload;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * No real AWS calls in tests: a presigned upload immediately "exists" (there's
 * no real browser here to do the PUT), letting the confirm step's existence
 * check pass so the rest of the flow (ownership, DB writes, response shape)
 * is exercised the same way it would be in production.
 */
public class FakeObjectStorageService implements ObjectStorageService {

    private final Set<String> uploadedKeys = ConcurrentHashMap.newKeySet();

    @Override
    public PresignedUpload presignUpload(String key, String contentType) {
        uploadedKeys.add(key);
        return new PresignedUpload("https://fake-s3.test/" + key, key, Instant.now().plusSeconds(300));
    }

    @Override
    public boolean exists(String key) {
        return uploadedKeys.contains(key);
    }

    @Override
    public void delete(String key) {
        uploadedKeys.remove(key);
    }

    @Override
    public String presignDownload(String key) {
        return "https://fake-s3.test/get/" + key;
    }
}
