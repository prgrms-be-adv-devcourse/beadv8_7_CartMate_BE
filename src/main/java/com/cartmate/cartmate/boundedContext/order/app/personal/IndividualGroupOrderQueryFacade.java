package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.shared.order.IndividualGroupOrderQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// cart가 shared 포트로 부르는 동기 조회. 대시보드 주문 줄은 cart가 이벤트로 만든 읽기 모델에서 읽는다
@Service
@RequiredArgsConstructor
public class IndividualGroupOrderQueryFacade implements IndividualGroupOrderQuery {

    private final OrderQueryUseCase orderQueryUseCase;

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveOrder(Long individualGroupPurchaseId, Long productId) {
        return orderQueryUseCase.hasActiveIndividualGroupOrder(individualGroupPurchaseId, productId);
    }
}
