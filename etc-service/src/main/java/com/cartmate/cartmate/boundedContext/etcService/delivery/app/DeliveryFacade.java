package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.DeliveryClaimCreateCommand;
import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.DeliveryDetailResponse;
import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.SellerClaimResponse;
import com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto.SellerDeliveryResponse;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.global.lock.DistributedLock;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryFacade {

    // 사용자 요청·배치로 배송을 바꾸는 경로는 배송 키 분산락을 잡는다(ADR 0002·0009).
    // 락은 트랜잭션 바깥에서 잡혀 커밋 뒤에 풀린다(DistributedLockAspect의 순서).
    // 이벤트로 들어오는 경로(배송 생성, 취소 판정, 환불 결과)는 Inbox 트랜잭션 안이라 행 락을 그대로 쓴다.
    public static final String LOCK_KEY_PREFIX = "delivery:";

    private final DeliveryCreateUseCase deliveryCreateUseCase;
    private final DeliveryQueryUseCase deliveryQueryUseCase;
    private final DeliveryReadyUseCase deliveryReadyUseCase;
    private final DeliveryCancelUseCase deliveryCancelUseCase;
    private final DeliveryShipUseCase deliveryShipUseCase;
    private final DeliveryDeliverUseCase deliveryDeliverUseCase;
    private final DeliveryConfirmUseCase deliveryConfirmUseCase;
    private final DeliveryAutoConfirmUseCase deliveryAutoConfirmUseCase;
    private final DeliveryClaimCreateUseCase deliveryClaimCreateUseCase;
    private final DeliveryClaimQueryUseCase deliveryClaimQueryUseCase;
    private final DeliveryClaimApproveUseCase deliveryClaimApproveUseCase;
    private final DeliveryClaimRejectUseCase deliveryClaimRejectUseCase;
    private final DeliveryClaimCompleteUseCase deliveryClaimCompleteUseCase;
    private final DeliveryClaimRefundResultUseCase deliveryClaimRefundResultUseCase;
    private final DeliveryWithdrawalQueryUseCase deliveryWithdrawalQueryUseCase;

    @Transactional
    public void createDelivery(DeliveryCreateCommand createCommand) {
        deliveryCreateUseCase.create(createCommand);
    }

    @Transactional
    public void cancelByBuyerRequest(Long orderId, int requestSeq, LocalDateTime requestedAt) {
        deliveryCancelUseCase.cancelByBuyerRequest(orderId, requestSeq, requestedAt);
    }

    @Transactional
    public void cancelBySalesStop(PurchaseType purchaseType, Long orderId, Long sellerId) {
        deliveryCancelUseCase.cancelBySalesStop(purchaseType, orderId, sellerId);
    }

    @DistributedLock(key = "'delivery:' + #deliveryId")
    @Transactional
    public DeliveryResponse readyDelivery(Long deliveryId, Long memberId, String carrier, String trackingNumber) {
        return deliveryReadyUseCase.ready(deliveryId, memberId, carrier, trackingNumber);
    }

    @DistributedLock(key = "'delivery:' + #deliveryId")
    @Transactional
    public DeliveryResponse shipDelivery(Long deliveryId, Long memberId) {
        return deliveryShipUseCase.ship(deliveryId, memberId);
    }

    @DistributedLock(key = "'delivery:' + #deliveryId")
    @Transactional
    public DeliveryResponse confirmDelivery(Long deliveryId, Long memberId) {
        return deliveryConfirmUseCase.confirm(deliveryId, memberId);
    }

    @DistributedLock(key = "'delivery:' + #command.deliveryId()")
    @Transactional
    public DeliveryClaimResponse createClaim(DeliveryClaimCreateCommand command) {
        return deliveryClaimCreateUseCase.create(command);
    }

    @DistributedLock(key = "'delivery:' + #deliveryId")
    @Transactional
    public void autoConfirmDelivery(Long deliveryId) {
        deliveryAutoConfirmUseCase.confirm(deliveryId);
    }

    @Transactional
    public void deliverDelivery(Long deliveryId) {
        deliveryDeliverUseCase.deliver(deliveryId);
    }

    // 클레임 승인·거절·완료는 클레임의 배송 키로 잠근다. 락을 잡기 전에 배송 ID를 알아야 해서
    // DeliveryClaimLockService가 이 조회 → 락 → 아래 메서드 순서로 부른다.
    @Transactional(readOnly = true)
    public Long getClaimDeliveryId(Long claimId) {
        return deliveryClaimQueryUseCase.getDeliveryId(claimId);
    }

    @Transactional
    public SellerClaimResponse approveClaim(Long claimId, Long memberId) {
        return deliveryClaimApproveUseCase.approve(claimId, memberId);
    }

    @Transactional
    public SellerClaimResponse rejectClaim(Long claimId, Long memberId, String reason) {
        return deliveryClaimRejectUseCase.reject(claimId, memberId, reason);
    }

    @Transactional
    public SellerClaimResponse completeClaim(Long claimId, Long memberId) {
        return deliveryClaimCompleteUseCase.complete(claimId, memberId);
    }

    @Transactional
    public void completeClaimRefund(Long claimId) {
        deliveryClaimRefundResultUseCase.complete(claimId);
    }

    @Transactional
    public void failClaimRefund(Long claimId) {
        deliveryClaimRefundResultUseCase.fail(claimId);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryClaimResponse> getMyClaims(Long memberId, Pageable pageable) {
        return deliveryClaimQueryUseCase.getMyClaims(memberId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SellerClaimResponse> getSellerClaims(
            Long memberId, List<ClaimStatus> statuses, ClaimType claimType, Pageable pageable
    ) {
        return deliveryClaimQueryUseCase.getSellerClaims(memberId, statuses, claimType, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryResponse> getMyDeliveries(Long memberId, Pageable pageable) {
        return deliveryQueryUseCase.getMyDeliveries(memberId, pageable);
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getMyDeliveriesByOrder(Long memberId, Long orderId, PurchaseType purchaseType) {
        return deliveryQueryUseCase.getMyDeliveriesByOrder(memberId, orderId, purchaseType);
    }

    @Transactional(readOnly = true)
    public Page<SellerDeliveryResponse> getSellerDeliveries(Long memberId, DeliveryStatus status, Pageable pageable) {
        return deliveryQueryUseCase.getSellerDeliveries(memberId, status, pageable);
    }

    @Transactional(readOnly = true)
    public DeliveryDetailResponse getDeliveryDetails(Long deliveryId, Long memberId) {
        return deliveryQueryUseCase.getDeliveryDetails(deliveryId, memberId);
    }

    // ===== 회원 탈퇴 확인 =====
    @Transactional(readOnly = true)
    public long countUnfinishedOfBuyer(Long memberId) {
        return deliveryWithdrawalQueryUseCase.countUnfinishedOfBuyer(memberId);
    }

    @Transactional(readOnly = true)
    public long countUnfinishedOfSharedOrders(List<Long> sharedGroupOrderIds) {
        return deliveryWithdrawalQueryUseCase.countUnfinishedOfSharedOrders(sharedGroupOrderIds);
    }

    @Transactional(readOnly = true)
    public long countUnfinishedOfSeller(Long sellerId) {
                return deliveryWithdrawalQueryUseCase.countUnfinishedOfSeller(sellerId);
    }
    @Transactional(readOnly = true)
    public long countClaimsInProgressOfBuyer(Long memberId) {
        return deliveryWithdrawalQueryUseCase.countClaimsInProgressOfBuyer(memberId);
    }

    @Transactional(readOnly = true)
    public long countClaimsInProgressOfSeller(Long sellerId) {
        return deliveryWithdrawalQueryUseCase.countClaimsInProgressOfSeller(sellerId);
    }
}
