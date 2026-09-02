package com.konecta.stores_stock_service.store.service;

import com.konecta.stores_stock_service.catalog.dto.CategoryResponse;
import com.konecta.stores_stock_service.catalog.model.Category;
import com.konecta.stores_stock_service.catalog.repository.CategoryRepository;
import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.store.dto.CreateShopRequest;
import com.konecta.stores_stock_service.store.dto.ShopCardResponse;
import com.konecta.stores_stock_service.store.dto.ShopResponse;
import com.konecta.stores_stock_service.store.dto.ShopStatusRequest;
import com.konecta.stores_stock_service.store.dto.UpdateShopRequest;
import com.konecta.stores_stock_service.store.hours.service.OpeningHoursService;
import com.konecta.stores_stock_service.store.model.Store;
import com.konecta.stores_stock_service.store.model.StoreCategory;
import com.konecta.stores_stock_service.store.model.StoreStatus;
import com.konecta.stores_stock_service.store.repository.StoreCategoryRepository;
import com.konecta.stores_stock_service.store.repository.StoreRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private static final String MAPUTO_CITY = "Maputo";

    private final StoreRepository storeRepository;
    private final StoreCategoryRepository storeCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final OpeningHoursService openingHoursService;
    private final LowStockCounter lowStockCounter;

    public StoreService(StoreRepository storeRepository, StoreCategoryRepository storeCategoryRepository,
            CategoryRepository categoryRepository, OpeningHoursService openingHoursService,
            LowStockCounter lowStockCounter) {
        this.storeRepository = storeRepository;
        this.storeCategoryRepository = storeCategoryRepository;
        this.categoryRepository = categoryRepository;
        this.openingHoursService = openingHoursService;
        this.lowStockCounter = lowStockCounter;
    }

    public List<ShopCardResponse> listForOwner(String ownerUserId) {
        return storeRepository.findByOwnerUserId(ownerUserId).stream()
                .map(this::toCard)
                .toList();
    }

    @Transactional
    public ShopResponse create(String ownerUserId, CreateShopRequest request) {
        if (!MAPUTO_CITY.equalsIgnoreCase(request.city())) {
            throw ApiException.validation(List.of("city: apenas \"Maputo\" é suportada nesta fase"));
        }
        Store store = new Store();
        store.setOwnerUserId(ownerUserId);
        store.setTradeName(request.name());
        store.setNuit(request.nuit());
        store.setAddressLine(request.address());
        store.setCity(request.city());
        store.setNeighborhood(request.neighborhood());
        store.setPhone(request.phone());
        store.setDescription(request.description());
        store.setStatus(store.meetsActivationRequirements() ? StoreStatus.ACTIVE : StoreStatus.DRAFT);
        store = storeRepository.save(store);
        replaceCategories(store.getId(), request.categoryIds());
        return toResponse(store);
    }

    public Store getOwned(UUID shopId, String ownerUserId, boolean isAdmin) {
        if (isAdmin) {
            return storeRepository.findById(shopId)
                    .orElseThrow(() -> ApiException.notFound("SHOP_NOT_FOUND", "Loja não encontrada"));
        }
        return storeRepository.findByIdAndOwnerUserId(shopId, ownerUserId)
                .orElseThrow(() -> ApiException.notFound("SHOP_NOT_FOUND", "Loja não encontrada"));
    }

    public ShopResponse getProfile(UUID shopId, String ownerUserId, boolean isAdmin) {
        return toResponse(getOwned(shopId, ownerUserId, isAdmin));
    }

    @Transactional
    public ShopResponse update(UUID shopId, String ownerUserId, boolean isAdmin, UpdateShopRequest request) {
        Store store = getOwned(shopId, ownerUserId, isAdmin);
        if (request.name() != null) {
            store.setTradeName(request.name());
        }
        if (request.nuit() != null) {
            store.setNuit(request.nuit());
        }
        if (request.address() != null) {
            store.setAddressLine(request.address());
        }
        if (request.city() != null) {
            if (!MAPUTO_CITY.equalsIgnoreCase(request.city())) {
                throw ApiException.validation(List.of("city: apenas \"Maputo\" é suportada nesta fase"));
            }
            store.setCity(request.city());
        }
        if (request.neighborhood() != null) {
            store.setNeighborhood(request.neighborhood());
        }
        if (request.phone() != null) {
            store.setPhone(request.phone());
        }
        if (request.categoryIds() != null) {
            replaceCategories(store.getId(), request.categoryIds());
        }
        if (request.description() != null) {
            store.setDescription(request.description());
        }
        if (request.logoUrl() != null) {
            store.setLogoUrl(request.logoUrl());
        }
        if (request.coverUrl() != null) {
            store.setCoverUrl(request.coverUrl());
        }
        if (request.acceptsPickup() != null) {
            store.setAcceptsPickup(request.acceptsPickup());
        }
        if (request.acceptsDelivery() != null) {
            store.setAcceptsDelivery(request.acceptsDelivery());
        }
        if (store.getStatus() == StoreStatus.DRAFT && store.meetsActivationRequirements()) {
            store.setStatus(StoreStatus.ACTIVE);
        }
        return toResponse(store);
    }

    @Transactional
    public ShopResponse updateStatus(UUID shopId, String ownerUserId, boolean isAdmin, ShopStatusRequest request) {
        Store store = getOwned(shopId, ownerUserId, isAdmin);
        store.setManuallyClosed(Boolean.TRUE.equals(request.manuallyClosed()));
        store.setManuallyClosedReason(request.reason());
        return toResponse(store);
    }

    private void replaceCategories(UUID storeId, List<UUID> categoryIds) {
        storeCategoryRepository.deleteByStoreId(storeId);
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        List<UUID> distinctIds = categoryIds.stream().distinct().toList();
        List<String> unknown = distinctIds.stream()
                .filter(id -> !categoryRepository.existsById(id))
                .map(UUID::toString)
                .toList();
        if (!unknown.isEmpty()) {
            throw ApiException.validation(List.of("categoryIds: categoria(s) desconhecida(s): " + String.join(", ", unknown)));
        }
        storeCategoryRepository.saveAll(distinctIds.stream().map(id -> new StoreCategory(storeId, id)).toList());
    }

    private List<CategoryResponse> categoriesOf(UUID storeId) {
        List<UUID> categoryIds = storeCategoryRepository.findByStoreId(storeId).stream()
                .map(StoreCategory::getCategoryId)
                .toList();
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return categoryRepository.findAllById(categoryIds).stream()
                .sorted(Comparator.comparing(Category::getSortOrder))
                .map(c -> new CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getSortOrder(), c.isActive()))
                .toList();
    }

    private ShopCardResponse toCard(Store store) {
        boolean open = isOpen(store);
        long lowStock = lowStockCounter.countLowStock(store.getId());
        return new ShopCardResponse(store.getId(), store.getTradeName(), store.getLogoUrl(), open, lowStock);
    }

    private ShopResponse toResponse(Store store) {
        return new ShopResponse(
                store.getId(),
                store.getTradeName(),
                store.getLegalName(),
                store.getNuit(),
                store.getEmail(),
                store.getPhone(),
                store.getAddressLine(),
                store.getCity(),
                store.getNeighborhood(),
                categoriesOf(store.getId()),
                store.getDescription(),
                store.getLogoUrl(),
                store.getCoverUrl(),
                store.getStatus(),
                isOpen(store),
                store.isManuallyClosed(),
                store.meetsActivationRequirements(),
                store.isAcceptsPickup(),
                store.isAcceptsDelivery(),
                store.getCreatedAt(),
                store.getUpdatedAt());
    }

    private boolean isOpen(Store store) {
        if (store.isManuallyClosed()) {
            return false;
        }
        if (store.getStatus() != StoreStatus.ACTIVE) {
            return false;
        }
        return openingHoursService.isOpenNow(store.getId());
    }
}
