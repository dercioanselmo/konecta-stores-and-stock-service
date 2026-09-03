package com.konecta.stores_stock_service.store.controller;

import com.konecta.stores_stock_service.common.storage.dto.ConfirmUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadResponse;
import com.konecta.stores_stock_service.security.CurrentUser;
import com.konecta.stores_stock_service.store.dto.CreateShopRequest;
import com.konecta.stores_stock_service.store.dto.ShopCardResponse;
import com.konecta.stores_stock_service.store.dto.ShopResponse;
import com.konecta.stores_stock_service.store.dto.ShopStatusRequest;
import com.konecta.stores_stock_service.store.dto.UpdateShopRequest;
import com.konecta.stores_stock_service.store.hours.dto.HoursResponse;
import com.konecta.stores_stock_service.store.hours.dto.UpdateHoursRequest;
import com.konecta.stores_stock_service.store.hours.service.OpeningHoursService;
import com.konecta.stores_stock_service.store.service.StoreService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchant/shops")
@PreAuthorize("hasRole('MERCHANT')")
public class StoreController {

    private final StoreService storeService;
    private final OpeningHoursService openingHoursService;

    public StoreController(StoreService storeService, OpeningHoursService openingHoursService) {
        this.storeService = storeService;
        this.openingHoursService = openingHoursService;
    }

    @GetMapping
    public List<ShopCardResponse> list(Authentication authentication) {
        return storeService.listForOwner(CurrentUser.userId(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShopResponse create(Authentication authentication, @Valid @RequestBody CreateShopRequest request) {
        return storeService.create(CurrentUser.userId(authentication), request);
    }

    @GetMapping("/{shopId}")
    public ShopResponse get(Authentication authentication, @PathVariable UUID shopId) {
        return storeService.getProfile(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication));
    }

    @PatchMapping("/{shopId}")
    public ShopResponse update(Authentication authentication, @PathVariable UUID shopId,
            @RequestBody UpdateShopRequest request) {
        return storeService.update(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication), request);
    }

    @PostMapping("/{shopId}/logo/presign")
    public PresignUploadResponse presignLogo(Authentication authentication, @PathVariable UUID shopId,
            @Valid @RequestBody PresignUploadRequest request) {
        return storeService.presignLogoUpload(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication), request);
    }

    @PostMapping("/{shopId}/logo")
    public ShopResponse confirmLogo(Authentication authentication, @PathVariable UUID shopId,
            @Valid @RequestBody ConfirmUploadRequest request) {
        return storeService.confirmLogoUpload(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication), request);
    }

    @PostMapping("/{shopId}/cover/presign")
    public PresignUploadResponse presignCover(Authentication authentication, @PathVariable UUID shopId,
            @Valid @RequestBody PresignUploadRequest request) {
        return storeService.presignCoverUpload(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication), request);
    }

    @PostMapping("/{shopId}/cover")
    public ShopResponse confirmCover(Authentication authentication, @PathVariable UUID shopId,
            @Valid @RequestBody ConfirmUploadRequest request) {
        return storeService.confirmCoverUpload(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication), request);
    }

    @PatchMapping("/{shopId}/status")
    public ShopResponse updateStatus(Authentication authentication, @PathVariable UUID shopId,
            @Valid @RequestBody ShopStatusRequest request) {
        return storeService.updateStatus(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication), request);
    }

    @GetMapping("/{shopId}/hours")
    public HoursResponse getHours(Authentication authentication, @PathVariable UUID shopId) {
        storeService.getOwned(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication));
        return openingHoursService.getHours(shopId);
    }

    @PutMapping("/{shopId}/hours")
    public HoursResponse putHours(Authentication authentication, @PathVariable UUID shopId,
            @Valid @RequestBody UpdateHoursRequest request) {
        storeService.getOwned(shopId, CurrentUser.userId(authentication), CurrentUser.isAdmin(authentication));
        return openingHoursService.replaceHours(shopId, request);
    }
}
