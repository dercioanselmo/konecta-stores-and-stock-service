package com.konecta.stores_stock_service.inventory.controller;

import com.konecta.stores_stock_service.inventory.dto.StockCommitRequest;
import com.konecta.stores_stock_service.inventory.dto.StockCommitResponse;
import com.konecta.stores_stock_service.inventory.service.StockCommitService;
import com.konecta.stores_stock_service.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by KONECTA-CHECKOUT-SERVICE with the customer's own JWT (any
 * authenticated role, not merchant-scoped) to atomically decrement stock
 * for every line of a placed order. See SecurityConfig for why this one
 * path under /api/v1/shops/** needs its own authenticated() matcher ahead
 * of that prefix's permitAll rule.
 */
@RestController
@RequestMapping("/api/v1/shops/{shopId}/stock")
public class StockCommitController {

    private final StockCommitService stockCommitService;

    public StockCommitController(StockCommitService stockCommitService) {
        this.stockCommitService = stockCommitService;
    }

    @PostMapping("/commit")
    public StockCommitResponse commit(Authentication authentication, @PathVariable UUID shopId,
            @Valid @RequestBody StockCommitRequest request) {
        return stockCommitService.commit(shopId, request, CurrentUser.userId(authentication));
    }
}
