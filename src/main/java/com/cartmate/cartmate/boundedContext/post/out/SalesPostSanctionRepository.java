package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSanction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesPostSanctionRepository extends JpaRepository<SalesPostSanction, Long> {
    List<SalesPostSanction> findBySalesPostIdOrderByIdDesc(Long salesPostId);
}
