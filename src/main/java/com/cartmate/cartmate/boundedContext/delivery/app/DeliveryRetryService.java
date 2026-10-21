package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

// 호출하는 쪽에 트랜잭션이 없어야 한다. 이미 트랜잭션 안이면 unique 위반으로 해당 트랜잭션이
// rollback-only가 되어 재시도가 실패한다. 이벤트로 호출할 때는 커밋 이후, 새 트랜잭션에서 호출한다.
@Service
@RequiredArgsConstructor
public class DeliveryRetryService {

    private final DeliveryFacade deliveryFacade;
    private final DeliveryRepository deliveryRepository;

    // 동시 요청이 모두 기존 배송을 찾지 못해 생성 단계로 진입한 경우,
    // 유니크 제약으로 실패한 요청도 먼저 생성된 배송을 검증해 처리한다.
    public void createDelivery(DeliveryCreateCommand createCommand) {
        try {
            // 정상 처리되면 신규 생성이거나 같은 요청의 재전송이므로 바로 종료한다.
            deliveryFacade.createDelivery(createCommand);
            return;
        } catch (DataIntegrityViolationException e) {
            // 같은 키의 배송이 없으면 다른 DB 제약 오류이므로 원래 예외를 전달한다.
            if (!deliveryRepository.existsByIdempotencyKey(createCommand.idempotencyKey())) {
                throw e;
            }
        }

        // 같은 키의 배송이 있으면 새 트랜잭션에서 요청 내용을 비교한다.
        // 같은 요청이면 정상 종료하고, 내용이 다르면 멱등키 충돌로 실패한다.
        deliveryFacade.createDelivery(createCommand);
    }
}
