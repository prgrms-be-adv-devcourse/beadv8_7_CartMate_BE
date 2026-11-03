package com.cartmate.cartmate.boundedContext.support.out;

import com.cartmate.cartmate.boundedContext.support.domain.Notice;
import com.cartmate.cartmate.boundedContext.support.domain.NoticeCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 고정 공지 먼저, 그다음 최신순. 구분·제목 검색은 값이 있을 때만 적용한다.
    @Query(value = "select n from Notice n " +
            "where (:category is null or n.category = :category) " +
            "and (:keyword is null or n.title like concat('%', :keyword, '%')) " +
            "order by n.pinned desc, n.id desc",
            countQuery = "select count(n) from Notice n " +
                    "where (:category is null or n.category = :category) " +
                    "and (:keyword is null or n.title like concat('%', :keyword, '%'))")
    Page<Notice> search(@Param("category") NoticeCategory category,
                        @Param("keyword") String keyword,
                        Pageable pageable);

    // 조회수는 동시에 여러 명이 봐도 빠지지 않게 DB에서 바로 1 올린다
    @Modifying(clearAutomatically = true)
    @Query("update Notice n set n.viewCount = n.viewCount + 1 where n.id = :id")
    int increaseViewCount(@Param("id") Long id);
}