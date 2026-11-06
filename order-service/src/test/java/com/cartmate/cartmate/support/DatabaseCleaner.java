package com.cartmate.cartmate.support;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트마다 시작 전에 테스트 DB의 모든 테이블을 비운다(TEST-2). {@link CartMateIntegrationTest} · {@link CartMateJpaTest}가 건다.
 * JPA 슬라이스는 테스트 트랜잭션과 다른 연결로 비우므로, 앞 통합 테스트가 커밋한 데이터가 슬라이스로 새지 않는다.
 *
 * <p>테이블 목록은 손으로 적지 않고 {@code information_schema}에서 읽는다. 엔티티 · outbox · shedlock 테이블이 늘어도
 * 정리 누락이 없고, 앞 테스트가 남긴 outbox 행이 릴레이를 막거나 다른 테스트 데이터와 섞이는 일이 없다.
 * 끝난 뒤가 아니라 시작 전에 비우므로 앞 테스트가 실패해 정리를 못 했어도 영향이 없다.
 *
 * <p>{@code TRUNCATE}는 테이블마다 DDL이라 느려서 {@code DELETE}를 쓴다. 대부분 비어 있어 거의 비용이 없고,
 * auto increment가 이어져 앞 테스트의 ID가 다시 쓰이지 않는다.
 *
 * <p>안전장치: 연결된 DB가 {@link CartMateTestContainers}의 컨테이너가 아니면 아무것도 지우지 않고 실패한다.
 * 로컬 {@code cartmate} DB에는 프론트 확인용 데이터가 있어서 절대 비우면 안 된다.
 */
public class DatabaseCleaner implements BeforeEachCallback {

    private static final String TABLES_SQL = "SELECT table_name FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'";

    @Override
    public void beforeEach(ExtensionContext context) throws SQLException {
        DataSource dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
        clean(dataSource);
    }

    public static void clean(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            requireTestContainer(connection.getMetaData().getURL());
            List<String> tables = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery(TABLES_SQL)) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String table : tables) {
                    statement.executeUpdate("DELETE FROM `" + table + "`");
                }
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    private static void requireTestContainer(String url) {
        String container = "jdbc:mysql://" + CartMateTestContainers.MYSQL.getHost() + ":"
                + CartMateTestContainers.MYSQL.getMappedPort(3306) + "/" + CartMateTestContainers.MYSQL.getDatabaseName();
        boolean matches = url != null && url.startsWith(container)
                && (url.length() == container.length() || url.charAt(container.length()) == '?');
        if (!matches) {
            throw new IllegalStateException("테스트 컨테이너 DB가 아니라 데이터를 지우지 않는다: " + url
                    + " (기대: " + container + "). 로컬 DB를 비우지 않도록 막아 둔 안전장치다");
        }
    }
}
