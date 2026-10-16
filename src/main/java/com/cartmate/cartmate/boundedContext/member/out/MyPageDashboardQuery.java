package com.cartmate.cartmate.boundedContext.member.out;

import com.cartmate.cartmate.boundedContext.member.dto.MyPageDashboardResponse;
import com.cartmate.cartmate.boundedContext.member.dto.MyPageDashboardResponse.GroupPurchase;
import com.cartmate.cartmate.boundedContext.member.dto.MyPageDashboardResponse.RecentOrder;
import com.cartmate.cartmate.boundedContext.member.dto.MyPageDashboardResponse.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * [임시] 마이페이지 요약 조회.
 * wallet, cart, order, delivery 모듈 API가 나오기 전까지 해당 테이블을 SQL로 읽기만 한다.
 * 모듈 경계 규칙의 예외이므로, 각 모듈 API가 나오면 shared 인터페이스 호출로 교체하고 이 클래스는 삭제한다.
 * 엔터티를 만들지 않아 다른 모듈 코드와 충돌하지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class MyPageDashboardQuery {
    private final NamedParameterJdbcTemplate jdbc;

    public MyPageDashboardResponse findByMemberId(Long memberId) {
        Map<String, Object> params = Map.of("memberId", memberId);
        return new MyPageDashboardResponse(
                findWallet(params),
                findGroupPurchases(params),
                findRecentOrders(params),
                countShipping(params)
        );
    }

    private Wallet findWallet(Map<String, Object> params) {
        List<Long> balances = jdbc.queryForList(
                "SELECT balance FROM wallet WHERE member_id = :memberId", params, Long.class);
        long balance = balances.isEmpty() || balances.get(0) == null ? 0 : balances.get(0);
        // wallet 테이블에 held_balance 컬럼이 아직 없어서 0으로 둔다
        return new Wallet(balance, 0);
    }

    private List<GroupPurchase> findGroupPurchases(Map<String, Object> params) {
        String sql = """
                SELECT gc.id,
                       gc.purchase_type,
                       gc.participants,
                       gc.edit_deadline_at,
                       (SELECT COUNT(*) FROM group_cart_member m WHERE m.group_cart_id = gc.id) AS joined_count,
                       (SELECT COUNT(*) FROM group_cart_item gi WHERE gi.group_cart_id = gc.id) AS item_count,
                       (SELECT p.name FROM group_cart_item gi JOIN product p ON p.id = gi.product_id
                         WHERE gi.group_cart_id = gc.id ORDER BY gi.id LIMIT 1) AS title,
                       CASE WHEN gc.purchase_type = 'SHARED' THEN
                            (SELECT CEIL(SUM(p.base_price * gi.quantity) / GREATEST(gc.participants, 1))
                               FROM group_cart_item gi JOIN product p ON p.id = gi.product_id
                              WHERE gi.group_cart_id = gc.id)
                       ELSE
                            (SELECT SUM(COALESCE(CAST(inv.applied_price AS SIGNED), p.base_price))
                               FROM group_cart_item gi
                               JOIN product p ON p.id = gi.product_id
                               LEFT JOIN group_cart_member_option o
                                      ON o.group_cart_item_id = gi.id AND o.group_cart_member_id = gm.id
                               LEFT JOIN Inventory inv ON inv.id = o.inventory_id
                              WHERE gi.group_cart_id = gc.id)
                       END AS my_amount
                  FROM group_cart_member gm
                  JOIN group_cart gc ON gc.id = gm.group_cart_id
                 WHERE gm.member_id = :memberId
                 ORDER BY gc.edit_deadline_at
                """;

        return jdbc.query(sql, params, (rs, i) -> new GroupPurchase(
                rs.getLong("id"),
                rs.getString("purchase_type"),
                rs.getString("title"),
                rs.getInt("item_count"),
                rs.getLong("my_amount"),
                rs.getInt("joined_count"),
                rs.getInt("participants"),
                toLocalDateTime(rs.getTimestamp("edit_deadline_at"))
        ));
    }

    private List<RecentOrder> findRecentOrders(Map<String, Object> params) {
        String sql = """
                SELECT o.id,
                       o.order_number,
                       o.ordered_at,
                       o.total_amount,
                       o.status,
                       (SELECT p.name FROM order_item oi JOIN product p ON p.id = oi.product_id
                         WHERE oi.order_id = o.id ORDER BY oi.id LIMIT 1) AS title,
                       (SELECT COUNT(*) FROM order_item oi WHERE oi.order_id = o.id) AS item_count,
                       (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_item oi WHERE oi.order_id = o.id) AS total_quantity,
                       (SELECT d.status FROM delivery d WHERE d.order_id = o.id ORDER BY d.id DESC LIMIT 1) AS delivery_status
                  FROM orders o
                 WHERE o.member_id = :memberId
                 ORDER BY o.ordered_at DESC
                 LIMIT 5
                """;

        return jdbc.query(sql, params, (rs, i) -> new RecentOrder(
                rs.getLong("id"),
                rs.getString("order_number"),
                toLocalDateTime(rs.getTimestamp("ordered_at")),
                rs.getString("title"),
                rs.getInt("item_count"),
                rs.getInt("total_quantity"),
                rs.getLong("total_amount"),
                rs.getString("status"),
                rs.getString("delivery_status")
        ));
    }

    private long countShipping(Map<String, Object> params) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM delivery d
                  JOIN orders o ON o.id = d.order_id
                 WHERE o.member_id = :memberId AND d.status = 'SHIPPING'
                """, params, Long.class);
        return count == null ? 0 : count;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}