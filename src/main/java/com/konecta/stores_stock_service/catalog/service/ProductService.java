package com.konecta.stores_stock_service.catalog.service;

import com.konecta.stores_stock_service.catalog.dto.CreateProductRequest;
import com.konecta.stores_stock_service.catalog.dto.ProductPhotoResponse;
import com.konecta.stores_stock_service.catalog.dto.ProductResponse;
import com.konecta.stores_stock_service.catalog.dto.PublicProductSummaryResponse;
import com.konecta.stores_stock_service.catalog.dto.StockAdjustRequest;
import com.konecta.stores_stock_service.catalog.dto.UpdateProductRequest;
import com.konecta.stores_stock_service.catalog.model.Category;
import com.konecta.stores_stock_service.catalog.model.Product;
import com.konecta.stores_stock_service.catalog.model.ProductImage;
import com.konecta.stores_stock_service.catalog.model.ProductStatus;
import com.konecta.stores_stock_service.catalog.model.Subcategory;
import com.konecta.stores_stock_service.catalog.repository.CategoryRepository;
import com.konecta.stores_stock_service.catalog.repository.ProductImageRepository;
import com.konecta.stores_stock_service.catalog.repository.ProductRepository;
import com.konecta.stores_stock_service.catalog.repository.SubcategoryRepository;
import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.common.PageResponse;
import com.konecta.stores_stock_service.common.storage.ObjectStorageService;
import com.konecta.stores_stock_service.common.storage.PresignedUpload;
import com.konecta.stores_stock_service.common.storage.S3KeyFactory;
import com.konecta.stores_stock_service.common.storage.dto.ConfirmUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadResponse;
import com.konecta.stores_stock_service.inventory.model.Inventory;
import com.konecta.stores_stock_service.inventory.service.InventoryService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryService inventoryService;
    private final SubcategoryRepository subcategoryRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectStorageService objectStorageService;
    private final S3KeyFactory s3KeyFactory;

    public ProductService(ProductRepository productRepository, ProductImageRepository productImageRepository,
            InventoryService inventoryService, SubcategoryRepository subcategoryRepository,
            CategoryRepository categoryRepository, ObjectStorageService objectStorageService, S3KeyFactory s3KeyFactory) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.inventoryService = inventoryService;
        this.subcategoryRepository = subcategoryRepository;
        this.categoryRepository = categoryRepository;
        this.objectStorageService = objectStorageService;
        this.s3KeyFactory = s3KeyFactory;
    }

    public PageResponse<ProductResponse> list(UUID shopId, String query, UUID categoryId, UUID subcategoryId,
            Boolean active, Boolean lowStock, Pageable pageable) {
        Specification<Product> spec = (root, cq, cb) -> cb.equal(root.get("storeId"), shopId);
        if (query != null && !query.isBlank()) {
            String like = "%" + query.toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.like(cb.lower(root.get("name")), like));
        }
        if (subcategoryId != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("subcategoryId"), subcategoryId));
        } else if (categoryId != null) {
            List<UUID> subcategoryIds = subcategoryRepository.findByCategoryIdOrderBySortOrderAsc(categoryId).stream()
                    .map(Subcategory::getId)
                    .toList();
            spec = spec.and((root, cq, cb) -> subcategoryIds.isEmpty()
                    ? cb.disjunction()
                    : root.get("subcategoryId").in(subcategoryIds));
        }
        if (active != null) {
            spec = active
                    ? spec.and((root, cq, cb) -> root.get("status").in(ProductStatus.ACTIVE, ProductStatus.OUT_OF_STOCK))
                    : spec.and((root, cq, cb) -> cb.equal(root.get("status"), ProductStatus.INACTIVE));
        }

        if (Boolean.TRUE.equals(lowStock)) {
            List<Product> all = productRepository.findAll(spec, Pageable.unpaged()).stream()
                    .filter(p -> inventoryService.isLowStock(p.getId()))
                    .sorted(Comparator.comparing(Product::getCreatedAt).reversed())
                    .toList();
            int from = Math.min((int) pageable.getOffset(), all.size());
            int to = Math.min(from + pageable.getPageSize(), all.size());
            List<ProductResponse> content = new ArrayList<>();
            for (Product p : all.subList(from, to)) {
                content.add(toResponse(p));
            }
            return PageResponse.of(new PageImpl<>(content, pageable, all.size()));
        }

        return PageResponse.of(productRepository.findAll(spec, pageable).map(this::toResponse));
    }

    /**
     * Public, unauthenticated browse of one shop's active products —
     * deliberately minimal (id, name, primary photo only, no price/stock),
     * matching the store→subcategory→product browsing ask. Caller is
     * responsible for confirming the shop itself is public/active first
     * (see {@code StoreService.getActivePublic}) — this method trusts the
     * {@code shopId} it's given.
     */
    public PageResponse<PublicProductSummaryResponse> listPublicByShop(UUID shopId, UUID subcategoryId,
            Pageable pageable) {
        Specification<Product> spec = (root, cq, cb) -> cb.and(
                cb.equal(root.get("storeId"), shopId),
                root.get("status").in(ProductStatus.ACTIVE, ProductStatus.OUT_OF_STOCK));
        if (subcategoryId != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("subcategoryId"), subcategoryId));
        }
        return PageResponse.of(productRepository.findAll(spec, pageable).map(this::toPublicSummary));
    }

    private PublicProductSummaryResponse toPublicSummary(Product product) {
        String photoUrl = productImageRepository.findByProductIdOrderBySortOrderAsc(product.getId()).stream()
                .filter(ProductImage::isPrimary)
                .findFirst()
                .map(img -> objectStorageService.presignDownload(img.getObjectKey()))
                .orElse(null);
        boolean inStock = inventoryService.getByProductId(product.getId()).getQuantityAvailable() > 0;
        return new PublicProductSummaryResponse(product.getId(), product.getName(), photoUrl, product.getPrice(), inStock);
    }

    @Transactional
    public ProductResponse create(UUID shopId, CreateProductRequest request) {
        Product product = new Product();
        product.setStoreId(shopId);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setSubcategoryId(requireValidSubcategory(request.subcategoryId()));
        product.setPrice(request.price());
        product.setStatus(request.active() == null || request.active() ? ProductStatus.ACTIVE : ProductStatus.INACTIVE);
        product = productRepository.save(product);

        int threshold = request.lowStockThreshold() != null ? request.lowStockThreshold() : DEFAULT_LOW_STOCK_THRESHOLD;
        inventoryService.createForProduct(product.getId(), request.stockQuantity(), threshold);

        return toResponse(product);
    }

    public ProductResponse get(UUID shopId, UUID productId) {
        return toResponse(getOwned(shopId, productId));
    }

    @Transactional
    public ProductResponse update(UUID shopId, UUID productId, UpdateProductRequest request) {
        Product product = getOwned(shopId, productId);
        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.subcategoryId() != null) {
            product.setSubcategoryId(requireValidSubcategory(request.subcategoryId()));
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.lowStockThreshold() != null) {
            inventoryService.setThreshold(product.getId(), request.lowStockThreshold());
        }
        if (request.active() != null) {
            product.setStatus(request.active() ? ProductStatus.ACTIVE : ProductStatus.INACTIVE);
        }
        return toResponse(product);
    }

    @Transactional
    public ProductResponse setActive(UUID shopId, UUID productId, boolean active) {
        Product product = getOwned(shopId, productId);
        product.setStatus(active ? ProductStatus.ACTIVE : ProductStatus.INACTIVE);
        return toResponse(product);
    }

    @Transactional
    public ProductResponse adjustStock(UUID shopId, UUID productId, StockAdjustRequest request, String actorUserId) {
        Product product = getOwned(shopId, productId);
        inventoryService.setAbsoluteQuantity(product.getId(), request.quantity(), actorUserId);
        return toResponse(product);
    }

    public PresignUploadResponse presignPhotoUpload(UUID shopId, UUID productId, PresignUploadRequest request) {
        Product product = getOwned(shopId, productId);
        String contentType = s3KeyFactory.requireValidContentType(request.contentType());
        String key = s3KeyFactory.productPhotoKey(product.getId(), contentType);
        PresignedUpload upload = objectStorageService.presignUpload(key, contentType);
        return new PresignUploadResponse(upload.uploadUrl(), upload.key(), upload.expiresAt());
    }

    @Transactional
    public ProductPhotoResponse confirmPhotoUpload(UUID shopId, UUID productId, ConfirmUploadRequest request) {
        Product product = getOwned(shopId, productId);
        s3KeyFactory.requireOwnedKey(request.key(), s3KeyFactory.productPhotoPrefix(product.getId()));
        if (!objectStorageService.exists(request.key())) {
            throw ApiException.validation(List.of("key: ficheiro não encontrado — confirme após o upload terminar"));
        }

        long existingCount = productImageRepository.countByProductId(product.getId());
        ProductImage image = new ProductImage(product.getId(), request.key(), existingCount == 0, (int) existingCount);
        image = productImageRepository.save(image);
        return toPhotoResponse(image);
    }

    @Transactional
    public void deletePhoto(UUID shopId, UUID productId, UUID photoId) {
        Product product = getOwned(shopId, productId);
        ProductImage image = getOwnedPhoto(product.getId(), photoId);
        boolean wasPrimary = image.isPrimary();
        productImageRepository.delete(image);
        objectStorageService.delete(image.getObjectKey());

        if (wasPrimary) {
            productImageRepository.findByProductIdOrderBySortOrderAsc(product.getId()).stream()
                    .findFirst()
                    .ifPresent(next -> next.setPrimary(true));
        }
    }

    @Transactional
    public ProductPhotoResponse setPrimaryPhoto(UUID shopId, UUID productId, UUID photoId) {
        Product product = getOwned(shopId, productId);
        ProductImage target = getOwnedPhoto(product.getId(), photoId);
        productImageRepository.findByProductIdOrderBySortOrderAsc(product.getId())
                .forEach(image -> image.setPrimary(image.getId().equals(photoId)));
        return toPhotoResponse(target);
    }

    private ProductImage getOwnedPhoto(UUID productId, UUID photoId) {
        ProductImage image = productImageRepository.findById(photoId)
                .orElseThrow(() -> ApiException.notFound("PHOTO_NOT_FOUND", "Foto não encontrada"));
        if (!image.getProductId().equals(productId)) {
            throw ApiException.notFound("PHOTO_NOT_FOUND", "Foto não encontrada");
        }
        return image;
    }

    private UUID requireValidSubcategory(UUID subcategoryId) {
        if (subcategoryId == null) {
            return null;
        }
        if (!subcategoryRepository.existsById(subcategoryId)) {
            throw ApiException.validation(List.of("subcategoryId: subcategoria desconhecida"));
        }
        return subcategoryId;
    }

    private Product getOwned(UUID shopId, UUID productId) {
        return productRepository.findByIdAndStoreId(productId, shopId)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Produto não encontrado"));
    }

    private ProductPhotoResponse toPhotoResponse(ProductImage image) {
        return new ProductPhotoResponse(image.getId(), objectStorageService.presignDownload(image.getObjectKey()),
                image.isPrimary());
    }

    private ProductResponse toResponse(Product product) {
        Inventory inventory = inventoryService.getByProductId(product.getId());

        String subcategoryName = null;
        UUID categoryId = null;
        String categoryName = null;
        if (product.getSubcategoryId() != null) {
            Optional<Subcategory> subcategory = subcategoryRepository.findById(product.getSubcategoryId());
            if (subcategory.isPresent()) {
                subcategoryName = subcategory.get().getName();
                Optional<Category> category = categoryRepository.findById(subcategory.get().getCategoryId());
                if (category.isPresent()) {
                    categoryId = category.get().getId();
                    categoryName = category.get().getName();
                }
            }
        }

        List<ProductPhotoResponse> photos = productImageRepository.findByProductIdOrderBySortOrderAsc(product.getId())
                .stream()
                .map(this::toPhotoResponse)
                .toList();

        return new ProductResponse(
                product.getId(),
                product.getStoreId(),
                product.getName(),
                product.getDescription(),
                product.getSubcategoryId(),
                subcategoryName,
                categoryId,
                categoryName,
                product.getPrice(),
                inventory.getQuantityAvailable(),
                inventory.getLowStockThreshold(),
                product.isActive(),
                inventory.isLowStock(),
                photos,
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
