package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
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

    // 배송준비·발송은 이벤트로 들어오는 취소 판정(행 락)과 같은 배송을 다툴 수 있다. 분산락과 행 락은 서로를 기다리게 하지 못해
    // 늦게 커밋한 쪽이 배송 버전 충돌로 실패하므로, 다른 모듈(WalletRetryService)과 같이 서버가 다시 시도한다.
    // 감싸는 순서는 재시도 → 분산락 → 트랜잭션이다(DeliveryFacade). 재시도마다 락을 다시 잡고 새 트랜잭션에서 최신 값을 읽으므로,
    // 그 사이 배송이 취소됐으면 "취소된 배송이라 준비·발송할 수 없다"는 업무 오류를 받는다. 3번 다 실패하면 지금은 500으로 나간다.
    @Retryable(
            value = {
                    OptimisticLockingFailureException.class, // 낙관적 락 충돌
                    CannotAcquireLockException.class    // DB 락 획득 실패(데드락 등)
            },
            maxRetries = 3,
            delay = 100,
            multiplier = 2,
            jitter = 50,
            maxDelay = 1000
    )
    public DeliveryResponse readyDelivery(Long deliveryId, Long memberId, String carrier, String trackingNumber) {
        return deliveryFacade.readyDelivery(deliveryId, memberId, carrier, trackingNumber);
    }

    @Retryable(
            value = {
                    OptimisticLockingFailureException.class,
                    CannotAcquireLockException.class
            },
            maxRetries = 3,
            delay = 100,
            multiplier = 2,
            jitter = 50,
            maxDelay = 1000
    )
    public DeliveryResponse shipDelivery(Long deliveryId, Long memberId) {
        return deliveryFacade.shipDelivery(deliveryId, memberId);
    }
}
