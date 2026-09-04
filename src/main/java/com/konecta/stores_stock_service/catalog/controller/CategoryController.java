package com.konecta.stores_stock_service.catalog.controller;

import com.konecta.stores_stock_service.catalog.dto.CategoryResponse;
import com.konecta.stores_stock_service.catalog.dto.SubcategoryResponse;
import com.konecta.stores_stock_service.catalog.model.Category;
import com.konecta.stores_stock_service.catalog.repository.CategoryRepository;
import com.konecta.stores_stock_service.catalog.repository.SubcategoryRepository;
import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.common.storage.ObjectStorageService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, active-only category reads for pickers (shop category, product
 * subcategory). Admin CRUD lives under {@link CategoryAdminController} /
 * {@link SubcategoryAdminController}.
 */
@RestController
@RequestMapping("/api/v1/meta/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final ObjectStorageService objectStorageService;

    public CategoryController(CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository,
            ObjectStorageService objectStorageService) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.objectStorageService = objectStorageService;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getSortOrder(), c.isActive(),
                        c.getImageKey() == null ? null : objectStorageService.presignDownload(c.getImageKey())))
                .toList();
    }

    @GetMapping("/{categoryId}/subcategories")
    public List<SubcategoryResponse> subcategories(@PathVariable UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Categoria não encontrada"));
        return subcategoryRepository.findByCategoryIdAndActiveTrueOrderBySortOrderAsc(categoryId).stream()
                .map(s -> new SubcategoryResponse(s.getId(), category.getId(), category.getCode(), category.getName(),
                        s.getCode(), s.getName(), s.getSortOrder(), s.isActive()))
                .toList();
    }
}
