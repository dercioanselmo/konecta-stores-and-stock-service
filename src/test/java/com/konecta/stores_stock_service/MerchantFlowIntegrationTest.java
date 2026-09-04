package com.konecta.stores_stock_service;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konecta.stores_stock_service.support.TestJwtUtil;
import com.konecta.stores_stock_service.support.TestStorageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestStorageConfig.class)
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

    private String staffToken(String userId, String shopId) {
        return "Bearer " + TestJwtUtil.staffToken(userId, shopId);
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

        // list ("my shops") also carries categories, not just the single-shop GET
        mockMvc.perform(get("/api/v1/merchant/shops")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(shopId)))
                .andExpect(jsonPath("$[0].categories[0].code", is("SUPERMERCADO")));

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
    void reSubmittingUnchangedCategoriesAndHours_doesNotViolateUniqueConstraint() throws Exception {
        // Regression test: replace-all (delete-then-insert) on a unique
        // (store_id, X) key must survive re-adding a row the store already
        // had — Hibernate flushes all inserts before all deletes within one
        // transaction regardless of call order, so this fails without an
        // explicit flush between the delete and the insert.
        String auth = merchantToken("owner-" + System.nanoTime());

        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Loja Reenvio", "address": "Rua X",
                                  "city": "Maputo", "categoryIds": ["%s"] }
                                """.formatted(supermercadoCategoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String shopId = objectMapper.readTree(shopResponse).get("id").asText();

        // PATCH with the same categoryIds a second time
        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"categoryIds\": [\"%s\"] }".formatted(supermercadoCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].code", is("SUPERMERCADO")));

        String hoursBody = """
                { "days": [ { "day": "SEGUNDA", "opensAt": "08:00", "closesAt": "18:00", "closed": false } ] }
                """;
        mockMvc.perform(put("/api/v1/merchant/shops/" + shopId + "/hours")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hoursBody))
                .andExpect(status().isOk());

        // PUT the same hours a second time — this is the normal case for any
        // settings-form re-save, not an edge case
        mockMvc.perform(put("/api/v1/merchant/shops/" + shopId + "/hours")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hoursBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].day", is("SEGUNDA")));
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
    void productPhotoUploadLifecycle() throws Exception {
        String auth = merchantToken("owner-" + System.nanoTime());

        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Loja Fotos", "nuit": "111222333", "address": "Rua Y",
                                  "city": "Maputo", "neighborhood": "Central" }
                                """))
                .andReturn().getResponse().getContentAsString();
        String shopId = objectMapper.readTree(shopResponse).get("id").asText();

        String productResponse = mockMvc.perform(post("/api/v1/merchant/shops/" + shopId + "/products")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Produto com Fotos", "description": "desc",
                                  "price": 10.0, "stockQuantity": 5 }
                                """))
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productResponse).get("id").asText();
        String photosBase = "/api/v1/merchant/shops/" + shopId + "/products/" + productId + "/photos";

        String photo1Key = presign(auth, photosBase + "/presign", "image/jpeg");
        String photo1Response = mockMvc.perform(post(photosBase)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"key\": \"" + photo1Key + "\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPrimary", is(true)))
                .andExpect(jsonPath("$.url").exists())
                .andReturn().getResponse().getContentAsString();
        String photo1Id = objectMapper.readTree(photo1Response).get("id").asText();

        String photo2Key = presign(auth, photosBase + "/presign", "image/png");
        String photo2Response = mockMvc.perform(post(photosBase)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"key\": \"" + photo2Key + "\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPrimary", is(false)))
                .andReturn().getResponse().getContentAsString();
        String photo2Id = objectMapper.readTree(photo2Response).get("id").asText();

        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId + "/products/" + productId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()", is(2)));

        // unsupported content type is rejected at the presign step
        mockMvc.perform(post(photosBase + "/presign")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"contentType\": \"application/pdf\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));

        // a key that doesn't belong to this product is rejected on confirm
        mockMvc.perform(post(photosBase)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"key\": \"products/some-other-product/x.jpg\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));

        // deleting the primary photo promotes the next one automatically
        mockMvc.perform(delete(photosBase + "/" + photo1Id).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId + "/products/" + productId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[0].id", is(photo2Id)))
                .andExpect(jsonPath("$.photos[0].isPrimary", is(true)));

        // shop logo upload
        String logoKey = presign(auth, "/api/v1/merchant/shops/" + shopId + "/logo/presign", "image/webp");
        mockMvc.perform(post("/api/v1/merchant/shops/" + shopId + "/logo")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"key\": \"" + logoKey + "\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").exists());
    }

    private String presign(String auth, String presignPath, String contentType) throws Exception {
        String response = mockMvc.perform(post(presignPath)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"contentType\": \"" + contentType + "\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("key").asText();
    }

    @Test
    void merchantStaff_canReadAssignedShopAndProducts() throws Exception {
        // Merchant creates a shop
        String owner = "owner-" + System.nanoTime();
        String merchantAuth = merchantToken(owner);

        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", merchantAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Loja Staff", "nuit": "987654321", "address": "Av. Mao Tse Tung",
                                  "city": "Maputo", "neighborhood": "Central" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String shopId = objectMapper.readTree(shopResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/merchant/shops/" + shopId + "/products")
                        .header("Authorization", merchantAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Produto Staff", "description": "desc",
                                  "price": 50.0, "stockQuantity": 10 }
                                """))
                .andExpect(status().isCreated());

        // Staff token scoped to this shop can read it
        String staffAuth = staffToken("staff-" + System.nanoTime(), shopId);

        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId)
                        .header("Authorization", staffAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(shopId)));

        mockMvc.perform(get("/api/v1/merchant/shops")
                        .header("Authorization", staffAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(shopId)));

        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId + "/products")
                        .header("Authorization", staffAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));

        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId + "/dashboard/summary")
                        .header("Authorization", staffAuth))
                .andExpect(status().isOk());
    }

    @Test
    void merchantStaff_cannotAccessDifferentShop() throws Exception {
        String owner = "owner-" + System.nanoTime();
        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", merchantToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Loja Alheia", "nuit": "111000111", "address": "Rua Z",
                                  "city": "Maputo", "neighborhood": "Central" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String shopId = objectMapper.readTree(shopResponse).get("id").asText();

        // Staff token scoped to a different shop ID
        String staffAuth = staffToken("staff-" + System.nanoTime(), "00000000-0000-0000-0000-000000000000");

        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId)
                        .header("Authorization", staffAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("SHOP_NOT_FOUND")));
    }

    @Test
    void merchantStaff_canWriteProductsButNotShopSettings() throws Exception {
        // The product ask (see API_REFERENCE_MERCHANT_DASHBOARD.md's
        // MERCHANT_STAFF section): full read/write on products for the
        // assigned shop, but shop-level settings (profile, hours, status,
        // logo/cover) stay MERCHANT-only.
        String owner = "owner-" + System.nanoTime();
        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", merchantToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Loja Write", "nuit": "222333444", "address": "Rua W",
                                  "city": "Maputo", "neighborhood": "Central" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String shopId = objectMapper.readTree(shopResponse).get("id").asText();

        String staffAuth = staffToken("staff-" + System.nanoTime(), shopId);

        String productResponse = mockMvc.perform(post("/api/v1/merchant/shops/" + shopId + "/products")
                        .header("Authorization", staffAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Produto", "description": "desc",
                                  "price": 10.0, "stockQuantity": 5 }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productResponse).get("id").asText();

        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId + "/products/" + productId + "/stock")
                        .header("Authorization", staffAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"quantity\": 20 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity", is(20)));

        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId + "/products/" + productId + "/active")
                        .header("Authorization", staffAuth)
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        // shop-level writes stay MERCHANT-only
        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId)
                        .header("Authorization", staffAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"description\": \"tentativa\" }"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/merchant/shops/" + shopId + "/hours")
                        .header("Authorization", staffAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"days\": [ { \"day\": \"SEGUNDA\", \"opensAt\": \"08:00\", \"closesAt\": \"18:00\", \"closed\": false } ] }"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shopLocation_persistsAndRejectsOutsideMaputo() throws Exception {
        String owner = "owner-" + System.nanoTime();
        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", merchantToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Loja Geo", "nuit": "111222333", "address": "Rua G",
                                  "city": "Maputo", "neighborhood": "Central" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String shopId = objectMapper.readTree(shopResponse).get("id").asText();
        String auth = merchantToken(owner);

        // freshly created shop has no location yet
        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(org.hamcrest.Matchers.nullValue()));

        // outside the Maputo bounding box -> rejected
        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId + "/location")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"latitude\": 40.7128, \"longitude\": -74.0060 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));

        // inside Maputo -> saved and reflected on the next read
        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId + "/location")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"latitude\": -25.9692, \"longitude\": 32.5732 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude", is(-25.9692)))
                .andExpect(jsonPath("$.longitude", is(32.5732)));

        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude", is(-25.9692)));

        // MERCHANT_STAFF cannot set location — shop-level setting, same as hours/status
        String staffAuth = staffToken("staff-" + System.nanoTime(), shopId);
        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId + "/location")
                        .header("Authorization", staffAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"latitude\": -25.9692, \"longitude\": 32.5732 }"))
                .andExpect(status().isForbidden());

        // ADMIN can set location on a shop it doesn't own, same as the other shop-settings endpoints
        String adminAuth = adminToken("admin-" + System.nanoTime());
        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId + "/location")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"latitude\": -25.95, \"longitude\": 32.58 }"))
                .andExpect(status().isOk());
    }

    @Test
    void admin_canManageAnyShopButNotCreateOne() throws Exception {
        // Admin ask (see API_REFERENCE_MERCHANT_DASHBOARD.md's Admin access
        // section): same shop-management capabilities as the owning MERCHANT
        // — read, edit, hours, status, logo/cover, products — for any shop,
        // without owning it. Creating brand-new shops stays MERCHANT-only.
        String owner = "owner-" + System.nanoTime();
        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", merchantToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Loja Admin", "nuit": "555666777", "address": "Rua A",
                                  "city": "Maputo", "neighborhood": "Central" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String shopId = objectMapper.readTree(shopResponse).get("id").asText();

        String adminAuth = adminToken("admin-" + System.nanoTime());

        mockMvc.perform(get("/api/v1/merchant/shops/" + shopId)
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(shopId)));

        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId)
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"description\": \"editado pelo admin\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("editado pelo admin")));

        mockMvc.perform(put("/api/v1/merchant/shops/" + shopId + "/hours")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"days\": [ { \"day\": \"SEGUNDA\", \"opensAt\": \"08:00\", \"closesAt\": \"18:00\", \"closed\": false } ] }"))
                .andExpect(status().isOk());

        String productResponse = mockMvc.perform(post("/api/v1/merchant/shops/" + shopId + "/products")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Produto Admin", "description": "desc",
                                  "price": 10.0, "stockQuantity": 5 }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        objectMapper.readTree(productResponse).get("id").asText();

        // creating a brand-new shop is still MERCHANT-only
        mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Loja Nova", "nuit": "888999000", "address": "Rua B",
                                  "city": "Maputo", "neighborhood": "Central" }
                                """))
                .andExpect(status().isForbidden());

        // GET /merchant/shops (list) is a "list mine" endpoint, not opened to admin
        mockMvc.perform(get("/api/v1/merchant/shops")
                        .header("Authorization", adminAuth))
                .andExpect(status().isForbidden());

        // the dedicated admin listing sees every shop, including this one
        mockMvc.perform(get("/api/v1/admin/shops")
                        .header("Authorization", adminAuth)
                        .param("query", "Loja Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(shopId)))
                .andExpect(jsonPath("$.content[0].ownerId", is(owner)));

        mockMvc.perform(get("/api/v1/admin/shops")
                        .header("Authorization", merchantToken(owner)))
                .andExpect(status().isForbidden());
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

    @Test
    void categoryImage_presignConfirmAndPublicRead() throws Exception {
        String admin = adminToken("admin-" + System.nanoTime());

        String createCategoryBody = """
                { "code": "PET_SHOP_IMG_%d", "name": "Pet Shop Imagem", "sortOrder": 60 }
                """.formatted(System.nanoTime());
        String categoryResponse = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCategoryBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categoryResponse).get("id").asText();

        // public read reflects no image yet
        mockMvc.perform(get("/api/v1/meta/categories"))
                .andExpect(status().isOk());

        String imageKey = presign(admin, "/api/v1/admin/categories/" + categoryId + "/image/presign", "image/png");

        mockMvc.perform(post("/api/v1/admin/categories/" + categoryId + "/image")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"key\": \"" + imageKey + "\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").exists());

        // public listing now carries a presigned imageUrl for this category
        mockMvc.perform(get("/api/v1/meta/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + categoryId + "')].imageUrl").exists());

        // a MERCHANT token cannot upload a category image
        mockMvc.perform(post("/api/v1/admin/categories/" + categoryId + "/image/presign")
                        .header("Authorization", merchantToken("some-merchant"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"contentType\": \"image/png\" }"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminShopsList_filtersByCategory() throws Exception {
        String admin = adminToken("admin-" + System.nanoTime());
        String owner = "owner-" + System.nanoTime();

        String categoriesResponse = mockMvc.perform(get("/api/v1/meta/categories"))
                .andReturn().getResponse().getContentAsString();
        var categories = objectMapper.readTree(categoriesResponse);
        String categoryId = categories.get(0).get("id").asText();
        String otherCategoryId = categories.get(1).get("id").asText();

        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", merchantToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Loja Filtro Categoria", "nuit": "999888777", "address": "Rua F",
                                  "city": "Maputo", "neighborhood": "Central", "categoryIds": ["%s"] }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String shopId = objectMapper.readTree(shopResponse).get("id").asText();

        mockMvc.perform(get("/api/v1/admin/shops")
                        .header("Authorization", admin)
                        .param("categoryId", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + shopId + "')]").exists());

        mockMvc.perform(get("/api/v1/admin/shops")
                        .header("Authorization", admin)
                        .param("categoryId", otherCategoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + shopId + "')]").doesNotExist());
    }

    @Test
    void publicShopsList_proximitySortedAndExcludesUnlocatedOrOtherCategory() throws Exception {
        String owner = "owner-" + System.nanoTime();
        String auth = merchantToken(owner);

        String categoriesResponse = mockMvc.perform(get("/api/v1/meta/categories"))
                .andReturn().getResponse().getContentAsString();
        var categories = objectMapper.readTree(categoriesResponse);
        String categoryId = categories.get(0).get("id").asText();
        String otherCategoryId = categories.get(1).get("id").asText();

        // near, right category
        String nearId = createLocatedShop(auth, "Loja Perto", categoryId, "999000111", -25.9700, 32.5750);
        // far, right category
        String farId = createLocatedShop(auth, "Loja Longe", categoryId, "999000112", -25.8000, 32.7000);
        // right category, no location -> excluded
        String noLocationId = createShopNoLocation(auth, "Loja Sem Localizacao", categoryId, "999000113");
        // located, wrong category -> excluded
        String wrongCategoryId = createLocatedShop(auth, "Loja Outra Categoria", otherCategoryId, "999000114",
                -25.9690, 32.5730);

        // no Authorization header at all — public endpoint
        String response = mockMvc.perform(get("/api/v1/shops")
                        .param("categoryId", categoryId)
                        .param("lat", "-25.9692")
                        .param("lng", "32.5732"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var content = objectMapper.readTree(response).get("content");
        java.util.List<String> ids = new java.util.ArrayList<>();
        content.forEach(n -> ids.add(n.get("id").asText()));

        org.assertj.core.api.Assertions.assertThat(ids).contains(nearId, farId);
        org.assertj.core.api.Assertions.assertThat(ids.indexOf(nearId)).isLessThan(ids.indexOf(farId));
        org.assertj.core.api.Assertions.assertThat(ids).doesNotContain(noLocationId, wrongCategoryId);

        // missing required params -> 400 VALIDATION_ERROR
        mockMvc.perform(get("/api/v1/shops").param("categoryId", categoryId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    private String createLocatedShop(String auth, String name, String categoryId, String nuit, double lat, double lng)
            throws Exception {
        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s", "nuit": "%s", "address": "Rua P",
                                  "city": "Maputo", "neighborhood": "Central", "categoryIds": ["%s"] }
                                """.formatted(name, nuit, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String shopId = objectMapper.readTree(shopResponse).get("id").asText();

        mockMvc.perform(patch("/api/v1/merchant/shops/" + shopId + "/location")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"latitude\": " + lat + ", \"longitude\": " + lng + " }"))
                .andExpect(status().isOk());
        return shopId;
    }

    private String createShopNoLocation(String auth, String name, String categoryId, String nuit) throws Exception {
        String shopResponse = mockMvc.perform(post("/api/v1/merchant/shops")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s", "nuit": "%s", "address": "Rua P",
                                  "city": "Maputo", "neighborhood": "Central", "categoryIds": ["%s"] }
                                """.formatted(name, nuit, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(shopResponse).get("id").asText();
    }
}
