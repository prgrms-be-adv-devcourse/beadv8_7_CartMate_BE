package com.cartmate.cartmate.boundedContext.orderService.abuse.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collection;
import java.util.Map;

public class AbuseTestDataCleaner {

    private final NamedParameterJdbcTemplate jdbc;

    public AbuseTestDataCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public void deleteRecordsOf(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return;
        }
        Map<String, Object> params = Map.of("memberIds", memberIds);
        jdbc.update("DELETE FROM abuse_restriction_history WHERE member_id IN (:memberIds)", params);
        jdbc.update("DELETE FROM abuse_signal WHERE member_id IN (:memberIds)", params);
        jdbc.update("DELETE FROM abuse_member_state WHERE member_id IN (:memberIds)", params);
    }
}
