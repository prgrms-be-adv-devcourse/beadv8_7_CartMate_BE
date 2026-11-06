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

public class DatabaseCleaner implements BeforeEachCallback {

    private static final String TABLES_SQL = "SELECT table_name FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'";

    @Override
    public void beforeEach(ExtensionContext context) throws SQLException {
        DataSource dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
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
        String container = "jdbc:mysql://" + ProductServiceTestContainers.MYSQL.getHost() + ":"
                + ProductServiceTestContainers.MYSQL.getMappedPort(3306) + "/"
                + ProductServiceTestContainers.MYSQL.getDatabaseName();
        boolean matches = url != null && url.startsWith(container)
                && (url.length() == container.length() || url.charAt(container.length()) == '?');
        if (!matches) {
            throw new IllegalStateException("테스트 컨테이너 DB가 아니라 데이터를 지우지 않는다: " + url
                    + " (기대: " + container + ")");
        }
    }
}
