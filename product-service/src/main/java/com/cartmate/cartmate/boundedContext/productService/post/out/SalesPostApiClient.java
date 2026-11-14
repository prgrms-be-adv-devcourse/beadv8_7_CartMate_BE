package com.cartmate.cartmate.boundedContext.productService.post.out;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSnapshot;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectProductResponse;
import com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CompleteProductUpdateRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.RestoreProduct;
import com.cartmate.cartmate.boundedContext.productService.post.dto.RestoreProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.RestoreInventory;
import com.cartmate.cartmate.boundedContext.productService.post.dto.RestoreProductsRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProduct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.ProductStatusChangeRequest;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Service
public class SalesPostApiClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;

    public SalesPostApiClient(@Value("${product.global.internalUrl}") String internalUrl) {
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory())
                .baseUrl(internalUrl + "/api/products")
                .build();
    }

    private static ClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    // product가 저장한 상품(사진 · 옵션 포함)을 돌려받는다. post는 이걸로 마무리 트랜잭션에서 복제본을 만든다
    public List<SyncProduct> registerProducts(CreateProductRequest request, List<MultipartFile> images) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("request", request, MediaType.APPLICATION_JSON);   // file은 빠진 채로 JSON

        if (images != null) {
            images.forEach(file -> body.part("images", file.getResource()));
        }

        try {
            InternalResponse<List<SyncProduct>> response = restClient.post()
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
                    .body(new ParameterizedTypeReference<>() {});
            return response == null ? List.of() : response.data();
        } catch (ResourceAccessException e) {   // 타임아웃, 연결 실패
            throw new ServiceException(ProductErrorCode.PRODUCT_CONNECT_FAILED);
        }
    }

    // 판매글 단위로 상품 판매 상태 변경 (판매자 내림·재개, 관리자 숨김·해제)
    public void changeProductStatus(Long postId, SalesPostProductStatus status) {
        try {
            restClient.patch()
                    .uri("/{postId}/status", postId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ProductStatusChangeRequest(status))
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

    // product가 수정한 상품(사진 · 옵션 포함)을 돌려받는다. post는 이걸로 수정 마무리 트랜잭션에서 복제본을 갱신한다
    public List<SyncProduct> updateProducts(UpdateSalesPostRequest request, List<MultipartFile> images) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("request", request, MediaType.APPLICATION_JSON);   // file은 빠진 채로 JSON

        if (images != null) {
            images.forEach(file -> body.part("images", file.getResource()));
        }

        try {
            InternalResponse<List<SyncProduct>> response = restClient.patch()
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
                    .body(new ParameterizedTypeReference<>() {});
            return response == null ? List.of() : response.data();
        } catch (ResourceAccessException e) {   // 타임아웃, 연결 실패
            throw new ServiceException(ProductErrorCode.PRODUCT_CONNECT_FAILED);
        }
    }

    public List<SelectProductResponse> getProduct(Long postId) {
        try {
            // RsData는 응답을 만드는 용도라 JSON에서 다시 만들 수 없다(final 필드, 생성자 표시 없음). 읽을 때는 같은 모양의 record로 읽는다
            InternalResponse<List<SelectProductResponse>> response = restClient.get()
                    .uri("/{postId}", postId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_SELECT_INVALID);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_SELECT_FAILED);
                    })
                    .body(new ParameterizedTypeReference<>() {});
            return response == null ? List.of() : response.data();
        } catch (ResourceAccessException e) {   // 타임아웃, 연결 실패
            throw new ServiceException(ProductErrorCode.PRODUCT_CONNECT_FAILED);
        }
    }

    // 판매글 수정 끝: 수정 중(UPDATING)인 상품을 스냅샷의 수정 전 상태로 되돌리게 한다.
    // 실패하면(어떤 이유든) 수정 실패로 보고 post가 스냅샷 복구를 한다 → 복구도 상태를 되돌린다
    public void completeProductUpdate(Long postId, SalesPostSnapshot snapshot) {
        CompleteProductUpdateRequest request = new CompleteProductUpdateRequest(snapshot.products().stream()
                .map(p -> new CompleteProductUpdateRequest.Item(p.id(), p.status()))
                .toList());
        try {
            restClient.put()
                    .uri("/{postId}/update-complete", postId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new ServiceException(ProductErrorCode.PRODUCT_UPDATE_FAILED);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {   // 타임아웃, 연결 실패
            throw new ServiceException(ProductErrorCode.PRODUCT_CONNECT_FAILED);
        }
    }

    // product가 되돌린 상품을 돌려받는다. post는 이걸로 복구 성공 트랜잭션에서 복제본도 되돌린다
    public List<SyncProduct> restoreProducts(Long postId, SalesPostSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalStateException("복구할 스냅샷이 없습니다. postId=" + postId);
        }

        try {
            InternalResponse<List<SyncProduct>> response = restClient.put()
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
                    .body(new ParameterizedTypeReference<>() {});
            return response == null ? List.of() : response.data();
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
                        toRestoreInventories(p.inventories()),
                        p.status()   // 수정 전 판매 상태로 되돌린다(수정 중 UPDATING 해제)
                ))
                .toList();
        return new RestoreProductsRequest(productList);
    }

    // 재고가 없는 스냅샷(재고 도입 전에 저장된 것)은 null로 보내 상품 쪽이 재고를 건드리지 않게 한다
    private List<RestoreInventory> toRestoreInventories(List<SalesPostSnapshot.InventorySnapshot> inventories) {
        if (inventories == null || inventories.isEmpty()) {
            return null;
        }
        return inventories.stream()
                .map(i -> new RestoreInventory(i.inventoryId(), i.additionalPrice(), i.stockQuantity()))
                .toList();
    }
}
