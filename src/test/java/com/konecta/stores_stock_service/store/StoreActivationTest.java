package com.konecta.stores_stock_service.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoreActivationTest {

    private Store validStore() {
        Store store = new Store();
        store.setTradeName("Loja Central");
        store.setNuit("123456789");
        store.setAddressLine("Av. Julius Nyerere");
        store.setCity("Maputo");
        store.setNeighborhood("Central");
        return store;
    }

    @Test
    void meetsActivationRequirements_whenAllFieldsPresent() {
        assertThat(validStore().meetsActivationRequirements()).isTrue();
    }

    @Test
    void doesNotMeetActivationRequirements_whenNuitMissing() {
        Store store = validStore();
        store.setNuit(null);
        assertThat(store.meetsActivationRequirements()).isFalse();
    }

    @Test
    void doesNotMeetActivationRequirements_whenAddressBlank() {
        Store store = validStore();
        store.setAddressLine("  ");
        assertThat(store.meetsActivationRequirements()).isFalse();
    }

    @Test
    void doesNotMeetActivationRequirements_whenNeighborhoodMissing() {
        Store store = validStore();
        store.setNeighborhood(null);
        assertThat(store.meetsActivationRequirements()).isFalse();
    }
}
