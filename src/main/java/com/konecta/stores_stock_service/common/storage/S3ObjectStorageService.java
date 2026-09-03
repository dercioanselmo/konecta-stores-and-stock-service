package com.konecta.stores_stock_service.common.storage;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class S3ObjectStorageService implements ObjectStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration putTtl;
    private final Duration getTtl;

    public S3ObjectStorageService(S3Client s3Client, S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.presign-put-ttl-seconds}") long putTtlSeconds,
            @Value("${aws.s3.presign-get-ttl-seconds}") long getTtlSeconds) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.putTtl = Duration.ofSeconds(putTtlSeconds);
        this.getTtl = Duration.ofSeconds(getTtlSeconds);
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(putTtl)
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return new PresignedUpload(presigned.url().toString(), key, Instant.now().plus(putTtl));
    }

    @Override
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public String presignDownload(String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(getTtl)
                .getObjectRequest(builder -> builder.bucket(bucket).key(key))
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}
