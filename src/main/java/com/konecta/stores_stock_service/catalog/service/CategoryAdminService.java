package com.konecta.stores_stock_service.catalog.service;

import com.konecta.stores_stock_service.catalog.dto.CategoryResponse;
import com.konecta.stores_stock_service.catalog.dto.CreateCategoryRequest;
import com.konecta.stores_stock_service.catalog.dto.UpdateCategoryRequest;
import com.konecta.stores_stock_service.catalog.model.Category;
import com.konecta.stores_stock_service.catalog.repository.CategoryRepository;
import com.konecta.stores_stock_service.catalog.repository.SubcategoryRepository;
import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.common.storage.ObjectStorageService;
import com.konecta.stores_stock_service.common.storage.PresignedUpload;
import com.konecta.stores_stock_service.common.storage.S3KeyFactory;
import com.konecta.stores_stock_service.common.storage.dto.ConfirmUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadResponse;
import com.konecta.stores_stock_service.store.repository.StoreCategoryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryAdminService {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final StoreCategoryRepository storeCategoryRepository;
    private final ObjectStorageService objectStorageService;
    private final S3KeyFactory s3KeyFactory;

    public CategoryAdminService(CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository,
            StoreCategoryRepository storeCategoryRepository, ObjectStorageService objectStorageService,
            S3KeyFactory s3KeyFactory) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.storeCategoryRepository = storeCategoryRepository;
        this.objectStorageService = objectStorageService;
        this.s3KeyFactory = s3KeyFactory;
    }

    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream().map(this::toResponse).toList();
    }

    public CategoryResponse get(UUID categoryId) {
        return toResponse(getEntity(categoryId));
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String code = request.code().trim().toUpperCase();
        if (categoryRepository.existsByCode(code)) {
            throw ApiException.conflict("CATEGORY_CODE_ALREADY_EXISTS", "Já existe uma categoria com este código");
        }
        Category category = new Category();
        category.setCode(code);
        category.setName(request.name());
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        category.setActive(request.active() == null || request.active());
        category = categoryRepository.save(category);
        return toResponse(category);
    }

    @Transactional
    public CategoryResponse update(UUID categoryId, UpdateCategoryRequest request) {
        Category category = getEntity(categoryId);
        if (request.name() != null) {
            category.setName(request.name());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return toResponse(category);
    }

    @Transactional
    public void delete(UUID categoryId) {
        Category category = getEntity(categoryId);
        if (subcategoryRepository.existsByCategoryId(categoryId)) {
            throw ApiException.conflict("CATEGORY_IN_USE",
                    "Esta categoria tem subcategorias associadas — desative-a em vez de a eliminar");
        }
        if (storeCategoryRepository.existsByCategoryId(categoryId)) {
            throw ApiException.conflict("CATEGORY_IN_USE",
                    "Esta categoria está associada a lojas — desative-a em vez de a eliminar");
        }
        categoryRepository.delete(category);
    }

    public PresignUploadResponse presignImageUpload(UUID categoryId, PresignUploadRequest request) {
        getEntity(categoryId);
        String contentType = s3KeyFactory.requireValidContentType(request.contentType());
        String key = s3KeyFactory.categoryImageKey(categoryId, contentType);
        PresignedUpload upload = objectStorageService.presignUpload(key, contentType);
        return new PresignUploadResponse(upload.uploadUrl(), upload.key(), upload.expiresAt());
    }

    @Transactional
    public CategoryResponse confirmImageUpload(UUID categoryId, ConfirmUploadRequest request) {
        Category category = getEntity(categoryId);
        s3KeyFactory.requireOwnedKey(request.key(), s3KeyFactory.categoryImagePrefix(categoryId));
        if (!objectStorageService.exists(request.key())) {
            throw ApiException.validation(List.of("key: ficheiro não encontrado — confirme após o upload terminar"));
        }
        category.setImageKey(request.key());
        return toResponse(category);
    }

    private Category getEntity(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Categoria não encontrada"));
    }

    private CategoryResponse toResponse(Category category) {
        String imageUrl = category.getImageKey() == null ? null : objectStorageService.presignDownload(category.getImageKey());
        return new CategoryResponse(category.getId(), category.getCode(), category.getName(),
                category.getSortOrder(), category.isActive(), imageUrl);
    }
}
