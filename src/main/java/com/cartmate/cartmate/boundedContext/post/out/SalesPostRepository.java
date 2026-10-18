package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesPostRepository extends JpaRepository<SalesPost, Long>, SalesPostQueryRepository {
}
