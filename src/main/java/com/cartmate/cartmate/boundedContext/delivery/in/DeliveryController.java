package com.cartmate.cartmate.boundedContext.delivery.in;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryDetailResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryReadyRequest;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerDeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryFacade deliveryFacade;

    @GetMapping("/me")
    public RsData<Page<DeliveryResponse>> getMyDeliveries(
            @AuthenticationPrincipal SecurityUser user,
            @PageableDefault(
                    size = 20,
                    sort = {"createdAt","id"},
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        Long memberId = user.memberId();

        return new RsData<>("200-1", "배송 목록 조회 성공했습니다.", deliveryFacade.getMyDeliveries(memberId, pageable));
    }

    // 주문 상세 화면용. 한 주문의 내 배송을 판매자별로 모두 돌려준다. 주문 ID는 구매 유형별로 겹칠 수 있어 유형이 필요하다.
    @GetMapping
    public RsData<List<DeliveryResponse>> getDeliveriesByOrder(
            @AuthenticationPrincipal SecurityUser user,
            @RequestParam Long orderId,
            @RequestParam PurchaseType purchaseType
    ) {
        return new RsData<>("200-1", "주문별 배송 목록 조회 성공했습니다.",
                deliveryFacade.getMyDeliveriesByOrder(user.memberId(), orderId, purchaseType));
    }

    // 판매자 배송 관리 목록. 상태 탭(배송요청/준비/중/완료)별로 조회하며 기본은 배송요청이다.
    @GetMapping("/seller")
    public RsData<Page<SellerDeliveryResponse>> getSellerDeliveries(
            @AuthenticationPrincipal SecurityUser user,
            @RequestParam(defaultValue = "REQUESTED") DeliveryStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return new RsData<>("200-1", "판매자 배송 목록 조회 성공했습니다.",
                deliveryFacade.getSellerDeliveries(user.memberId(), status, pageable));
    }

    // 판매자가 택배사와 송장번호를 등록해 배송요청을 배송준비로 처리한다.
    @PatchMapping("/{deliveryId}/ready")
    public RsData<DeliveryResponse> readyDelivery(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long deliveryId,
            @Valid @RequestBody DeliveryReadyRequest request
    ) {
        return new RsData<>("200-1", "배송 준비 처리되었습니다.",
                deliveryFacade.readyDelivery(deliveryId, user.memberId(), request.carrier(), request.trackingNumber()));
    }

    // 판매자가 배송준비 상태의 배송을 배송중으로 처리한다.
    @PatchMapping("/{deliveryId}/ship")
    public RsData<DeliveryResponse> shipDelivery(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long deliveryId
    ) {
        return new RsData<>("200-1", "배송 출발 처리되었습니다.",
                deliveryFacade.shipDelivery(deliveryId, user.memberId()));
    }

    // 수령인(구매자) 본인이 배송완료된 배송을 구매확정한다.
    @PatchMapping("/{deliveryId}/confirm")
    public RsData<DeliveryResponse> confirmDelivery(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long deliveryId
    ) {
        return new RsData<>("200-1", "구매확정 처리되었습니다.",
                deliveryFacade.confirmDelivery(deliveryId, user.memberId()));
    }

    @GetMapping("/{deliveryId}")
    public RsData<DeliveryDetailResponse> getDeliveryDetails(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long deliveryId
    ) {
        return new RsData<>("200-1", "배송 상세 조회 성공했습니다.", deliveryFacade.getDeliveryDetails(deliveryId, user.memberId()));
    }
}
