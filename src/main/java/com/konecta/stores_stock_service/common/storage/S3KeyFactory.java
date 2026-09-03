package com.konecta.stores_stock_service.common.storage;

import com.konecta.stores_stock_service.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class S3KeyFactory {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final String storesPrefix;
    private final String productsPrefix;

    public S3KeyFactory(@Value("${aws.s3.stores-prefix}") String storesPrefix,
            @Value("${aws.s3.products-prefix}") String productsPrefix) {
        this.storesPrefix = storesPrefix;
        this.productsPrefix = productsPrefix;
    }

    public String requireValidContentType(String contentType) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw ApiException.validation(List.of("contentType: formato não suportado (use JPEG, PNG ou WEBP)"));
        }
        return contentType;
    }

    public String productPhotoKey(UUID productId, String contentType) {
        return productsPrefix + productId + "/" + UUID.randomUUID() + EXTENSIONS.get(contentType);
    }

    public String shopLogoKey(UUID shopId, String contentType) {
        return storesPrefix + shopId + "/logo/" + UUID.randomUUID() + EXTENSIONS.get(contentType);
    }

    public String shopCoverKey(UUID shopId, String contentType) {
        return storesPrefix + shopId + "/cover/" + UUID.randomUUID() + EXTENSIONS.get(contentType);
    }

    /** Guards against a client confirming a key it wasn't issued (wrong product/shop, or forged path). */
    public void requireOwnedKey(String key, String expectedPrefix) {
        if (key == null || !key.startsWith(expectedPrefix)) {
            throw ApiException.validation(List.of("key: não corresponde a este recurso"));
        }
    }

    public String productPhotoPrefix(UUID productId) {
        return productsPrefix + productId + "/";
    }

    public String shopLogoPrefix(UUID shopId) {
        return storesPrefix + shopId + "/logo/";
    }

    public String shopCoverPrefix(UUID shopId) {
        return storesPrefix + shopId + "/cover/";
    }
}
