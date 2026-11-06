package com.cartmate.cartmate.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class CartMateTestContainers {

    @ServiceConnection
    static final MySQLContainer MYSQL = new SingletonMySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("cartmatedb");

    static {
        MYSQL.start();
    }

    private CartMateTestContainers() {
    }

    private static final class SingletonMySQLContainer extends MySQLContainer {

        private SingletonMySQLContainer(DockerImageName image) {
            super(image);
        }

        @Override
        public void stop() {
        }
    }
}
