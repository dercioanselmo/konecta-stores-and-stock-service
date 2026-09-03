package com.konecta.stores_stock_service.store.controller;

import com.konecta.stores_stock_service.catalog.model.ProductStatus;
import com.konecta.stores_stock_service.catalog.repository.ProductRepository;
import com.konecta.stores_stock_service.security.CurrentUser;
import com.konecta.stores_stock_service.store.dto.DashboardSummaryResponse;
import com.konecta.stores_stock_service.store.hours.service.OpeningHoursService;
import com.konecta.stores_stock_service.store.model.Store;
import com.konecta.stores_stock_service.store.model.StoreStatus;
import com.konecta.stores_stock_service.store.service.LowStockCounter;
import com.konecta.stores_stock_service.store.service.StoreService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchant/shops/{shopId}/dashboard")
@PreAuthorize("hasAnyRole('MERCHANT', 'MERCHANT_STAFF')")
public class DashboardController {

    private final StoreService storeService;
    private final OpeningHoursService openingHoursService;
    private final ProductRepository productRepository;
    private final LowStockCounter lowStockCounter;

    public DashboardController(StoreService storeService, OpeningHoursService openingHoursService,
            ProductRepository productRepository, LowStockCounter lowStockCounter) {
        this.storeService = storeService;
        this.openingHoursService = openingHoursService;
        this.productRepository = productRepository;
        this.lowStockCounter = lowStockCounter;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(Authentication authentication, @PathVariable UUID shopId) {
        Store store = storeService.getOwned(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication),
                CurrentUser.isMerchantStaff(authentication), CurrentUser.shopId(authentication));
        boolean open = !store.isManuallyClosed() && store.getStatus() == StoreStatus.ACTIVE
                && openingHoursService.isOpenNow(shopId);
        long productCount = productRepository.countByStoreId(shopId);
        long activeCount = productRepository.countByStoreIdAndStatus(shopId, ProductStatus.ACTIVE);
        long lowStockCount = lowStockCounter.countLowStock(shopId);
        return new DashboardSummaryResponse(open, productCount, activeCount, lowStockCount);
    }
}
