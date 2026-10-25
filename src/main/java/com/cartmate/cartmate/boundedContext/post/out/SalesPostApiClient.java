package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSnapshot;
import com.cartmate.cartmate.boundedContext.post.dto.SelectProductResponse;
import com.cartmate.cartmate.boundedContext.post.dto.UpdateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import com.cartmate.cartmate.shared.product.dto.RestoreProduct;
import com.cartmate.cartmate.shared.product.dto.RestoreProductImage;
import com.cartmate.cartmate.shared.product.dto.RestoreProductOption;
import com.cartmate.cartmate.shared.product.dto.RestoreProductsRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class SalesPostApiClient {
    private final RestClient restClient;

    public SalesPostApiClient(@Value("${product.global.internalUrl}") String internalUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(internalUrl + "/api/products")
                .build();
    }

    public void registerProducts(CreateProductRequest request, List<MultipartFile> images) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("request", request, MediaType.APPLICATION_JSON);   // file은 빠진 채로 JSON

        if (images != null) {
            images.forEach(file -> body.part("images", file.getResource()));
        }

        try {
            restClient.post()
                    .uri("")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_CREATE_INVALID);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_CREATE_FAILED);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {   // 타임아웃, 연결 실패
            throw new ServiceException(ProductErrorCode.PRODUCT_CONNECT_FAILED);
        }
    }

    public void deleteProducts(Long postId) {
        try {
            restClient.delete()
                    .uri("/{postId}", postId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_DELETE_INVALID);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_DELETE_FAILED);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {   // 타임아웃, 연결 실패
            throw new ServiceException(ProductErrorCode.PRODUCT_CONNECT_FAILED);
        }
    }

    public void updateProducts(UpdateSalesPostRequest request, List<MultipartFile> images) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("request", request, MediaType.APPLICATION_JSON);   // file은 빠진 채로 JSON

        if (images != null) {
            images.forEach(file -> body.part("images", file.getResource()));
        }

        try {
            restClient.patch()
                    .uri("")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_UPDATE_INVALID);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_UPDATE_FAILED);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {   // 타임아웃, 연결 실패
            throw new ServiceException(ProductErrorCode.PRODUCT_CONNECT_FAILED);
        }
    }

    public List<SelectProductResponse> getProduct(Long postId) {
        try {
            RsData<List<SelectProductResponse>> response = restClient.get()
                    .uri("/{postId}", postId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_SELECT_INVALID);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_SELECT_FAILED);
                    })
                    .body(new ParameterizedTypeReference<>() {});
            return response == null ? List.of() : response.getData();
        } catch (ResourceAccessException e) {   // 타임아웃, 연결 실패
            throw new ServiceException(ProductErrorCode.PRODUCT_CONNECT_FAILED);
        }
    }

    public void restoreProducts(Long postId, SalesPostSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalStateException("복구할 스냅샷이 없습니다. postId=" + postId);
        }

        try {
            restClient.put()
                    .uri("/{postId}/restore", postId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toRestoreRequest(snapshot))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_RESTORE_INVALID);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_RESTORE_FAILED);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {   // 타임아웃, 연결 실패
            throw new ServiceException(ProductErrorCode.PRODUCT_CONNECT_FAILED);
        }
    }

    private RestoreProductsRequest toRestoreRequest(SalesPostSnapshot snapshot) {
        List<RestoreProduct> productList = snapshot.products().stream()
                .map(p -> new RestoreProduct(
                        p.id(),
                        p.categoryId(),
                        p.name(),
                        p.description(),
                        p.basePrice(),
                        p.images().stream()
                                .map(i -> new RestoreProductImage(i.imageId(), i.sortOrder()))
                                .toList(),
                        p.options().stream()
                                .map(o -> new RestoreProductOption(o.productTypeId(), o.productOptionId()))
                                .toList()
                ))
                .toList();
        return new RestoreProductsRequest(productList);
    }
}
