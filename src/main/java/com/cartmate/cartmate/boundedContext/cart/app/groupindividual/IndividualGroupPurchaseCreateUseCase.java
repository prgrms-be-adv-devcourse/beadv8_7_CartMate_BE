package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.app.InviteCodeGenerator;
import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class IndividualGroupPurchaseCreateUseCase {

    private final IndividualGroupPurchaseRepository individualGroupPurchaseRepository;
    private final IndividualGroupPurchaseSupport individualGroupPurchaseSupport;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final DomainEventPublisher eventPublisher;

    IndividualGroupPurchaseCreateResponse create(Long memberId, IndividualGroupPurchaseCreateRequest request) {
        individualGroupPurchaseSupport.validatePurchasableProducts(request.productIds());
        String inviteCode = inviteCodeGenerator.generateUnique(individualGroupPurchaseRepository::existsByInviteCode);
        LocalDateTime now = LocalDateTime.now();
        IndividualGroupPurchase purchase = IndividualGroupPurchase.create(
                request.title(), memberId, inviteCode, request.productIds(), now);
        IndividualGroupPurchase saved = individualGroupPurchaseRepository.save(purchase);
        eventPublisher.publish(IndividualGroupPurchaseEvents.opened(saved, now));
        return IndividualGroupPurchaseCreateResponse.from(saved);
    }
}
