package com.konecta.stores_stock_service.catalog;

import com.konecta.stores_stock_service.catalog.dto.CreateSubcategoryRequest;
import com.konecta.stores_stock_service.catalog.dto.SubcategoryResponse;
import com.konecta.stores_stock_service.catalog.dto.UpdateSubcategoryRequest;
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

@RestController
@RequestMapping("/api/v1/admin/categories/{categoryId}/subcategories")
@PreAuthorize("hasRole('ADMIN')")
public class SubcategoryAdminController {

    private final SubcategoryAdminService subcategoryAdminService;

    public SubcategoryAdminController(SubcategoryAdminService subcategoryAdminService) {
        this.subcategoryAdminService = subcategoryAdminService;
    }

    @GetMapping
    public List<SubcategoryResponse> list(@PathVariable UUID categoryId) {
        return subcategoryAdminService.list(categoryId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubcategoryResponse create(@PathVariable UUID categoryId, @Valid @RequestBody CreateSubcategoryRequest request) {
        return subcategoryAdminService.create(categoryId, request);
    }

    @GetMapping("/{subcategoryId}")
    public SubcategoryResponse get(@PathVariable UUID categoryId, @PathVariable UUID subcategoryId) {
        return subcategoryAdminService.get(categoryId, subcategoryId);
    }

    @PatchMapping("/{subcategoryId}")
    public SubcategoryResponse update(@PathVariable UUID categoryId, @PathVariable UUID subcategoryId,
            @RequestBody UpdateSubcategoryRequest request) {
        return subcategoryAdminService.update(categoryId, subcategoryId, request);
    }

    @DeleteMapping("/{subcategoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID categoryId, @PathVariable UUID subcategoryId) {
        subcategoryAdminService.delete(categoryId, subcategoryId);
    }
}
