package com.konecta.stores_stock_service.catalog.controller;

import com.konecta.stores_stock_service.catalog.dto.CreateProductRequest;
import com.konecta.stores_stock_service.catalog.dto.ProductPhotoResponse;
import com.konecta.stores_stock_service.catalog.dto.ProductResponse;
import com.konecta.stores_stock_service.catalog.dto.StockAdjustRequest;
import com.konecta.stores_stock_service.catalog.dto.UpdateProductRequest;
import com.konecta.stores_stock_service.catalog.service.ProductService;
import com.konecta.stores_stock_service.common.PageResponse;
import com.konecta.stores_stock_service.common.storage.dto.ConfirmUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadResponse;
import com.konecta.stores_stock_service.security.CurrentUser;
import com.konecta.stores_stock_service.store.service.StoreService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every method here — read and write — is open to both MERCHANT and
 * MERCHANT_STAFF: a staff member gets full CRUD on products for the one
 * shop they're assigned to (enforced by {@link #assertOwned}, which scopes
 * MERCHANT_STAFF to their JWT's {@code shopId} claim), matching the
 * product-level access the frontend asked for. Shop-level settings
 * (profile, hours, logo/cover, status) stay MERCHANT-only — see
 * {@link com.konecta.stores_stock_service.store.controller.StoreController}.
 */
@RestController
@RequestMapping("/api/v1/merchant/shops/{shopId}/products")
@PreAuthorize("hasAnyRole('MERCHANT', 'MERCHANT_STAFF')")
public class ProductController {

    private final ProductService productService;
    private final StoreService storeService;

    public ProductController(ProductService productService, StoreService storeService) {
        this.productService = productService;
        this.storeService = storeService;
    }

    @GetMapping
    public PageResponse<ProductResponse> list(Authentication authentication, @PathVariable UUID shopId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID subcategoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean lowStock,
            Pageable pageable) {
        assertOwned(authentication, shopId);
        return productService.list(shopId, query, categoryId, subcategoryId, active, lowStock, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(Authentication authentication, @PathVariable UUID shopId,
            @Valid @RequestBody CreateProductRequest request) {
        assertOwned(authentication, shopId);
        return productService.create(shopId, request);
    }

    @GetMapping("/{productId}")
    public ProductResponse get(Authentication authentication, @PathVariable UUID shopId, @PathVariable UUID productId) {
        assertOwned(authentication, shopId);
        return productService.get(shopId, productId);
    }

    @PatchMapping("/{productId}")
    public ProductResponse update(Authentication authentication, @PathVariable UUID shopId,
            @PathVariable UUID productId, @RequestBody UpdateProductRequest request) {
        assertOwned(authentication, shopId);
        return productService.update(shopId, productId, request);
    }

    @PatchMapping("/{productId}/active")
    public ProductResponse setActive(Authentication authentication, @PathVariable UUID shopId,
            @PathVariable UUID productId, @RequestParam boolean active) {
        assertOwned(authentication, shopId);
        return productService.setActive(shopId, productId, active);
    }

    @PatchMapping("/{productId}/stock")
    public ProductResponse adjustStock(Authentication authentication, @PathVariable UUID shopId,
            @PathVariable UUID productId, @Valid @RequestBody StockAdjustRequest request) {
        assertOwned(authentication, shopId);
        return productService.adjustStock(shopId, productId, request, CurrentUser.userId(authentication));
    }

    @PostMapping("/{productId}/photos/presign")
    public PresignUploadResponse presignPhoto(Authentication authentication, @PathVariable UUID shopId,
            @PathVariable UUID productId, @Valid @RequestBody PresignUploadRequest request) {
        assertOwned(authentication, shopId);
        return productService.presignPhotoUpload(shopId, productId, request);
    }

    @PostMapping("/{productId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductPhotoResponse confirmPhoto(Authentication authentication, @PathVariable UUID shopId,
            @PathVariable UUID productId, @Valid @RequestBody ConfirmUploadRequest request) {
        assertOwned(authentication, shopId);
        return productService.confirmPhotoUpload(shopId, productId, request);
    }

    @DeleteMapping("/{productId}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(Authentication authentication, @PathVariable UUID shopId,
            @PathVariable UUID productId, @PathVariable UUID photoId) {
        assertOwned(authentication, shopId);
        productService.deletePhoto(shopId, productId, photoId);
    }

    @PatchMapping("/{productId}/photos/{photoId}/primary")
    public ProductPhotoResponse setPrimaryPhoto(Authentication authentication, @PathVariable UUID shopId,
            @PathVariable UUID productId, @PathVariable UUID photoId) {
        assertOwned(authentication, shopId);
        return productService.setPrimaryPhoto(shopId, productId, photoId);
    }

    private void assertOwned(Authentication authentication, UUID shopId) {
        storeService.getOwned(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication),
                CurrentUser.isMerchantStaff(authentication), CurrentUser.shopId(authentication));
    }
}
