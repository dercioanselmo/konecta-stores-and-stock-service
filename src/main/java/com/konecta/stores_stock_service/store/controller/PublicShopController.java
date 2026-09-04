package com.konecta.stores_stock_service.store.controller;

import com.konecta.stores_stock_service.catalog.dto.PublicProductSummaryResponse;
import com.konecta.stores_stock_service.catalog.service.ProductService;
import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.common.PageResponse;
import com.konecta.stores_stock_service.store.dto.PublicShopDetailResponse;
import com.konecta.stores_stock_service.store.dto.PublicShopResponse;
import com.konecta.stores_stock_service.store.service.StoreService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated proximity shop browse for the anonymous
 * customer flow: category tile -> gate -> shop grid, nearest-first. See
 * API_REFERENCE_MERCHANT_DASHBOARD.md's "Proximity shop browsing" section.
 */
@RestController
@RequestMapping("/api/v1/shops")
public class PublicShopController {

    private final StoreService storeService;
    private final ProductService productService;

    public PublicShopController(StoreService storeService, ProductService productService) {
        this.storeService = storeService;
        this.productService = productService;
    }

    @GetMapping
    public PageResponse<PublicShopResponse> list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            Pageable pageable) {
        List<String> errors = new ArrayList<>();
        if (categoryId == null) {
            errors.add("categoryId: obrigatório");
        }
        if (lat == null) {
            errors.add("lat: obrigatório");
        }
        if (lng == null) {
            errors.add("lng: obrigatório");
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        return storeService.listPublicByCategory(categoryId, lat, lng, pageable);
    }

    @GetMapping("/{shopId}")
    public PublicShopDetailResponse get(@PathVariable UUID shopId) {
        return storeService.getPublicDetail(shopId);
    }

    @GetMapping("/{shopId}/products")
    public PageResponse<PublicProductSummaryResponse> products(@PathVariable UUID shopId,
            @RequestParam(required = false) UUID subcategoryId, Pageable pageable) {
        storeService.getActivePublic(shopId);
        return productService.listPublicByShop(shopId, subcategoryId, pageable);
    }
}
