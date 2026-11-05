package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchaseCloseReason;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseManageUseCase {

    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final IndividualGroupPurchaseSupport individualGroupPurchaseSupport;
    private final IndividualGroupPurchaseOrderClient orderClient;
    private final DomainEventPublisher eventPublisher;

    void addProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        individualGroupPurchaseSupport.validatePurchasableProducts(List.of(productId));
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        LocalDateTime now = LocalDateTime.now();
        purchase.addProduct(memberId, productId, now);
        eventPublisher.publish(IndividualGroupPurchaseEvents.updated(purchase, now));
    }

    void removeProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        // 주문 쪽 동기 조회(조회만). 이 조회와 주문 생성 사이의 틈으로 생긴 주문은 결제 때 사본으로 다시 걸러진다(ARCH-16)
        boolean hasOrders = orderClient.hasOrders(individualGroupPurchaseId, productId);
        LocalDateTime now = LocalDateTime.now();
        purchase.removeProduct(memberId, productId, hasOrders, now);
        eventPublisher.publish(IndividualGroupPurchaseEvents.updated(purchase, now));
    }

    void changeDeadline(Long memberId, Long individualGroupPurchaseId, LocalDateTime deadlineAt) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        LocalDateTime now = LocalDateTime.now();
        purchase.changeDeadline(memberId, deadlineAt, now);
        eventPublisher.publish(IndividualGroupPurchaseEvents.updated(purchase, now));
    }

    void close(Long memberId, Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        purchase.close(memberId, LocalDateTime.now());
        publishClosed(purchase, IndividualGroupPurchaseCloseReason.CREATOR);
    }

    void closeByAdmin(Long adminId, Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        purchase.closeByAdmin(LocalDateTime.now());
        log.info("어드민 공동구매 마감: id={} admin={} creator={}", purchase.getId(), adminId, purchase.getCreatorMemberId());
        publishClosed(purchase, IndividualGroupPurchaseCloseReason.ADMIN);
    }

    // 스케줄러가 공동구매 하나마다 부른다. 행을 잠근 뒤 다시 확인하므로 그사이 연장 · 마감됐으면 건너뛴다
    void closeByDeadline(Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        if (purchase.closeByDeadline(LocalDateTime.now())) {
            publishClosed(purchase, IndividualGroupPurchaseCloseReason.DEADLINE);
        }
    }

    List<Long> findDemandIdsToCloseByDeadline(LocalDateTime now) {
        return individualGroupPurchaseRepository.findDemandIdsToCloseByDeadline(now);
    }

    private void publishClosed(IndividualGroupPurchase purchase, IndividualGroupPurchaseCloseReason reason) {
        eventPublisher.publish(IndividualGroupPurchaseEvents.closed(purchase, reason));
    }

    private IndividualGroupPurchase getPurchaseForUpdate(Long individualGroupPurchaseId) {
        return individualGroupPurchaseRepository.findByIdForUpdate(individualGroupPurchaseId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_GROUP_PURCHASE_NOT_FOUND));
    }
}
