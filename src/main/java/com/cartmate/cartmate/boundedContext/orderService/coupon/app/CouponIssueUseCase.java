package com.cartmate.cartmate.boundedContext.orderService.coupon.app;

import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.Coupon;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponAutoTrigger;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponIssueType;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponStatus;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.MemberCoupon;
import com.cartmate.cartmate.boundedContext.orderService.coupon.exception.CouponErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.coupon.out.CouponRepository;
import com.cartmate.cartmate.boundedContext.orderService.coupon.out.MemberCouponRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * 쿠폰 발급: 회원이 다운로드하거나, 조건(가입)을 채우면 자동 발급.
 *
 * <p>같은 쿠폰은 회원당 한 장이다. 이미 있으면 다운로드는 "이미 받음", 자동 발급은 건너뛴다(같은 가입 이벤트가 두 번 와도 한 장).
 * 수량은 조건부 UPDATE로 올려서, 여러 명이 동시에 받아도 총 수량을 넘지 않는다.
 */
@Slf4j
@Service
class CouponIssueUseCase {

    private static final int MYSQL_DUPLICATE_KEY = 1062;

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final TransactionTemplate perCouponTransaction;

    CouponIssueUseCase(CouponRepository couponRepository, MemberCouponRepository memberCouponRepository,
                       PlatformTransactionManager transactionManager) {
        this.couponRepository = couponRepository;
        this.memberCouponRepository = memberCouponRepository;
        // 자동 발급은 쿠폰 한 장마다 새 트랜잭션: 한 장이 실패하면 그 쿠폰의 수량 증가와 저장만 롤백되고 나머지는 발급된다
        this.perCouponTransaction = new TransactionTemplate(transactionManager);
        this.perCouponTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    void download(Long couponId, Long memberId, LocalDateTime now) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ServiceException(CouponErrorCode.COUPON_NOT_FOUND));
        if (coupon.getIssueType() != CouponIssueType.DOWNLOAD || !coupon.isIssuable(now)) {
            throw new ServiceException(coupon.isSoldOut() ? CouponErrorCode.COUPON_SOLD_OUT : CouponErrorCode.COUPON_NOT_DOWNLOADABLE);
        }
        if (memberCouponRepository.existsByCouponIdAndMemberId(couponId, memberId)) {
            throw new ServiceException(CouponErrorCode.COUPON_ALREADY_ISSUED);
        }
        issue(coupon, memberId, now);
    }

    // 가입 쿠폰 자동 발급. 하나가 실패해도 다른 쿠폰은 계속 발급한다 (로그만 남김)
    // 가입 쿠폰 자동 발급. 쿠폰마다 트랜잭션을 따로 둬서, 한 장이 실패해도(소진·동시 중복) 다른 쿠폰은 발급된다
    int issueOnSignup(Long memberId, LocalDateTime now) {
        int issued = 0;
        for (Coupon coupon : couponRepository.findAllByIssueTypeAndAutoTriggerAndStatus(
                CouponIssueType.AUTO, CouponAutoTrigger.SIGNUP, CouponStatus.ACTIVE)) {
            if (!coupon.isIssuable(now) || memberCouponRepository.existsByCouponIdAndMemberId(coupon.getId(), memberId)) {
                continue;
            }
            try {
                perCouponTransaction.executeWithoutResult(status -> issue(coupon, memberId, now));
                issued++;
            } catch (RuntimeException e) {
                log.warn("가입 쿠폰 자동 발급 건너뜀: 쿠폰 #{} 회원 #{} ({})", coupon.getId(), memberId, e.getMessage());
            }
        }
        return issued;
    }

    private void issue(Coupon coupon, Long memberId, LocalDateTime now) {
        if (couponRepository.increaseIssuedCount(coupon.getId()) == 0) {
            throw new ServiceException(CouponErrorCode.COUPON_SOLD_OUT);
        }
        try {
            memberCouponRepository.saveAndFlush(MemberCoupon.issue(coupon, memberId, now));
        } catch (DataIntegrityViolationException e) {
            // 같은 회원이 동시에 두 번 받음 → 유니크 제약. 중복 키일 때만 "이미 받음"으로 바꾼다 (규칙 문서 4-1)
            if (isDuplicateKey(e)) {
                throw new ServiceException(CouponErrorCode.COUPON_ALREADY_ISSUED);
            }
            throw e;
        }
    }

    private static boolean isDuplicateKey(DataIntegrityViolationException e) {
        return e.getMostSpecificCause() instanceof SQLException sql && sql.getErrorCode() == MYSQL_DUPLICATE_KEY;
    }
}