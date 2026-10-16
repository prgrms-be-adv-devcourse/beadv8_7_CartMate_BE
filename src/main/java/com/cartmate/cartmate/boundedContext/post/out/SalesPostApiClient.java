package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SalesPostApiClient {
    private final RestClient restClient;

    public SalesPostApiClient(@Value("${product.global.internalUrl}") String internalUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(internalUrl + "/api/products")
                .build();
    }

    public RsData<Void> publishProducts(CreateProductRequest request) {
        return restClient.post()
                .uri("/product")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

}
