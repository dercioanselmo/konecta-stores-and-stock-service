package com.konecta.stores_stock_service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.konecta.stores_stock_service.catalog.repository.CategoryRepository;
import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.store.hours.service.OpeningHoursService;
import com.konecta.stores_stock_service.store.model.Store;
import com.konecta.stores_stock_service.store.repository.StoreCategoryRepository;
import com.konecta.stores_stock_service.store.repository.StoreRepository;
import com.konecta.stores_stock_service.store.service.LowStockCounter;
import com.konecta.stores_stock_service.store.service.StoreService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreServiceOwnershipTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private StoreCategoryRepository storeCategoryRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private OpeningHoursService openingHoursService;
    @Mock
    private LowStockCounter lowStockCounter;

    private StoreService service() {
        return new StoreService(storeRepository, storeCategoryRepository, categoryRepository, openingHoursService,
                lowStockCounter);
    }

    @Test
    void getOwned_returnsStore_whenCallerIsOwner() {
        UUID shopId = UUID.randomUUID();
        Store store = new Store();
        store.setOwnerUserId("owner-1");
        when(storeRepository.findByIdAndOwnerUserId(shopId, "owner-1")).thenReturn(Optional.of(store));

        Store result = service().getOwned(shopId, "owner-1", false);

        assertThat(result).isSameAs(store);
    }

    @Test
    void getOwned_throwsNotFound_whenCallerIsNotOwner() {
        UUID shopId = UUID.randomUUID();
        when(storeRepository.findByIdAndOwnerUserId(shopId, "intruder")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getOwned(shopId, "intruder", false))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("SHOP_NOT_FOUND");
    }

    @Test
    void getOwned_bypassesOwnershipCheck_forAdmin() {
        UUID shopId = UUID.randomUUID();
        Store store = new Store();
        store.setOwnerUserId("some-other-owner");
        when(storeRepository.findById(shopId)).thenReturn(Optional.of(store));

        Store result = service().getOwned(shopId, "admin-user", true);

        assertThat(result).isSameAs(store);
    }
}
