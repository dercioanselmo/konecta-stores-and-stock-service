package com.konecta.stores_stock_service.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konecta.stores_stock_service.common.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private com.konecta.stores_stock_service.catalog.ProductRepository productRepository;

    private InventoryService service() {
        return new InventoryService(inventoryRepository, stockMovementRepository, productRepository);
    }

    @Test
    void setAbsoluteQuantity_rejectsNegative() {
        InventoryService service = service();
        UUID productId = UUID.randomUUID();

        assertThatThrownBy(() -> service.setAbsoluteQuantity(productId, -1, "user-1"))
                .isInstanceOf(ApiException.class)
                .extracting(t -> ((ApiException) t).getDetails())
                .satisfies(details -> assertThat(details.toString()).contains("must not be negative"));
    }

    @Test
    void setAbsoluteQuantity_recordsManualAdjustMovement() {
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 10, 5);
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));

        InventoryService service = service();
        Inventory result = service.setAbsoluteQuantity(productId, 3, "user-1");

        assertThat(result.getQuantityAvailable()).isEqualTo(3);
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void setAbsoluteQuantity_noMovementRecorded_whenQuantityUnchanged() {
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 10, 5);
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));

        InventoryService service = service();
        service.setAbsoluteQuantity(productId, 10, "user-1");

        verify(stockMovementRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void isLowStock_trueWhenAtOrBelowThreshold() {
        UUID productId = UUID.randomUUID();
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(new Inventory(productId, 5, 5)));

        assertThat(service().isLowStock(productId)).isTrue();
    }

    @Test
    void isLowStock_falseWhenAboveThreshold() {
        UUID productId = UUID.randomUUID();
        when(inventoryRepository.findByProductId(productId))
                .thenReturn(Optional.of(new Inventory(productId, 6, 5)));

        assertThat(service().isLowStock(productId)).isFalse();
    }

    @Test
    void createForProduct_rejectsNegativeInitialQuantity() {
        assertThatThrownBy(() -> service().createForProduct(UUID.randomUUID(), -5, 5))
                .isInstanceOf(ApiException.class);
    }
}
