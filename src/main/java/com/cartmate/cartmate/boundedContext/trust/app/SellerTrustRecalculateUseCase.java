package com.cartmate.cartmate.boundedContext.trust.app;

import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustScore;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustEvaluation;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustLevel;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustMetrics;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustScorePolicy;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustScoreRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.shared.trust.event.SellerTrustScoreChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SellerTrustRecalculateUseCase {

    private final SellerTrustScoreRepository sellerTrustScoreRepository;
    private final EventPublisher eventPublisher;

    // 판매자 한 명의 집계값으로 점수와 등급을 다시 계산해 저장한다. 점수 행이 없으면 새로 만들고 있으면 덮어쓴다.
    // 집계 건수와 점수·등급이 모두 그대로이면 아무것도 쓰지 않아, 매일 전체 판매자를 돌려도 달라진 판매자만 저장된다.
    // 점수나 등급이 바뀌었을 때만 변경 이벤트를 발행한다. 집계 건수만 바뀐 경우에는 저장만 하고 이벤트는 내지 않는다.
    public void recalculate(Long sellerId, TrustMetrics metrics) {
        TrustEvaluation evaluation = TrustScorePolicy.evaluate(metrics);
        LocalDateTime calculatedAt = LocalDateTime.now();

        SellerTrustScore trustScore = sellerTrustScoreRepository.findBySellerId(sellerId).orElse(null);
        if (trustScore == null) {
            sellerTrustScoreRepository.save(SellerTrustScore.create(sellerId, metrics, evaluation, calculatedAt));
            // 점수 행이 없던 판매자는 이미 점수 없는 NEW로 보이므로, 처음부터 NEW이면 바뀐 것이 없다.
            if (evaluation.level() != TrustLevel.NEW) {
                publishChanged(sellerId, evaluation, calculatedAt);
            }
            return;
        }

        if (!trustScore.isChanged(metrics, evaluation)) {
            return;
        }

        boolean scoreChanged = trustScore.isChanged(evaluation);
        trustScore.recalculate(metrics, evaluation, calculatedAt);
        if (scoreChanged) {
            publishChanged(sellerId, evaluation, calculatedAt);
        }
    }

    private void publishChanged(Long sellerId, TrustEvaluation evaluation, LocalDateTime calculatedAt) {
        eventPublisher.publish(new SellerTrustScoreChangedEvent(
                sellerId, evaluation.score(), evaluation.level().name(), calculatedAt));
    }
}
