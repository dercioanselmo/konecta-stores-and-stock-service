package com.konecta.stores_stock_service.common.storage;

/**
 * Product photos and shop logo/cover live in a private S3 bucket — the
 * browser talks to S3 directly for both writes and reads, this service
 * only hands out short-lived presigned URLs. The backend never sees the
 * file bytes.
 */
public interface ObjectStorageService {

    /**
     * A presigned {@code PUT} URL the client uploads the file to directly.
     * Valid for a short window (see {@code aws.s3.presign-put-ttl-seconds}).
     */
    PresignedUpload presignUpload(String key, String contentType);

    /** True if an object exists at this key — used to confirm an upload actually landed. */
    boolean exists(String key);

    void delete(String key);

    /**
     * A presigned {@code GET} URL, valid for {@code aws.s3.presign-get-ttl-seconds}.
     * Regenerate on every read — never persist this value, only the key.
     */
    String presignDownload(String key);
}
