package com.cartmate.cartmate.boundedContext.cart.in;

import com.cartmate.cartmate.boundedContext.cart.dto.IndividualGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.IndividualGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.IndividualGroupPurchaseDashboardResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.IndividualGroupPurchaseDeadlineUpdateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.IndividualGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.IndividualGroupPurchaseProductAddRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.IndividualGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.boundedContext.cart.app.IndividualGroupPurchaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;


@RestController
@RequestMapping("/group-purchases/individual")
@RequiredArgsConstructor
public class IndividualGroupPurchaseController {

    private static final String MEMBER_ID_HEADER = "X-Member-Id";
    private static final String BASE_PATH = "/group-purchases/individual/";
    private final IndividualGroupPurchaseService individualGroupPurchaseService;


    @PostMapping
    public ResponseEntity<IndividualGroupPurchaseCreateResponse> create(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @Valid @RequestBody IndividualGroupPurchaseCreateRequest request
    ) {
        IndividualGroupPurchaseCreateResponse response = individualGroupPurchaseService.create(memberId, request);
        return ResponseEntity.created(URI.create(BASE_PATH + response.id())).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<IndividualGroupPurchaseSummaryResponse>> getMyList(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId
    ) {
        return ResponseEntity.ok(individualGroupPurchaseService.getMyList(memberId));
    }


    @GetMapping("/invite/{inviteCode}")
    public ResponseEntity<IndividualGroupPurchaseDetailResponse> getByInviteCode(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable @Pattern(regexp = "^[a-z0-9]{12}$") String inviteCode
    ) {
        return ResponseEntity.ok(individualGroupPurchaseService.getByInviteCode(memberId, inviteCode));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IndividualGroupPurchaseDetailResponse> getDetail(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(individualGroupPurchaseService.getDetail(memberId, id));
    }


    @GetMapping("/{id}/dashboard")
    public ResponseEntity<IndividualGroupPurchaseDashboardResponse> getDashboard(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(individualGroupPurchaseService.getDashboard(memberId, id));
    }


    @PostMapping("/{id}/products")
    public ResponseEntity<Void> addProduct(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody IndividualGroupPurchaseProductAddRequest request
    ) {
        individualGroupPurchaseService.addProduct(memberId, id, request.productId());
        return ResponseEntity.noContent().build();
    }


    @DeleteMapping("/{id}/products/{productId}")
    public ResponseEntity<Void> removeProduct(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id,
            @PathVariable Long productId
    ) {
        individualGroupPurchaseService.removeProduct(memberId, id, productId);
        return ResponseEntity.noContent().build();
    }


    @PatchMapping("/{id}/deadline")
    public ResponseEntity<Void> changeDeadline(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody IndividualGroupPurchaseDeadlineUpdateRequest request
    ) {
        individualGroupPurchaseService.changeDeadline(memberId, id, request.deadlineAt());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id
    ) {
        individualGroupPurchaseService.close(memberId, id);
        return ResponseEntity.noContent().build();
    }
}
