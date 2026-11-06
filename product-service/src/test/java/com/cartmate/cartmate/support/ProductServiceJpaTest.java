package com.cartmate.cartmate.support;

import com.cartmate.cartmate.global.global.JpaAuditingConfig;
import com.cartmate.cartmate.global.global.QuerydslConfig;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ImportTestcontainers(ProductServiceTestContainers.class)
@ExtendWith(DatabaseCleaner.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
public @interface ProductServiceJpaTest {
}
