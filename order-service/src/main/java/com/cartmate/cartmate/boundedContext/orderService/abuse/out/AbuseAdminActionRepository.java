package com.cartmate.cartmate.boundedContext.orderService.abuse.out;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseAdminAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AbuseAdminActionRepository extends JpaRepository<AbuseAdminAction, Long> {

    List<AbuseAdminAction> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);
}
