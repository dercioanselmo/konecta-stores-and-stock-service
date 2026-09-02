package com.konecta.stores_stock_service.catalog;

import com.konecta.stores_stock_service.catalog.dto.CategoryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(c -> new CategoryResponse(c.getCode(), c.getName(), c.getSortOrder()))
                .toList();
    }
}
