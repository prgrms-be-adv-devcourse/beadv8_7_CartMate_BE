package com.cartmate.cartmate.boundedContext.post.in;

import com.cartmate.cartmate.boundedContext.post.app.SalesPostFacade;
import com.cartmate.cartmate.boundedContext.post.dto.create.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.dto.create.CreateSalesPostResponse;
import com.cartmate.cartmate.global.code.SuccessCode;
import com.cartmate.cartmate.global.response.ResponseData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sales-posts")
public class SalesPostController {
    private final SalesPostFacade salesPostFacade;


    @PostMapping("/post")
    public ResponseEntity<ResponseData<CreateSalesPostResponse>> registerSalesPost(CreateSalesPostRequest request) {
        ResponseData<CreateSalesPostResponse> response = salesPostFacade.registerSalesPost(request);
        return ResponseEntity
                .status(SuccessCode.POST_CREATED.getStatus())
                .body(response);
    }
}
