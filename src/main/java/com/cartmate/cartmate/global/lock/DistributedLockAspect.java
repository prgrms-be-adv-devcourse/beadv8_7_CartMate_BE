package com.cartmate.cartmate.global.lock;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Aspect
@Component
// @Transactional(LOWEST_PRECEDENCE)보다 바깥에서 실행되어, 커밋이 끝난 뒤에 락이 풀린다.
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final DistributedLockExecutor executor;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String key = "lock:" + evaluate(joinPoint, distributedLock.key());
        return executor.call(key, distributedLock.waitSeconds(), joinPoint::proceed);
    }

    // 메서드 파라미터 이름(#orderNumber)을 SpEL 변수로 넣어 키 문자열을 만든다.
    String evaluate(ProceedingJoinPoint joinPoint, String expression) {
        String[] names = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < names.length; i++) {
            context.setVariable(names[i], args[i]);
        }

        String key = parser.parseExpression(expression).getValue(context, String.class);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("락 키가 비어 있습니다: " + expression);
        }
        return key;
    }
}
