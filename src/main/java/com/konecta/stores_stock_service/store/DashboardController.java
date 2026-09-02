package com.konecta.stores_stock_service.store;

import com.konecta.stores_stock_service.catalog.ProductRepository;
import com.konecta.stores_stock_service.catalog.ProductStatus;
import com.konecta.stores_stock_service.security.CurrentUser;
import com.konecta.stores_stock_service.store.dto.DashboardSummaryResponse;
import com.konecta.stores_stock_service.store.hours.OpeningHoursService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchant/shops/{shopId}/dashboard")
@PreAuthorize("hasRole('MERCHANT')")
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
        Store store = storeService.getOwned(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication));
        boolean open = !store.isManuallyClosed() && store.getStatus() == StoreStatus.ACTIVE
                && openingHoursService.isOpenNow(shopId);
        long productCount = productRepository.countByStoreId(shopId);
        long activeCount = productRepository.countByStoreIdAndStatus(shopId, ProductStatus.ACTIVE);
        long lowStockCount = lowStockCounter.countLowStock(shopId);
        return new DashboardSummaryResponse(open, productCount, activeCount, lowStockCount);
    }
}
