package com.konecta.stores_stock_service.support;

import com.konecta.stores_stock_service.common.storage.ObjectStorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestStorageConfig {

    @Bean
    @Primary
    public ObjectStorageService objectStorageService() {
        return new FakeObjectStorageService();
    }
}
