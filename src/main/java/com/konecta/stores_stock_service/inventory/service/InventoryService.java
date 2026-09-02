package com.konecta.stores_stock_service.inventory.service;

import com.konecta.stores_stock_service.catalog.model.Product;
import com.konecta.stores_stock_service.catalog.repository.ProductRepository;
import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.inventory.model.Inventory;
import com.konecta.stores_stock_service.inventory.model.StockMovement;
import com.konecta.stores_stock_service.inventory.model.StockMovementReason;
import com.konecta.stores_stock_service.inventory.repository.InventoryRepository;
import com.konecta.stores_stock_service.inventory.repository.StockMovementRepository;
import com.konecta.stores_stock_service.store.service.LowStockCounter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService implements LowStockCounter {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;

    public InventoryService(InventoryRepository inventoryRepository,
            StockMovementRepository stockMovementRepository,
            ProductRepository productRepository) {
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Inventory createForProduct(UUID productId, int quantityAvailable, int lowStockThreshold) {
        if (quantityAvailable < 0) {
            throw ApiException.validation(List.of("stockQuantity: não pode ser negativo"));
        }
        Inventory inventory = new Inventory(productId, quantityAvailable, lowStockThreshold);
        return inventoryRepository.save(inventory);
    }

    public Inventory getByProductId(UUID productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> ApiException.notFound("INVENTORY_NOT_FOUND", "Não existe registo de stock para este produto"));
    }

    @Transactional
    public Inventory setThreshold(UUID productId, int lowStockThreshold) {
        Inventory inventory = getByProductId(productId);
        inventory.setLowStockThreshold(lowStockThreshold);
        return inventory;
    }

    @Transactional
    public Inventory setAbsoluteQuantity(UUID productId, int newQuantity, String actorUserId) {
        if (newQuantity < 0) {
            throw ApiException.validation(List.of("quantity: não pode ser negativo"));
        }
        Inventory inventory = getByProductId(productId);
        int delta = newQuantity - inventory.getQuantityAvailable();
        inventory.setQuantityAvailable(newQuantity);
        if (delta != 0) {
            stockMovementRepository.save(
                    new StockMovement(productId, delta, StockMovementReason.MANUAL_ADJUST, actorUserId));
        }
        return inventory;
    }

    public boolean isLowStock(UUID productId) {
        return inventoryRepository.findByProductId(productId).map(Inventory::isLowStock).orElse(false);
    }

    @Override
    public long countLowStock(UUID storeId) {
        List<UUID> productIds = productRepository.findByStoreId(storeId).stream()
                .map(Product::getId)
                .toList();
        if (productIds.isEmpty()) {
            return 0;
        }
        return productIds.stream().filter(this::isLowStock).count();
    }
}
