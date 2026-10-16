package com.cartmate.cartmate.boundedContext.member.out;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.dto.AdminMemberSearchRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class MemberSpecifications {

    private MemberSpecifications() {
    }

    public static Specification<Member> adminSearch(AdminMemberSearchRequest c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            String keyword = c.keyword() == null ? "" : c.keyword().trim();
            if (!keyword.isEmpty()) {
                String type = c.searchType() == null ? "email" : c.searchType();
                switch (type) {
                    case "id" -> {
                        try {
                            predicates.add(cb.equal(root.get("id"), Long.parseLong(keyword)));
                        } catch (NumberFormatException e) {
                            predicates.add(cb.disjunction()); // 숫자가 아니면 결과 없음
                        }
                    }
                    case "nickname" -> predicates.add(cb.like(root.get("nickname"), "%" + keyword + "%"));
                    default -> predicates.add(cb.like(cb.lower(root.get("email")), "%" + keyword.toLowerCase() + "%"));
                }
            }

            if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }
            if (c.role() != null) {
                predicates.add(cb.equal(root.get("role"), c.role()));
            }
            if (c.joinedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createDate"), c.joinedFrom().atStartOfDay()));
            }
            if (c.joinedTo() != null) {
                // 종료일 당일까지 포함
                predicates.add(cb.lessThan(root.get("createDate"), c.joinedTo().plusDays(1).atStartOfDay()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}