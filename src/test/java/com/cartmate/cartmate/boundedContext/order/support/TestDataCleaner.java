package com.cartmate.cartmate.boundedContext.order.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TestDataCleaner {

    private final NamedParameterJdbcTemplate jdbc;

    public TestDataCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public void deleteDeliveriesOf(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return;
        }
        List<Long> deliveryIds = jdbc.queryForList(
                "SELECT id FROM delivery WHERE member_id IN (:memberIds)",
                Map.of("memberIds", memberIds), Long.class);
        if (deliveryIds.isEmpty()) {
            return;
        }
        Map<String, Object> params = Map.of("deliveryIds", deliveryIds);
        jdbc.update("DELETE FROM delivery_item_option WHERE delivery_item_id IN "
                + "(SELECT id FROM delivery_item WHERE delivery_id IN (:deliveryIds))", params);
        jdbc.update("DELETE FROM delivery_item WHERE delivery_id IN (:deliveryIds)", params);
        jdbc.update("DELETE FROM delivery WHERE id IN (:deliveryIds)", params);
    }

    public void deleteAbuseRecordsOf(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return;
        }
        Map<String, Object> params = Map.of("memberIds", memberIds);
        jdbc.update("DELETE FROM abuse_restriction WHERE member_id IN (:memberIds)", params);
        jdbc.update("DELETE FROM abuse_judgment WHERE member_id IN (:memberIds)", params);
        jdbc.update("DELETE FROM abuse_signal WHERE member_id IN (:memberIds)", params);
    }

    public void deleteSharedGroupOrdersOfPurchases(Collection<Long> purchaseIds) {
        if (purchaseIds.isEmpty()) {
            return;
        }
        List<Long> orderIds = jdbc.queryForList(
                "SELECT id FROM shared_group_order WHERE shared_group_purchase_id IN (:purchaseIds)",
                Map.of("purchaseIds", purchaseIds), Long.class);
        if (orderIds.isEmpty()) {
            return;
        }
        Map<String, Object> params = Map.of("orderIds", orderIds);
        jdbc.update("DELETE FROM shared_group_order_item WHERE shared_group_order_id IN (:orderIds)", params);
        jdbc.update("DELETE FROM shared_group_order_participant WHERE shared_group_order_id IN (:orderIds)", params);
        jdbc.update("DELETE FROM shared_group_order WHERE id IN (:orderIds)", params);
    }
}
