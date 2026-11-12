package com.cartmate.cartmate.boundedContext.orderService.abuse.out;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class AbuseMemberStateFaults implements BeanPostProcessor {

    private final AtomicReference<RuntimeException> insertFailure = new AtomicReference<>();
    private volatile boolean stateHiddenOnLock;

    public Scope failInsertIfAbsent(RuntimeException failure) {
        insertFailure.set(failure);
        return () -> insertFailure.set(null);
    }

    public Scope hideStateOnLock() {
        stateHiddenOnLock = true;
        return () -> stateHiddenOnLock = false;
    }

    public void reset() {
        insertFailure.set(null);
        stateHiddenOnLock = false;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof AbuseMemberStateRepository)) {
            return bean;
        }
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(bean);
        factory.addInterface(AbuseMemberStateRepository.class);
        factory.addAdvice((MethodInterceptor) this::intercept);
        return factory.getProxy(AbuseMemberStateRepository.class.getClassLoader());
    }

    private Object intercept(MethodInvocation invocation) throws Throwable {
        String method = invocation.getMethod().getName();
        RuntimeException failure = insertFailure.get();
        if (failure != null && method.equals("insertIfAbsent")) {
            throw failure;
        }
        if (stateHiddenOnLock && method.equals("findForUpdate")) {
            return Optional.empty();
        }
        return invocation.proceed();
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
