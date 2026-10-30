package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSellerTrust;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SalesPostSellerTrustRepository extends JpaRepository<SalesPostSellerTrust, Long> {

    // 판매글 목록의 판매자들의 신뢰도를 한 번에 조회한다. 판매자마다 조회하지 않아 목록 크기와 무관하게 쿼리 1번이다.
    List<SalesPostSellerTrust> findAllBySellerIdIn(Collection<Long> sellerIds);
}
