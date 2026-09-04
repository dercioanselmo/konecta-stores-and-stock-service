package com.konecta.stores_stock_service.catalog.controller;

import com.konecta.stores_stock_service.catalog.dto.CategoryResponse;
import com.konecta.stores_stock_service.catalog.dto.CreateCategoryRequest;
import com.konecta.stores_stock_service.catalog.dto.UpdateCategoryRequest;
import com.konecta.stores_stock_service.catalog.model.Product;
import com.konecta.stores_stock_service.catalog.service.CategoryAdminService;
import com.konecta.stores_stock_service.common.storage.dto.ConfirmUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin CRUD for store-level (top) categories, e.g. Supermercado, Beleza.
 * Product-level subcategories live under {@link SubcategoryAdminController}.
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
public class CategoryAdminController {

    private final CategoryAdminService categoryAdminService;

    public CategoryAdminController(CategoryAdminService categoryAdminService) {
        this.categoryAdminService = categoryAdminService;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryAdminService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
        return categoryAdminService.create(request);
    }

    @GetMapping("/{categoryId}")
    public CategoryResponse get(@PathVariable UUID categoryId) {
        return categoryAdminService.get(categoryId);
    }

    @PatchMapping("/{categoryId}")
    public CategoryResponse update(@PathVariable UUID categoryId, @RequestBody UpdateCategoryRequest request) {
        return categoryAdminService.update(categoryId, request);
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID categoryId) {
        categoryAdminService.delete(categoryId);
    }

    @PostMapping("/{categoryId}/image/presign")
    public PresignUploadResponse presignImage(@PathVariable UUID categoryId,
            @Valid @RequestBody PresignUploadRequest request) {
        return categoryAdminService.presignImageUpload(categoryId, request);
    }

    @PostMapping("/{categoryId}/image")
    public CategoryResponse confirmImage(@PathVariable UUID categoryId,
            @Valid @RequestBody ConfirmUploadRequest request) {
        return categoryAdminService.confirmImageUpload(categoryId, request);
    }
}
