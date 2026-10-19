package com.cartmate.cartmate.boundedContext.order.in.personal;

import com.cartmate.cartmate.boundedContext.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderCreateRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderCreateResponse;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderDetailResponse;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderSummaryResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<OrderCreateResponse> createPersonal(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        return new RsData<>("201-1", "주문을 만들었습니다.", orderFacade.createPersonal(user.memberId(), request));
    }

    @PostMapping("/individual-group")
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<OrderCreateResponse> createIndividualGroup(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody IndividualGroupOrderCreateRequest request
    ) {
        return new RsData<>("201-1", "공동구매 상품을 주문했습니다.", orderFacade.createIndividualGroup(user.memberId(), request));
    }

    @GetMapping("/me")
    public RsData<List<OrderSummaryResponse>> getMyList(@AuthenticationPrincipal SecurityUser user) {
        return new RsData<>("200-1", "내 주문 목록 조회 성공했습니다.", orderFacade.getMyList(user.memberId()));
    }

    @GetMapping("/{id}")
    public RsData<OrderDetailResponse> getDetail(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return new RsData<>("200-1", "주문 조회 성공했습니다.", orderFacade.getDetail(user.memberId(), id));
    }
}
