package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.matching.DemandGroupPurchaseOpenRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDashboardResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseOrderable;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IndividualGroupPurchaseFacade {

    private final IndividualGroupPurchaseCreateUseCase individualGroupPurchaseCreateUseCase;
    private final IndividualGroupPurchaseDemandOpenUseCase individualGroupPurchaseDemandOpenUseCase;
    private final IndividualGroupPurchaseManageUseCase individualGroupPurchaseManageUseCase;
    private final IndividualGroupPurchaseQueryUseCase individualGroupPurchaseQueryUseCase;
    private final IndividualGroupPurchaseOrderReplicaUseCase individualGroupPurchaseOrderReplicaUseCase;

    @Transactional
    public IndividualGroupPurchaseCreateResponse create(Long memberId, IndividualGroupPurchaseCreateRequest request) {
        return individualGroupPurchaseCreateUseCase.create(memberId, request);
    }

    @Transactional
    public IndividualGroupPurchaseCreateResponse openFromDemand(Long adminMemberId, Long productId,
                                                                DemandGroupPurchaseOpenRequest request) {
        return individualGroupPurchaseDemandOpenUseCase.open(adminMemberId, productId, request, LocalDateTime.now());
    }

    @Transactional
    public void addProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        individualGroupPurchaseManageUseCase.addProduct(memberId, individualGroupPurchaseId, productId);
    }

    @Transactional
    public void removeProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        individualGroupPurchaseManageUseCase.removeProduct(memberId, individualGroupPurchaseId, productId);
    }

    @Transactional
    public void changeDeadline(Long memberId, Long individualGroupPurchaseId, LocalDateTime deadlineAt) {
        individualGroupPurchaseManageUseCase.changeDeadline(memberId, individualGroupPurchaseId, deadlineAt);
    }

    @Transactional
    public void close(Long memberId, Long individualGroupPurchaseId) {
        individualGroupPurchaseManageUseCase.close(memberId, individualGroupPurchaseId);
    }

    @Transactional(readOnly = true)
    public IndividualGroupPurchaseDetailResponse getByInviteCode(Long memberId, String inviteCode) {
        return individualGroupPurchaseQueryUseCase.getByInviteCode(memberId, inviteCode);
    }

    @Transactional(readOnly = true)
    public IndividualGroupPurchaseDetailResponse getDetail(Long memberId, Long individualGroupPurchaseId) {
        return individualGroupPurchaseQueryUseCase.getDetail(memberId, individualGroupPurchaseId);
    }

    @Transactional(readOnly = true)
    public IndividualGroupPurchaseDashboardResponse getDashboard(Long memberId, Long individualGroupPurchaseId) {
        return individualGroupPurchaseQueryUseCase.getDashboard(memberId, individualGroupPurchaseId);
    }

    @Transactional(readOnly = true)
    public List<IndividualGroupPurchaseSummaryResponse> getMyList(Long memberId) {
        return individualGroupPurchaseQueryUseCase.getMyList(memberId);
    }

    @Transactional
    public IndividualGroupPurchaseOrderable getOrderable(Long individualGroupPurchaseId, List<Long> productIds,
                                                         LocalDateTime now) {
        return individualGroupPurchaseQueryUseCase.getOrderable(individualGroupPurchaseId, productIds, now);
    }

    // 아래 넷은 order 이벤트로 주문 읽기 모델을 고친다 (아웃박스 릴레이 → IndividualGroupPurchaseOrderReplicaListener)
    @Transactional
    public void applyOrderCreated(OrderCreatedEvent event) {
        individualGroupPurchaseOrderReplicaUseCase.applyCreated(event);
    }

    @Transactional
    public void applyOrderPaid(OrderPaidEvent event) {
        individualGroupPurchaseOrderReplicaUseCase.applyPaid(event);
    }

    @Transactional
    public void applyOrderCanceled(OrderCanceledEvent event) {
        individualGroupPurchaseOrderReplicaUseCase.applyCanceled(event);
    }

    @Transactional
    public void applyOrderSellerItemsCanceled(OrderSellerItemsCanceledEvent event) {
        individualGroupPurchaseOrderReplicaUseCase.applySellerItemsCanceled(event);
    }
}
