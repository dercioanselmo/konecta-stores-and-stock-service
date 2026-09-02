package com.konecta.stores_stock_service;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.konecta.stores_stock_service.support.TestJwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

    private String supermercadoCategoryId;
    private String legumesSubcategoryId;

    private String merchantToken(String userId) {
        return "Bearer " + TestJwtUtil.token(userId, "MERCHANT");
    }

    private String adminToken(String userId) {
        return "Bearer " + TestJwtUtil.token(userId, "ADMIN");
    }

    @BeforeEach
    void loadSeededTaxonomy() throws Exception {
        String categories = mockMvc.perform(get("/api/v1/meta/categories"))
                .andReturn().getResponse().getContentAsString();
        JsonNode categoriesJson = objectMapper.readTree(categories);
        for (JsonNode c : categoriesJson) {
            if ("SUPERMERCADO".equals(c.get("code").asText())) {
                supermercadoCategoryId = c.get("id").asText();
            }
        }

        String subcategories = mockMvc.perform(get("/api/v1/meta/categories/" + supermercadoCategoryId + "/subcategories"))
                .andReturn().getResponse().getContentAsString();
        legumesSubcategoryId = objectMapper.readTree(subcategories).get(0).get("id").asText();
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
                  "categoryIds": ["%s"] }
                """.formatted(supermercadoCategoryId);

        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createShopBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.activationReady", is(true)))
                .andExpect(jsonPath("$.categories[0].code", is("SUPERMERCADO")))
                .andReturn().getResponse().getContentAsString();

        String shopId = objectMapper.readTree(shopResponse).get("id").asText();

        String createProductBody = """
                { "name": "Arroz 5kg", "description": "Arroz agulha", "subcategoryId": "%s",
                  "price": 350.0, "stockQuantity": 3, "lowStockThreshold": 5 }
                """.formatted(legumesSubcategoryId);

        String productResponse = mockMvc.perform(post("/api/v1/merchant/shops/" + shopId + "/products")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lowStock", is(true)))
                .andExpect(jsonPath("$.categoryName", is("Supermercado")))
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

        // filter products by subcategory
        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId + "/products")
                        .param("subcategoryId", legumesSubcategoryId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));

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
    void unknownCategoryId_isRejectedOnShopCreate() throws Exception {
        String auth = merchantToken("owner-" + System.nanoTime());
        String body = """
                { "name": "Loja X", "address": "Rua X", "city": "Maputo",
                  "categoryIds": ["00000000-0000-0000-0000-000000000000"] }
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

    @Test
    void subcategoriesEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/api/v1/meta/categories/" + supermercadoCategoryId + "/subcategories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryCode", is("SUPERMERCADO")));
    }

    @Test
    void adminCanManageCategoriesAndSubcategories() throws Exception {
        String admin = adminToken("admin-" + System.nanoTime());

        String createCategoryBody = """
                { "code": "PET_SHOP_%d", "name": "Pet Shop", "sortOrder": 50 }
                """.formatted(System.nanoTime());

        String categoryResponse = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCategoryBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categoryResponse).get("id").asText();

        String createSubcategoryBody = """
                { "code": "RACAO", "name": "Ração" }
                """;
        String subcategoryResponse = mockMvc.perform(post("/api/v1/admin/categories/" + categoryId + "/subcategories")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSubcategoryBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId", is(categoryId)))
                .andReturn().getResponse().getContentAsString();
        String subcategoryId = objectMapper.readTree(subcategoryResponse).get("id").asText();

        // duplicate subcategory code within the same category is rejected
        mockMvc.perform(post("/api/v1/admin/categories/" + categoryId + "/subcategories")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSubcategoryBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("SUBCATEGORY_CODE_ALREADY_EXISTS")));

        // rename via PATCH
        mockMvc.perform(patch("/api/v1/admin/categories/" + categoryId + "/subcategories/" + subcategoryId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"Ração e Petiscos\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Ração e Petiscos")));

        // a MERCHANT token cannot manage categories
        mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", merchantToken("some-merchant"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCategoryBody))
                .andExpect(status().isForbidden());

        // category with a subcategory cannot be hard-deleted
        mockMvc.perform(delete("/api/v1/admin/categories/" + categoryId)
                        .header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CATEGORY_IN_USE")));

        // but the subcategory itself can be deleted once unused
        mockMvc.perform(delete("/api/v1/admin/categories/" + categoryId + "/subcategories/" + subcategoryId)
                        .header("Authorization", admin))
                .andExpect(status().isNoContent());

        // now the (now-empty) category can be deleted too
        mockMvc.perform(delete("/api/v1/admin/categories/" + categoryId)
                        .header("Authorization", admin))
                .andExpect(status().isNoContent());
    }
}
