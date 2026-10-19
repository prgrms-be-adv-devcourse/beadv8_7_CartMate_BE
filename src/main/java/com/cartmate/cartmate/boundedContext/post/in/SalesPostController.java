package com.cartmate.cartmate.boundedContext.post.in;

import com.cartmate.cartmate.boundedContext.post.app.SalesPostFacade;
import com.cartmate.cartmate.boundedContext.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostRequest;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostResponse;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sales-posts")
public class SalesPostController {
    private final SalesPostFacade salesPostFacade;


    @PostMapping("/post")
    public ResponseEntity<Void> registerSalesPost(
            @RequestPart("request") CreateSalesPostRequest request,
            @RequestPart("images") List<MultipartFile> images) {
        salesPostFacade.registerSalesPost(request, images);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<RsData<PageResponse<SelectSalesPostResponse>>> selectSalesPostAll(
            SelectSalesPostRequest request,
            Pageable pageable) {
        RsData<PageResponse<SelectSalesPostResponse>> result = salesPostFacade.selectSalesPostAll(request, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/detail")
    public ResponseEntity<RsData<SelectSalesPostResponse>> selectSalesPostDetail(Long postId) {
        RsData<SelectSalesPostResponse> result = salesPostFacade.selectSalesPostDetail(postId);
        return ResponseEntity.ok(result);
    }

}
