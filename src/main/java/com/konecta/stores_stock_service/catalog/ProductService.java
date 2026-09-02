package com.konecta.stores_stock_service.catalog;

import com.konecta.stores_stock_service.catalog.dto.CreateProductRequest;
import com.konecta.stores_stock_service.catalog.dto.ProductResponse;
import com.konecta.stores_stock_service.catalog.dto.StockAdjustRequest;
import com.konecta.stores_stock_service.catalog.dto.UpdateProductRequest;
import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.common.PageResponse;
import com.konecta.stores_stock_service.inventory.Inventory;
import com.konecta.stores_stock_service.inventory.InventoryService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private final InventoryService inventoryService;

    public ProductService(ProductRepository productRepository, InventoryService inventoryService) {
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
    }

    public PageResponse<ProductResponse> list(UUID shopId, String query, String category, Boolean active,
            Boolean lowStock, Pageable pageable) {
        Specification<Product> spec = (root, cq, cb) -> cb.equal(root.get("storeId"), shopId);
        if (query != null && !query.isBlank()) {
            String like = "%" + query.toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.like(cb.lower(root.get("name")), like));
        }
        if (category != null && !category.isBlank()) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("categoryCode"), category));
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

    @Transactional
    public ProductResponse create(UUID shopId, CreateProductRequest request) {
        Product product = new Product();
        product.setStoreId(shopId);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategoryCode(request.category());
        product.setPrice(request.price());
        product.setStatus(request.active() == null || request.active() ? ProductStatus.ACTIVE : ProductStatus.INACTIVE);
        if (request.imageUrls() != null) {
            product.setImageUrls(request.imageUrls());
        }
        product.setPrimaryImageUrl(request.primaryImageUrl());
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
        if (request.category() != null) {
            product.setCategoryCode(request.category());
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
        if (request.imageUrls() != null) {
            product.setImageUrls(request.imageUrls());
        }
        if (request.primaryImageUrl() != null) {
            product.setPrimaryImageUrl(request.primaryImageUrl());
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

    private Product getOwned(UUID shopId, UUID productId) {
        return productRepository.findByIdAndStoreId(productId, shopId)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found"));
    }

    private ProductResponse toResponse(Product product) {
        Inventory inventory = inventoryService.getByProductId(product.getId());
        return new ProductResponse(
                product.getId(),
                product.getStoreId(),
                product.getName(),
                product.getDescription(),
                product.getCategoryCode(),
                product.getPrice(),
                inventory.getQuantityAvailable(),
                inventory.getLowStockThreshold(),
                product.isActive(),
                inventory.isLowStock(),
                product.getImageUrls(),
                product.getPrimaryImageUrl(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
