package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import org.springframework.beans.factory.annotation.Value;
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
                    .uri("/register")
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
                    .uri("/posts/{postId}", postId)
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
}
