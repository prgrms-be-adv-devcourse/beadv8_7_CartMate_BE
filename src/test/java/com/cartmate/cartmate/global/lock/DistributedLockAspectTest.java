package com.cartmate.cartmate.global.lock;

import com.cartmate.cartmate.boundedContext.wallet.app.WalletChargeConfirmService;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletChargeSettlement;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletChargeGateway;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedLockAspectTest {

    private final DistributedLockExecutor executor = mock(DistributedLockExecutor.class);
    private final DistributedLockAspect aspect = new DistributedLockAspect(executor);

    public static class Target {
        boolean called;

        @DistributedLock(key = "'wallet-charge:' + #orderNumber", waitSeconds = 5)
        public String confirm(Long walletId, String orderNumber) {
            called = true;
            return "ok:" + orderNumber;
        }

        @DistributedLock(key = "'default-wait:' + #id")
        public void withDefaultWait(Long id) {
            called = true;
        }

        @DistributedLock(key = "#missing")
        public void emptyKey() {
            called = true;
        }
    }

    private final Target target = new Target();
    private Target proxy;

    @BeforeEach
    void setUp() throws Throwable {
        // 일꾼은 가짜다. 락 없이 건네받은 일을 그대로 실행한다.
        when(executor.call(anyString(), anyLong(), any())).thenAnswer(
                invocation -> ((DistributedLockExecutor.LockedAction<?>) invocation.getArgument(2)).get());

        proxy = proxyOf(target);
    }

    private <T> T proxyOf(T object) {
        AspectJProxyFactory factory = new AspectJProxyFactory(object);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Test
    @DisplayName("파라미터 값으로 락 키를 만들어 일꾼에게 넘기고, 진짜 메서드의 결과를 돌려준다")
    void buildsKeyFromParameter() throws Throwable {
        String result = proxy.confirm(1L, "ORD-1");

        assertThat(result).isEqualTo("ok:ORD-1");
        verify(executor).call(eq("lock:wallet-charge:ORD-1"), eq(5L), any());
    }

    @Test
    @DisplayName("주문번호가 다르면 락 키도 다르다")
    void differentOrderDifferentKey() throws Throwable {
        proxy.confirm(1L, "ORD-1");
        proxy.confirm(1L, "ORD-2");

        verify(executor).call(eq("lock:wallet-charge:ORD-1"), anyLong(), any());
        verify(executor).call(eq("lock:wallet-charge:ORD-2"), anyLong(), any());
    }

    @Test
    @DisplayName("waitSeconds를 안 적으면 기본값 3초를 일꾼에게 넘긴다")
    void usesDefaultWaitSeconds() throws Throwable {
        proxy.withDefaultWait(7L);

        verify(executor).call(eq("lock:default-wait:7"), eq(3L), any());
    }

    @Test
    @DisplayName("락을 못 얻으면 진짜 메서드를 실행하지 않고 예외를 그대로 던진다")
    void doesNotRunMethodWhenLockFails() throws Throwable {
        doThrow(new ServiceException(LockErrorCode.LOCK_ACQUISITION_FAILED))
                .when(executor).call(anyString(), anyLong(), any());

        assertThatThrownBy(() -> proxy.confirm(1L, "ORD-1"))
                .isInstanceOfSatisfying(ServiceException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(LockErrorCode.LOCK_ACQUISITION_FAILED.resultCode()));

        assertThat(target.called).isFalse();
    }

    @Test
    @DisplayName("키 계산 결과가 비면 IllegalStateException을 던지고 진짜 메서드를 실행하지 않는다")
    void failsWhenKeyIsEmpty() {
        assertThatThrownBy(() -> proxy.emptyKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#missing");

        assertThat(target.called).isFalse();
    }

    @Test
    @DisplayName("충전 승인(confirm)에 붙은 어노테이션은 lock:wallet-charge:{주문번호} 키를 만든다")
    void walletChargeConfirmUsesOrderNumberKey() throws Throwable {
        // 이 테스트는 일꾼이 action을 실행하지 않게 해서, 진짜 confirm의 내부 로직은 건드리지 않는다.
        doReturn(null).when(executor).call(anyString(), anyLong(), any());
        WalletChargeConfirmService service = proxyOf(new WalletChargeConfirmService(
                mock(WalletFacade.class), mock(WalletChargeSettlement.class), mock(WalletChargeGateway.class)));

        service.confirm(1L, "Charge_1", "pk_1", 10_000L);

        verify(executor).call(eq("lock:wallet-charge:Charge_1"), eq(3L), any());
    }
}
