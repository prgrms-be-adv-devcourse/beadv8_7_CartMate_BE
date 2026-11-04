package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchaseCloseReason;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
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
        getPurchaseForUpdate(individualGroupPurchaseId)
                .addProduct(memberId, productId, LocalDateTime.now());
    }

    void removeProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        boolean hasOrders = orderClient.hasOrders(individualGroupPurchaseId, productId);
        purchase.removeProduct(memberId, productId, hasOrders, LocalDateTime.now());
    }

    void changeDeadline(Long memberId, Long individualGroupPurchaseId, LocalDateTime deadlineAt) {
        getPurchaseForUpdate(individualGroupPurchaseId)
                .changeDeadline(memberId, deadlineAt, LocalDateTime.now());
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
        eventPublisher.publish(new IndividualGroupPurchaseClosedEvent(
                purchase.getId(), purchase.getOrigin().name(), reason.name(), purchase.getDeadlineAt(),
                purchase.getClosedAt()));
    }

    private IndividualGroupPurchase getPurchaseForUpdate(Long individualGroupPurchaseId) {
        return individualGroupPurchaseRepository.findByIdForUpdate(individualGroupPurchaseId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_GROUP_PURCHASE_NOT_FOUND));
    }
}
