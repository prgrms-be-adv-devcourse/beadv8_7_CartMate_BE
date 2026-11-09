package com.cartmate.cartmate.support.context;

import org.springframework.core.Ordered;
import org.springframework.test.context.BootstrapUtils;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

public class ContextUsageListener extends AbstractTestExecutionListener {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        Class<?> testClass = testContext.getTestClass();
        try {
            ContextUsageReport.record(mergedConfig(testContext), testClass);
        } catch (RuntimeException e) {
            System.err.println("[test-contexts] " + testClass.getName() + " 컨텍스트 구성을 기록하지 못했다: " + e);
        }
    }

    private static MergedContextConfiguration mergedConfig(TestContext testContext) {
        Field field = ReflectionUtils.findField(testContext.getClass(), "mergedConfig", MergedContextConfiguration.class);
        if (field != null) {
            ReflectionUtils.makeAccessible(field);
            Object value = ReflectionUtils.getField(field, testContext);
            if (value instanceof MergedContextConfiguration config) {
                return config;
            }
        }
        return BootstrapUtils.resolveTestContextBootstrapper(testContext.getTestClass()).buildMergedContextConfiguration();
    }
}
