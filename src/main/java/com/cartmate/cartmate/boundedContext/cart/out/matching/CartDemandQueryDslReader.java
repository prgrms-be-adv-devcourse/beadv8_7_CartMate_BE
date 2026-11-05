package com.cartmate.cartmate.boundedContext.cart.out.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductDemand;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.cartmate.cartmate.boundedContext.cart.domain.personal.QCart.cart;
import static com.cartmate.cartmate.boundedContext.cart.domain.personal.QCartItem.cartItem;

@Repository
@RequiredArgsConstructor
class CartDemandQueryDslReader implements CartDemandReader {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ProductDemand> findRecentDemands(LocalDateTime since, int minMemberCount, int limit,
                                                Collection<Long> excludedProductIds) {
        List<Long> productIds = queryFactory
                .select(cartItem.productId)
                .from(cartItem)
                .join(cartItem.cart, cart)
                .where(
                        cartItem.updatedAt.goe(since),
                        excludedProductIds.isEmpty() ? null : cartItem.productId.notIn(excludedProductIds)
                )
                .groupBy(cartItem.productId)
                .having(cart.memberId.countDistinct().goe(minMemberCount))
                .orderBy(cart.memberId.countDistinct().desc(), cartItem.productId.asc())
                .limit(limit)
                .fetch();
        if (productIds.isEmpty()) {
            return List.of();
        }

        List<Tuple> rows = queryFactory
                .select(cartItem.productId, cart.memberId)
                .distinct()
                .from(cartItem)
                .join(cartItem.cart, cart)
                .where(
                        cartItem.productId.in(productIds),
                        cartItem.updatedAt.goe(since)
                )
                .fetch();

        Map<Long, Set<Long>> memberIdsByProductId = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> row.get(cartItem.productId),
                        Collectors.mapping(row -> row.get(cart.memberId), Collectors.toSet())
                ));
        return memberIdsByProductId.entrySet().stream()
                .map(entry -> new ProductDemand(entry.getKey(), entry.getValue()))
                .toList();
    }
}
