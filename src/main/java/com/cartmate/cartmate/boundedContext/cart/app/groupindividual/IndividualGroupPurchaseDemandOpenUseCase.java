package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.app.InviteCodeGenerator;
import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.matching.DemandGroupPurchaseOpenRequest;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.event.DemandGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseDemandOpenUseCase {

    private static final String DEMAND_PRODUCT_KEY_CONSTRAINT = "uk_individual_group_purchase_demand_product";

    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final IndividualGroupPurchaseSupport individualGroupPurchaseSupport;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final DomainEventPublisher domainEventPublisher;

    IndividualGroupPurchaseCreateResponse open(Long adminMemberId, Long productId,
                                               DemandGroupPurchaseOpenRequest request, LocalDateTime now) {
        individualGroupPurchaseSupport.validatePurchasableProducts(List.of(productId));
        individualGroupPurchaseRepository.findByDemandProductKeyForUpdate(productId)
                .ifPresent(previous -> {
                    previous.releaseDemandProductKey(now);
                    individualGroupPurchaseRepository.flush();
                });

        String inviteCode = inviteCodeGenerator.generateUnique(individualGroupPurchaseRepository::existsByInviteCode);
        IndividualGroupPurchase purchase = IndividualGroupPurchase.openFromDemand(
                request.title(), adminMemberId, inviteCode, productId, request.deadlineAt(),
                Objects.requireNonNullElse(request.paybackMinBuyers(), IndividualGroupPurchase.DEFAULT_PAYBACK_MIN_BUYERS),
                Objects.requireNonNullElse(request.paybackAmount(), IndividualGroupPurchase.DEFAULT_PAYBACK_AMOUNT),
                now);

        IndividualGroupPurchase saved = save(purchase);
        domainEventPublisher.publish(IndividualGroupPurchaseEvents.opened(saved, now));
        eventPublisher.publishEvent(new DemandGroupPurchaseOpenedEvent(
                saved.getId(), productId, saved.getTitle(), saved.getInviteCode(), saved.getDeadlineAt(),
                saved.getPaybackMinBuyers(), saved.getPaybackAmount(), now));
        return IndividualGroupPurchaseCreateResponse.from(saved);
    }

    private IndividualGroupPurchase save(IndividualGroupPurchase purchase) {
        try {
            return individualGroupPurchaseRepository.saveAndFlush(purchase);
        } catch (DataIntegrityViolationException e) {
            if (isDemandProductKeyViolation(e)) {
                throw new ServiceException(CartErrorCode.CART_DEMAND_ALREADY_OPEN);
            }
            throw e;
        } catch (PessimisticLockingFailureException e) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_ALREADY_OPEN);
        }
    }

    private static boolean isDemandProductKeyViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(DEMAND_PRODUCT_KEY_CONSTRAINT);
    }
}
