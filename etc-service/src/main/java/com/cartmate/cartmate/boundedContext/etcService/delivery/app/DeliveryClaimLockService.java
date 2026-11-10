package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.SellerClaimResponse;
import com.cartmate.cartmate.global.lock.DistributedLockExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 클레임 승인·거절·완료를 배송 키 분산락 안에서 실행한다(ADR 0002·0009).
// 락 키에 배송 ID가 필요해서 먼저 클레임의 배송 ID를 읽고(락 밖, 짧은 읽기 트랜잭션), 락을 잡은 뒤 처리 트랜잭션을 연다.
// 클레임의 배송은 바뀌지 않으므로 락 전에 읽은 배송 ID로 키를 만들어도 된다. 상태 판단은 락 안의 트랜잭션에서 다시 읽어서 한다.
// 이 클래스에는 트랜잭션을 걸지 않는다. 걸면 락이 트랜잭션 안쪽에서 풀려 커밋 전에 다른 요청이 들어온다.
@Service
@RequiredArgsConstructor
public class DeliveryClaimLockService {

    private final DeliveryFacade deliveryFacade;
    private final DistributedLockExecutor lockExecutor;

    public SellerClaimResponse approve(Long claimId, Long memberId) {
        return lockExecutor.get(lockKey(claimId), () -> deliveryFacade.approveClaim(claimId, memberId));
    }

    public SellerClaimResponse reject(Long claimId, Long memberId, String reason) {
        return lockExecutor.get(lockKey(claimId), () -> deliveryFacade.rejectClaim(claimId, memberId, reason));
    }

    public SellerClaimResponse complete(Long claimId, Long memberId) {
        return lockExecutor.get(lockKey(claimId), () -> deliveryFacade.completeClaim(claimId, memberId));
    }

    private String lockKey(Long claimId) {
        return DeliveryFacade.LOCK_KEY_PREFIX + deliveryFacade.getClaimDeliveryId(claimId);
    }
}
