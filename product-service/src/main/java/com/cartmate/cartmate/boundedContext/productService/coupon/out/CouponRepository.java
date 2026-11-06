package com.cartmate.cartmate.boundedContext.productService.coupon.out;

import com.cartmate.cartmate.boundedContext.productService.coupon.domain.Coupon;
import com.cartmate.cartmate.boundedContext.productService.coupon.domain.CouponAutoTrigger;
import com.cartmate.cartmate.boundedContext.productService.coupon.domain.CouponIssueType;
import com.cartmate.cartmate.boundedContext.productService.coupon.domain.CouponStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // 발급 수를 DB에서 하나 올린다. 총 수량이 다 찼으면 0 → 소진.
    // 여러 명이 동시에 받아도 수량을 넘지 않는다 (읽고 더하는 대신 조건부 UPDATE 한 번)
    // UPDATE 뒤 영속성 컨텍스트를 비워서, 이미 불러온 Coupon의 옛 issuedCount를 다시 쓰거나 덮어쓰지 않게 한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Coupon c set c.issuedCount = c.issuedCount + 1 " +
            "where c.id = :id and (c.totalQuantity is null or c.issuedCount < c.totalQuantity)")
    int increaseIssuedCount(@Param("id") Long id);

    List<Coupon> findAllByIssueTypeAndAutoTriggerAndStatus(CouponIssueType issueType, CouponAutoTrigger autoTrigger,
                                                           CouponStatus status);

    List<Coupon> findAllByIssueTypeAndStatusOrderByIdDesc(CouponIssueType issueType, CouponStatus status);

    Page<Coupon> findAllByOrderByIdDesc(Pageable pageable);
}
