package com.konecta.stores_stock_service.store.controller;

import com.konecta.stores_stock_service.common.PageResponse;
import com.konecta.stores_stock_service.store.dto.AdminShopSummaryResponse;
import com.konecta.stores_stock_service.store.model.StoreStatus;
import com.konecta.stores_stock_service.store.service.StoreService;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-only view over every shop on the platform (not scoped to any
 * owner). Individual shop management (profile, products, hours,
 * logo/cover, status) is not duplicated here — ADMIN uses the same
 * {@code /api/v1/merchant/shops/{shopId}/**} endpoints as a MERCHANT owner,
 * now widened to also accept ROLE_ADMIN (ownership check bypassed).
 */
@RestController
@RequestMapping("/api/v1/admin/shops")
@PreAuthorize("hasRole('ADMIN')")
public class AdminShopController {

    private final StoreService storeService;

    public AdminShopController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public PageResponse<AdminShopSummaryResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) StoreStatus status,
            Pageable pageable) {
        return storeService.listForAdmin(query, status, pageable);
    }
}
