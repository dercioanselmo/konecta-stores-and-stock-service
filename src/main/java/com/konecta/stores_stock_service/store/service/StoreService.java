package com.konecta.stores_stock_service.store.service;

import com.konecta.stores_stock_service.catalog.dto.CategoryResponse;
import com.konecta.stores_stock_service.catalog.model.Category;
import com.konecta.stores_stock_service.catalog.repository.CategoryRepository;
import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.common.storage.ObjectStorageService;
import com.konecta.stores_stock_service.common.storage.PresignedUpload;
import com.konecta.stores_stock_service.common.storage.S3KeyFactory;
import com.konecta.stores_stock_service.common.storage.dto.ConfirmUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadResponse;
import com.konecta.stores_stock_service.store.dto.AdminShopSummaryResponse;
import com.konecta.stores_stock_service.store.dto.CreateShopRequest;
import com.konecta.stores_stock_service.store.dto.ShopCardResponse;
import com.konecta.stores_stock_service.store.dto.ShopResponse;
import com.konecta.stores_stock_service.store.dto.ShopStatusRequest;
import com.konecta.stores_stock_service.store.dto.UpdateShopLocationRequest;
import com.konecta.stores_stock_service.store.dto.UpdateShopRequest;
import com.konecta.stores_stock_service.store.hours.service.OpeningHoursService;
import com.konecta.stores_stock_service.store.model.Store;
import com.konecta.stores_stock_service.store.model.StoreCategory;
import com.konecta.stores_stock_service.store.model.StoreStatus;
import com.konecta.stores_stock_service.store.repository.StoreCategoryRepository;
import com.konecta.stores_stock_service.store.repository.StoreRepository;
import com.konecta.stores_stock_service.common.PageResponse;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private static final String MAPUTO_CITY = "Maputo";

    // Loose bounding box covering Maputo municipality plus its immediate
    // metro area (Matola, KaTembe) — generous on purpose since neighborhood
    // boundaries aren't validated against a fixed list either; this is a
    // sanity check against wildly wrong pins (e.g. another country/continent),
    // not a precise city-limits check.
    private static final double MAPUTO_LAT_MIN = -26.3;
    private static final double MAPUTO_LAT_MAX = -25.7;
    private static final double MAPUTO_LON_MIN = 32.3;
    private static final double MAPUTO_LON_MAX = 32.8;

    private final StoreRepository storeRepository;
    private final StoreCategoryRepository storeCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final OpeningHoursService openingHoursService;
    private final LowStockCounter lowStockCounter;
    private final ObjectStorageService objectStorageService;
    private final S3KeyFactory s3KeyFactory;

    public StoreService(StoreRepository storeRepository, StoreCategoryRepository storeCategoryRepository,
            CategoryRepository categoryRepository, OpeningHoursService openingHoursService,
            LowStockCounter lowStockCounter, ObjectStorageService objectStorageService, S3KeyFactory s3KeyFactory) {
        this.storeRepository = storeRepository;
        this.storeCategoryRepository = storeCategoryRepository;
        this.categoryRepository = categoryRepository;
        this.openingHoursService = openingHoursService;
        this.lowStockCounter = lowStockCounter;
        this.objectStorageService = objectStorageService;
        this.s3KeyFactory = s3KeyFactory;
    }

    public List<ShopCardResponse> listForOwner(String ownerUserId) {
        return storeRepository.findByOwnerUserId(ownerUserId).stream()
                .map(this::toCard)
                .toList();
    }

    /**
     * ADMIN-only: every shop on the platform, not scoped to any owner.
     * {@code ownerName}/{@code ownerEmail} are not populated here — this
     * service has no local copy of user profile data and no client wired up
     * yet to look them up from KONECTA-SECURITY-SERVICE by id; only
     * {@code ownerId} (the JWT {@code sub} the shop was created under) is
     * available today.
     */
    public PageResponse<AdminShopSummaryResponse> listForAdmin(String query, StoreStatus status, UUID categoryId,
            Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Store> spec = (root, cq, cb) -> cb.conjunction();
        if (query != null && !query.isBlank()) {
            String like = "%" + query.toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.like(cb.lower(root.get("tradeName")), like));
        }
        if (status != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null) {
            spec = spec.and((root, cq, cb) -> {
                var subquery = cq.subquery(UUID.class);
                var scRoot = subquery.from(StoreCategory.class);
                subquery.select(scRoot.get("storeId")).where(cb.equal(scRoot.get("categoryId"), categoryId));
                return root.get("id").in(subquery);
            });
        }
        return PageResponse.of(storeRepository.findAll(spec, pageable).map(this::toAdminSummary));
    }

    private AdminShopSummaryResponse toAdminSummary(Store store) {
        return new AdminShopSummaryResponse(
                store.getId(),
                store.getTradeName(),
                presignedUrlOrNull(store.getLogoKey()),
                store.getStatus(),
                isOpen(store),
                store.getOwnerUserId(),
                null,
                null,
                store.getCreatedAt());
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
        return getOwned(shopId, ownerUserId, isAdmin, false, null);
    }

    /**
     * Resolves and authorises access to a shop.
     * <ul>
     *   <li>ADMIN — any shop, no ownership check.</li>
     *   <li>MERCHANT_STAFF — shop must match the {@code claimedShopId} from their JWT.</li>
     *   <li>MERCHANT — shop must be owned by {@code ownerUserId}.</li>
     * </ul>
     */
    public Store getOwned(UUID shopId, String ownerUserId, boolean isAdmin,
            boolean isMerchantStaff, UUID claimedShopId) {
        if (isAdmin) {
            return storeRepository.findById(shopId)
                    .orElseThrow(() -> ApiException.notFound("SHOP_NOT_FOUND", "Loja não encontrada"));
        }
        if (isMerchantStaff) {
            if (claimedShopId == null || !claimedShopId.equals(shopId)) {
                throw ApiException.notFound("SHOP_NOT_FOUND", "Loja não encontrada");
            }
            return storeRepository.findById(shopId)
                    .orElseThrow(() -> ApiException.notFound("SHOP_NOT_FOUND", "Loja não encontrada"));
        }
        return storeRepository.findByIdAndOwnerUserId(shopId, ownerUserId)
                .orElseThrow(() -> ApiException.notFound("SHOP_NOT_FOUND", "Loja não encontrada"));
    }

    public ShopCardResponse getCard(UUID shopId) {
        Store store = storeRepository.findById(shopId)
                .orElseThrow(() -> ApiException.notFound("SHOP_NOT_FOUND", "Loja não encontrada"));
        return toCard(store);
    }

    public ShopResponse getProfile(UUID shopId, String ownerUserId, boolean isAdmin) {
        return toResponse(getOwned(shopId, ownerUserId, isAdmin));
    }

    public ShopResponse getProfile(UUID shopId, String ownerUserId, boolean isAdmin,
            boolean isMerchantStaff, UUID claimedShopId) {
        return toResponse(getOwned(shopId, ownerUserId, isAdmin, isMerchantStaff, claimedShopId));
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

    public PresignUploadResponse presignLogoUpload(UUID shopId, String ownerUserId, boolean isAdmin,
            PresignUploadRequest request) {
        Store store = getOwned(shopId, ownerUserId, isAdmin);
        String contentType = s3KeyFactory.requireValidContentType(request.contentType());
        String key = s3KeyFactory.shopLogoKey(store.getId(), contentType);
        PresignedUpload upload = objectStorageService.presignUpload(key, contentType);
        return new PresignUploadResponse(upload.uploadUrl(), upload.key(), upload.expiresAt());
    }

    @Transactional
    public ShopResponse confirmLogoUpload(UUID shopId, String ownerUserId, boolean isAdmin, ConfirmUploadRequest request) {
        Store store = getOwned(shopId, ownerUserId, isAdmin);
        s3KeyFactory.requireOwnedKey(request.key(), s3KeyFactory.shopLogoPrefix(store.getId()));
        requireUploaded(request.key());
        store.setLogoKey(request.key());
        return toResponse(store);
    }

    public PresignUploadResponse presignCoverUpload(UUID shopId, String ownerUserId, boolean isAdmin,
            PresignUploadRequest request) {
        Store store = getOwned(shopId, ownerUserId, isAdmin);
        String contentType = s3KeyFactory.requireValidContentType(request.contentType());
        String key = s3KeyFactory.shopCoverKey(store.getId(), contentType);
        PresignedUpload upload = objectStorageService.presignUpload(key, contentType);
        return new PresignUploadResponse(upload.uploadUrl(), upload.key(), upload.expiresAt());
    }

    @Transactional
    public ShopResponse confirmCoverUpload(UUID shopId, String ownerUserId, boolean isAdmin, ConfirmUploadRequest request) {
        Store store = getOwned(shopId, ownerUserId, isAdmin);
        s3KeyFactory.requireOwnedKey(request.key(), s3KeyFactory.shopCoverPrefix(store.getId()));
        requireUploaded(request.key());
        store.setCoverKey(request.key());
        return toResponse(store);
    }

    @Transactional
    public ShopResponse updateLocation(UUID shopId, String ownerUserId, boolean isAdmin, UpdateShopLocationRequest request) {
        Store store = getOwned(shopId, ownerUserId, isAdmin);
        double lat = request.latitude();
        double lon = request.longitude();
        if (lat < MAPUTO_LAT_MIN || lat > MAPUTO_LAT_MAX || lon < MAPUTO_LON_MIN || lon > MAPUTO_LON_MAX) {
            throw ApiException.validation(List.of(
                    "latitude/longitude: localização fora da área de Maputo suportada"));
        }
        store.setLatitude(lat);
        store.setLongitude(lon);
        return toResponse(store);
    }

    private void requireUploaded(String key) {
        if (!objectStorageService.exists(key)) {
            throw ApiException.validation(List.of("key: ficheiro não encontrado — confirme após o upload terminar"));
        }
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
        // Hibernate flushes all pending inserts before any pending deletes,
        // regardless of call order — without this explicit flush, re-adding
        // a category the store already had (same store_id/category_id) would
        // insert the new row before the old one is deleted, violating the
        // unique constraint.
        storeCategoryRepository.flush();
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
                .map(c -> new CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getSortOrder(), c.isActive(),
                        presignedUrlOrNull(c.getImageKey())))
                .toList();
    }

    private String presignedUrlOrNull(String key) {
        return key == null ? null : objectStorageService.presignDownload(key);
    }

    private ShopCardResponse toCard(Store store) {
        boolean open = isOpen(store);
        long lowStock = lowStockCounter.countLowStock(store.getId());
        return new ShopCardResponse(store.getId(), store.getTradeName(), presignedUrlOrNull(store.getLogoKey()), open, lowStock);
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
                store.getLatitude(),
                store.getLongitude(),
                categoriesOf(store.getId()),
                store.getDescription(),
                presignedUrlOrNull(store.getLogoKey()),
                presignedUrlOrNull(store.getCoverKey()),
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
