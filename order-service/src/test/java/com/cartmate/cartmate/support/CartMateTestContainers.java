package com.cartmate.cartmate.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * order-service 테스트가 JVM 하나에서 같이 쓰는 컨테이너 모음(TEST-2). 공용 어노테이션이
 * {@code @ImportTestcontainers(CartMateTestContainers.class)}로 가져오고, {@link ServiceConnection}이
 * datasource url · 계정을 넣는다. 그래서 {@code application-test.yaml}에는 DB 주소가 없다.
 *
 * <p>컨테이너는 이 클래스를 처음 쓸 때 한 번만 뜨고 JVM이 끝날 때 Testcontainers ryuk가 치운다.
 * Spring은 컨텍스트를 닫을 때 컨테이너 빈의 {@code stop()}을 부르는데, 컨텍스트가 여러 개(통합 · JPA 슬라이스 등)라
 * 하나가 닫힐 때 컨테이너가 같이 내려가면 남은 컨텍스트가 DB를 잃는다. 그래서 {@link SingletonMySQLContainer}는
 * {@code stop()}을 무시한다.
 *
 * <p>Kafka · Redis 테스트 컨테이너가 필요해지면 여기에 같은 방식(static 필드 + {@link ServiceConnection})으로 더한다.
 */
public final class CartMateTestContainers {

    @ServiceConnection
    static final MySQLContainer MYSQL = new SingletonMySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("cartmatedb");

    static {
        MYSQL.start();
    }

    private CartMateTestContainers() {
    }

    /** 컨텍스트가 닫혀도 내려가지 않는 MySQL 컨테이너. 정리는 JVM 종료 때 ryuk가 한다. */
    private static final class SingletonMySQLContainer extends MySQLContainer {

        private SingletonMySQLContainer(DockerImageName image) {
            super(image);
        }

        @Override
        public void stop() {
            // 의도적으로 비움: 다른 컨텍스트가 아직 이 컨테이너를 쓰고 있을 수 있다
        }
    }
}
