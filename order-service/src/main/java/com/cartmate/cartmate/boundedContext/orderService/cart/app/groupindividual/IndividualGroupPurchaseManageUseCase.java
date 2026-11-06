package com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseCloseReason;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseOrderClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
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
    private final Clock clock;

    void addProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        individualGroupPurchaseSupport.validatePurchasableProducts(List.of(productId));
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        LocalDateTime now = LocalDateTime.now(clock);
        purchase.addProduct(memberId, productId, now);
        eventPublisher.publish(IndividualGroupPurchaseEvents.updated(purchase, now));
    }

    void removeProduct(Long memberId, Long individualGroupPurchaseId, Long productId) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        boolean hasOrders = orderClient.hasOrders(individualGroupPurchaseId, productId);
        LocalDateTime now = LocalDateTime.now(clock);
        purchase.removeProduct(memberId, productId, hasOrders, now);
        eventPublisher.publish(IndividualGroupPurchaseEvents.updated(purchase, now));
    }

    void changeDeadline(Long memberId, Long individualGroupPurchaseId, LocalDateTime deadlineAt) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        LocalDateTime now = LocalDateTime.now(clock);
        purchase.changeDeadline(memberId, deadlineAt, now);
        eventPublisher.publish(IndividualGroupPurchaseEvents.updated(purchase, now));
    }

    void close(Long memberId, Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        purchase.close(memberId, LocalDateTime.now(clock));
        publishClosed(purchase, IndividualGroupPurchaseCloseReason.CREATOR);
    }

    void closeByAdmin(Long adminId, Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        purchase.closeByAdmin(LocalDateTime.now(clock));
        log.info("어드민 공동구매 마감: id={} admin={} creator={}", purchase.getId(), adminId, purchase.getCreatorMemberId());
        publishClosed(purchase, IndividualGroupPurchaseCloseReason.ADMIN);
    }

    void closeByDeadline(Long individualGroupPurchaseId) {
        IndividualGroupPurchase purchase = getPurchaseForUpdate(individualGroupPurchaseId);
        if (purchase.closeByDeadline(LocalDateTime.now(clock))) {
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
