package com.konecta.stores_stock_service.user.controller;

import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.common.storage.ObjectStorageService;
import com.konecta.stores_stock_service.common.storage.PresignedUpload;
import com.konecta.stores_stock_service.common.storage.S3KeyFactory;
import com.konecta.stores_stock_service.common.storage.dto.ConfirmUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadResponse;
import com.konecta.stores_stock_service.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Presigned upload for a user's own profile photo — S3-only, no persistence
 * here. This service doesn't own user profile data (KONECTA-SECURITY-SERVICE
 * does); the frontend takes the {@code url} this returns and PATCHes it onto
 * the user's profile over there. Open to any authenticated role — a profile
 * photo isn't a merchant-specific concept, unlike everything else in this
 * service.
 */
@RestController
@RequestMapping("/api/v1/users/me/photo")
public class UserPhotoController {

    private final ObjectStorageService objectStorageService;
    private final S3KeyFactory s3KeyFactory;

    public UserPhotoController(ObjectStorageService objectStorageService, S3KeyFactory s3KeyFactory) {
        this.objectStorageService = objectStorageService;
        this.s3KeyFactory = s3KeyFactory;
    }

    @PostMapping("/presign")
    public PresignUploadResponse presign(Authentication authentication, @Valid @RequestBody PresignUploadRequest request) {
        String userId = CurrentUser.userId(authentication);
        String contentType = s3KeyFactory.requireValidContentType(request.contentType());
        String key = s3KeyFactory.userPhotoKey(userId, contentType);
        PresignedUpload upload = objectStorageService.presignUpload(key, contentType);
        return new PresignUploadResponse(upload.uploadUrl(), upload.key(), upload.expiresAt());
    }

    @PostMapping
    public UserPhotoResponse confirm(Authentication authentication, @Valid @RequestBody ConfirmUploadRequest request) {
        String userId = CurrentUser.userId(authentication);
        s3KeyFactory.requireOwnedKey(request.key(), s3KeyFactory.userPhotoPrefix(userId));
        if (!objectStorageService.exists(request.key())) {
            throw ApiException.validation(List.of("key: ficheiro não encontrado — confirme após o upload terminar"));
        }
        return new UserPhotoResponse(objectStorageService.presignDownload(request.key()));
    }

    public record UserPhotoResponse(String url) {
    }
}
