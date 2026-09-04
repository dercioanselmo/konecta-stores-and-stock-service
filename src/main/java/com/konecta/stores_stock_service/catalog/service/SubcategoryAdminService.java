package com.konecta.stores_stock_service.catalog.service;

import com.konecta.stores_stock_service.catalog.dto.CreateSubcategoryRequest;
import com.konecta.stores_stock_service.catalog.dto.SubcategoryResponse;
import com.konecta.stores_stock_service.catalog.dto.UpdateSubcategoryRequest;
import com.konecta.stores_stock_service.catalog.model.Category;
import com.konecta.stores_stock_service.catalog.model.Subcategory;
import com.konecta.stores_stock_service.catalog.repository.CategoryRepository;
import com.konecta.stores_stock_service.catalog.repository.ProductRepository;
import com.konecta.stores_stock_service.catalog.repository.SubcategoryRepository;
import com.konecta.stores_stock_service.common.ApiException;
import com.konecta.stores_stock_service.common.storage.ObjectStorageService;
import com.konecta.stores_stock_service.common.storage.PresignedUpload;
import com.konecta.stores_stock_service.common.storage.S3KeyFactory;
import com.konecta.stores_stock_service.common.storage.dto.ConfirmUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadRequest;
import com.konecta.stores_stock_service.common.storage.dto.PresignUploadResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubcategoryAdminService {

    private final SubcategoryRepository subcategoryRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ObjectStorageService objectStorageService;
    private final S3KeyFactory s3KeyFactory;

    public SubcategoryAdminService(SubcategoryRepository subcategoryRepository, CategoryRepository categoryRepository,
            ProductRepository productRepository, ObjectStorageService objectStorageService, S3KeyFactory s3KeyFactory) {
        this.subcategoryRepository = subcategoryRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.objectStorageService = objectStorageService;
        this.s3KeyFactory = s3KeyFactory;
    }

    public List<SubcategoryResponse> list(UUID categoryId) {
        requireCategory(categoryId);
        return subcategoryRepository.findByCategoryIdOrderBySortOrderAsc(categoryId).stream()
                .map(this::toResponse)
                .toList();
    }

    public SubcategoryResponse get(UUID categoryId, UUID subcategoryId) {
        return toResponse(getOwned(categoryId, subcategoryId));
    }

    @Transactional
    public SubcategoryResponse create(UUID categoryId, CreateSubcategoryRequest request) {
        requireCategory(categoryId);
        String code = request.code().trim().toUpperCase();
        if (subcategoryRepository.findByCategoryIdAndCode(categoryId, code).isPresent()) {
            throw ApiException.conflict("SUBCATEGORY_CODE_ALREADY_EXISTS",
                    "Já existe uma subcategoria com este código nesta categoria");
        }
        Subcategory subcategory = new Subcategory();
        subcategory.setCategoryId(categoryId);
        subcategory.setCode(code);
        subcategory.setName(request.name());
        subcategory.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        subcategory.setActive(request.active() == null || request.active());
        subcategory = subcategoryRepository.save(subcategory);
        return toResponse(subcategory);
    }

    @Transactional
    public SubcategoryResponse update(UUID categoryId, UUID subcategoryId, UpdateSubcategoryRequest request) {
        Subcategory subcategory = getOwned(categoryId, subcategoryId);
        if (request.name() != null) {
            subcategory.setName(request.name());
        }
        if (request.sortOrder() != null) {
            subcategory.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            subcategory.setActive(request.active());
        }
        return toResponse(subcategory);
    }

    @Transactional
    public void delete(UUID categoryId, UUID subcategoryId) {
        Subcategory subcategory = getOwned(categoryId, subcategoryId);
        if (productRepository.existsBySubcategoryId(subcategoryId)) {
            throw ApiException.conflict("SUBCATEGORY_IN_USE",
                    "Esta subcategoria está associada a produtos — desative-a em vez de a eliminar");
        }
        subcategoryRepository.delete(subcategory);
    }

    public PresignUploadResponse presignImageUpload(UUID categoryId, UUID subcategoryId, PresignUploadRequest request) {
        getOwned(categoryId, subcategoryId);
        String contentType = s3KeyFactory.requireValidContentType(request.contentType());
        String key = s3KeyFactory.subcategoryImageKey(subcategoryId, contentType);
        PresignedUpload upload = objectStorageService.presignUpload(key, contentType);
        return new PresignUploadResponse(upload.uploadUrl(), upload.key(), upload.expiresAt());
    }

    @Transactional
    public SubcategoryResponse confirmImageUpload(UUID categoryId, UUID subcategoryId, ConfirmUploadRequest request) {
        Subcategory subcategory = getOwned(categoryId, subcategoryId);
        s3KeyFactory.requireOwnedKey(request.key(), s3KeyFactory.subcategoryImagePrefix(subcategoryId));
        if (!objectStorageService.exists(request.key())) {
            throw ApiException.validation(List.of("key: ficheiro não encontrado — confirme após o upload terminar"));
        }
        subcategory.setImageKey(request.key());
        return toResponse(subcategory);
    }

    private void requireCategory(UUID categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw ApiException.notFound("CATEGORY_NOT_FOUND", "Categoria não encontrada");
        }
    }

    private Subcategory getOwned(UUID categoryId, UUID subcategoryId) {
        Subcategory subcategory = subcategoryRepository.findById(subcategoryId)
                .orElseThrow(() -> ApiException.notFound("SUBCATEGORY_NOT_FOUND", "Subcategoria não encontrada"));
        if (!subcategory.getCategoryId().equals(categoryId)) {
            throw ApiException.notFound("SUBCATEGORY_NOT_FOUND", "Subcategoria não encontrada");
        }
        return subcategory;
    }

    private SubcategoryResponse toResponse(Subcategory subcategory) {
        Category category = categoryRepository.findById(subcategory.getCategoryId())
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Categoria não encontrada"));
        String imageUrl = subcategory.getImageKey() == null ? null
                : objectStorageService.presignDownload(subcategory.getImageKey());
        return new SubcategoryResponse(subcategory.getId(), category.getId(), category.getCode(), category.getName(),
                subcategory.getCode(), subcategory.getName(), subcategory.getSortOrder(), subcategory.isActive(), imageUrl);
    }
}
