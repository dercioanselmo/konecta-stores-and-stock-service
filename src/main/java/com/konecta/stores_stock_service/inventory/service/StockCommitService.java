package com.konecta.stores_stock_service.inventory.service;

import com.konecta.stores_stock_service.catalog.model.Product;
import com.konecta.stores_stock_service.catalog.repository.ProductRepository;
import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.inventory.dto.StockCommitItemRequest;
import com.konecta.stores_stock_service.inventory.dto.StockCommitLineResponse;
import com.konecta.stores_stock_service.inventory.dto.StockCommitRequest;
import com.konecta.stores_stock_service.inventory.dto.StockCommitResponse;
import com.konecta.stores_stock_service.inventory.model.Inventory;
import com.konecta.stores_stock_service.inventory.model.StockMovement;
import com.konecta.stores_stock_service.inventory.model.StockMovementReason;
import com.konecta.stores_stock_service.inventory.repository.InventoryRepository;
import com.konecta.stores_stock_service.inventory.repository.StockMovementRepository;
import com.konecta.stores_stock_service.store.model.StoreStatus;
import com.konecta.stores_stock_service.store.repository.StoreRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes AGENTS.md's checkout rule CHK-13: the one place stock is actually
 * decremented for an order, called by KONECTA-CHECKOUT-SERVICE with the
 * customer's own JWT (any authenticated role — this is not merchant-scoped,
 * same reasoning as Cart's own endpoints).
 */
@Service
public class StockCommitService {

    private static final String REF_TYPE_ORDER = "ORDER";

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StoreRepository storeRepository;

    public StockCommitService(ProductRepository productRepository, InventoryRepository inventoryRepository,
            StockMovementRepository stockMovementRepository, StoreRepository storeRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.storeRepository = storeRepository;
    }

    @Transactional
    public StockCommitResponse commit(UUID shopId, StockCommitRequest request, String actorUserId) {
        String refId = request.orderId().toString();

        // Idempotent replay: every line for one order is committed in a
        // single transaction below, so the existence of any stock movement
        // for this orderId proves the whole commit already succeeded --
        // checkout may retry this call on a transient network error even
        // though the order itself was already persisted.
        if (stockMovementRepository.existsByRefTypeAndRefId(REF_TYPE_ORDER, refId)) {
            return buildResponse(request);
        }

        storeRepository.findById(shopId)
                .filter(store -> store.getStatus() == StoreStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("SHOP_NOT_FOUND", "Loja não encontrada"));

        Map<UUID, Product> productsById = new LinkedHashMap<>();
        Map<UUID, Inventory> inventoriesById = new LinkedHashMap<>();
        for (StockCommitItemRequest item : request.items()) {
            Product product = productRepository.findByIdAndStoreId(item.productId(), shopId)
                    .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Produto não encontrado"));
            productsById.put(item.productId(), product);
            inventoriesById.put(item.productId(), inventoryRepository.findByProductId(item.productId())
                    .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Produto não encontrado")));
        }

        List<InsufficientStockException.FailedItem> failed = new ArrayList<>();
        for (StockCommitItemRequest item : request.items()) {
            int available = inventoriesById.get(item.productId()).getQuantityAvailable();
            if (item.quantity() > available) {
                failed.add(new InsufficientStockException.FailedItem(item.productId(), item.quantity(), available));
            }
        }
        if (!failed.isEmpty()) {
            throw new InsufficientStockException(failed);
        }

        List<StockCommitLineResponse> lines = new ArrayList<>();
        for (StockCommitItemRequest item : request.items()) {
            Inventory inventory = inventoriesById.get(item.productId());
            inventory.setQuantityAvailable(inventory.getQuantityAvailable() - item.quantity());

            StockMovement movement = new StockMovement(item.productId(), -item.quantity(),
                    StockMovementReason.SALE_COMMIT, actorUserId);
            movement.setRefType(REF_TYPE_ORDER);
            movement.setRefId(refId);
            stockMovementRepository.save(movement);

            lines.add(new StockCommitLineResponse(item.productId(), inventory.getQuantityAvailable()));
        }

        return new StockCommitResponse(request.orderId(), lines);
    }

    private StockCommitResponse buildResponse(StockCommitRequest request) {
        List<StockCommitLineResponse> lines = request.items().stream()
                .map(item -> new StockCommitLineResponse(item.productId(),
                        inventoryRepository.findByProductId(item.productId())
                                .map(Inventory::getQuantityAvailable)
                                .orElse(0)))
                .toList();
        return new StockCommitResponse(request.orderId(), lines);
    }
}
