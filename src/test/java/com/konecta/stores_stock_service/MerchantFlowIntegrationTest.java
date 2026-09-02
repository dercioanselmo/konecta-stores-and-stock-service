package com.konecta.stores_stock_service;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.konecta.stores_stock_service.support.TestJwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MerchantFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String merchantToken(String userId) {
        return "Bearer " + TestJwtUtil.token(userId, "MERCHANT");
    }

    @Test
    void noToken_isUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/shops"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    void customerToken_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/shops")
                        .header("Authorization", "Bearer " + TestJwtUtil.token("cust-1", "CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    void merchantCanCreateAndManageOwnShopEndToEnd() throws Exception {
        String owner = "owner-" + System.nanoTime();
        String auth = merchantToken(owner);

        String createShopBody = """
                { "name": "Loja Central", "nuit": "123456789", "address": "Av. Julius Nyerere",
                  "city": "Maputo", "neighborhood": "Central", "phone": "+258841234567",
                  "category": "SUPERMERCADO" }
                """;

        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createShopBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.activationReady", is(true)))
                .andReturn().getResponse().getContentAsString();

        String shopId = objectMapper.readTree(shopResponse).get("id").asText();

        String createProductBody = """
                { "name": "Arroz 5kg", "description": "Arroz agulha", "category": "SUPERMERCADO",
                  "price": 350.0, "stockQuantity": 3, "lowStockThreshold": 5 }
                """;

        String productResponse = mockMvc.perform(post("/api/v1/merchant/shops/" + shopId + "/products")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lowStock", is(true)))
                .andReturn().getResponse().getContentAsString();

        String productId = objectMapper.readTree(productResponse).get("id").asText();

        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId + "/dashboard/summary")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockCount", is(1)))
                .andExpect(jsonPath("$.productCount", is(1)));

        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId + "/products/" + productId + "/stock")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"quantity\": 20 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity", is(20)))
                .andExpect(jsonPath("$.lowStock", is(false)));

        // negative stock is rejected
        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId + "/products/" + productId + "/stock")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"quantity\": -1 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));

        // another merchant cannot see or touch this shop's products
        String intruderAuth = merchantToken("owner-" + System.nanoTime());
        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId, "")
                        .header("Authorization", intruderAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId + "/products")
                        .header("Authorization", intruderAuth))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonMaputoCity_isRejectedOnCreate() throws Exception {
        String auth = merchantToken("owner-" + System.nanoTime());
        String body = """
                { "name": "Loja Beira", "address": "Rua X", "city": "Beira" }
                """;
        mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    void categoriesEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/api/v1/meta/categories"))
                .andExpect(status().isOk());
    }
}
