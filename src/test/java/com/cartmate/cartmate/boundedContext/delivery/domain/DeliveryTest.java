package com.cartmate.cartmate.boundedContext.delivery.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryTest {

    private Delivery newDelivery() {
        return Delivery.create(1L, 10L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
    }

    @Test
    @DisplayName("배송을 생성하면 배송요청 상태로 시작한다")
    void createStartsAsRequested() {
        assertThat(newDelivery().getStatus()).isEqualTo(DeliveryStatus.REQUESTED);
    }

    @Test
    @DisplayName("배송요청 상태에서 택배사와 송장번호를 등록하면 배송준비 상태가 된다")
    void prepare() {
        Delivery delivery = newDelivery();

        delivery.prepare("CJ대한통운", "658219384910");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PREPARING);
        assertThat(delivery.getCarrier()).isEqualTo("CJ대한통운");
        assertThat(delivery.getTrackingNumber()).isEqualTo("658219384910");
    }

    @Test
    @DisplayName("택배사와 송장번호의 앞뒤 공백은 제거해서 저장한다")
    void prepareTrims() {
        Delivery delivery = newDelivery();

        delivery.prepare(" CJ대한통운 ", " 658219384910 ");

        assertThat(delivery.getCarrier()).isEqualTo("CJ대한통운");
        assertThat(delivery.getTrackingNumber()).isEqualTo("658219384910");
    }

    @Test
    @DisplayName("택배사나 송장번호가 비어 있으면 400 오류이고 상태가 바뀌지 않는다")
    void prepareRequiresCarrierAndTrackingNumber() {
        Delivery delivery = newDelivery();

        assertThatThrownBy(() -> delivery.prepare(null, "1"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CARRIER_REQUIRED");
        assertThatThrownBy(() -> delivery.prepare(" ", "1"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CARRIER_REQUIRED");
        assertThatThrownBy(() -> delivery.prepare("CJ대한통운", null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_TRACKING_NUMBER_REQUIRED");
        assertThatThrownBy(() -> delivery.prepare("CJ대한통운", " "))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_TRACKING_NUMBER_REQUIRED");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.REQUESTED);
    }

    @Test
    @DisplayName("이미 같은 송장 정보로 준비 처리됐으면 다시 요청해도 그대로 성공한다")
    void prepareIsIdempotent() {
        Delivery delivery = newDelivery();
        delivery.prepare("CJ대한통운", "658219384910");

        delivery.prepare("CJ대한통운", "658219384910");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PREPARING);
    }

    @Test
    @DisplayName("이미 준비 처리된 배송에 다른 송장 정보가 오면 409 오류이고 기존 값이 유지된다")
    void prepareWithDifferentInfoConflicts() {
        Delivery delivery = newDelivery();
        delivery.prepare("CJ대한통운", "658219384910");

        assertThatThrownBy(() -> delivery.prepare("우체국택배", "111"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("409-DELIVERY_ALREADY_PREPARED");

        assertThat(delivery.getCarrier()).isEqualTo("CJ대한통운");
        assertThat(delivery.getTrackingNumber()).isEqualTo("658219384910");
    }

    @Test
    @DisplayName("배송준비 상태에서 출발 처리하면 배송중이 되고 출발 시각이 기록된다")
    void ship() {
        Delivery delivery = newDelivery();
        delivery.prepare("CJ대한통운", "658219384910");

        delivery.ship();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
        assertThat(delivery.getShippedAt()).isNotNull();
        assertThat(delivery.getCarrier()).isEqualTo("CJ대한통운");
        assertThat(delivery.getTrackingNumber()).isEqualTo("658219384910");
    }

    @Test
    @DisplayName("이미 배송중이면 다시 출발 처리해도 성공하고 출발 시각이 바뀌지 않는다")
    void shipIsIdempotent() {
        Delivery delivery = newDelivery();
        delivery.prepare("CJ대한통운", "658219384910");
        delivery.ship();
        LocalDateTime firstShippedAt = delivery.getShippedAt();

        delivery.ship();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
        assertThat(delivery.getShippedAt()).isEqualTo(firstShippedAt);
    }

    @Test
    @DisplayName("배송요청, 배송완료, 배송취소 상태에서는 출발 처리할 수 없다")
    void shipInvalidStatus() {
        for (DeliveryStatus status : new DeliveryStatus[]{
                DeliveryStatus.REQUESTED, DeliveryStatus.DELIVERED, DeliveryStatus.CANCELED}) {
            Delivery delivery = newDelivery();
            ReflectionTestUtils.setField(delivery, "status", status);

            assertThatThrownBy(delivery::ship)
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("409-DELIVERY_INVALID_STATUS");
            assertThat(delivery.getStatus()).isEqualTo(status);
            assertThat(delivery.getShippedAt()).isNull();
        }
    }

    @Test
    @DisplayName("배송중 상태에서 완료 처리하면 배송완료가 되고 완료 시각이 기록된다")
    void deliver() {
        Delivery delivery = newDelivery();
        delivery.prepare("CJ대한통운", "658219384910");
        delivery.ship();

        delivery.deliver();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isNotNull();
        assertThat(delivery.getShippedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 배송완료면 다시 완료 처리해도 성공하고 완료 시각이 바뀌지 않는다")
    void deliverIsIdempotent() {
        Delivery delivery = newDelivery();
        delivery.prepare("CJ대한통운", "658219384910");
        delivery.ship();
        delivery.deliver();
        LocalDateTime firstDeliveredAt = delivery.getDeliveredAt();

        delivery.deliver();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isEqualTo(firstDeliveredAt);
    }

    @Test
    @DisplayName("배송요청, 배송준비, 배송취소 상태에서는 완료 처리할 수 없다")
    void deliverInvalidStatus() {
        for (DeliveryStatus status : new DeliveryStatus[]{
                DeliveryStatus.REQUESTED, DeliveryStatus.PREPARING, DeliveryStatus.CANCELED}) {
            Delivery delivery = newDelivery();
            ReflectionTestUtils.setField(delivery, "status", status);

            assertThatThrownBy(delivery::deliver)
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("409-DELIVERY_INVALID_STATUS");
            assertThat(delivery.getStatus()).isEqualTo(status);
            assertThat(delivery.getDeliveredAt()).isNull();
        }
    }

    @Test
    @DisplayName("배송완료 상태에서 구매확정하면 구매확정이 되고 확정 시각이 기록된다")
    void confirm() {
        Delivery delivery = newDelivery();
        delivery.prepare("CJ대한통운", "658219384910");
        delivery.ship();
        delivery.deliver();

        delivery.confirm();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CONFIRMED);
        assertThat(delivery.getConfirmedAt()).isNotNull();
        assertThat(delivery.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 구매확정이면 다시 확정해도 성공하고 확정 시각이 바뀌지 않는다")
    void confirmIsIdempotent() {
        Delivery delivery = newDelivery();
        delivery.prepare("CJ대한통운", "658219384910");
        delivery.ship();
        delivery.deliver();
        delivery.confirm();
        LocalDateTime firstConfirmedAt = delivery.getConfirmedAt();

        delivery.confirm();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CONFIRMED);
        assertThat(delivery.getConfirmedAt()).isEqualTo(firstConfirmedAt);
    }

    @Test
    @DisplayName("배송요청, 배송준비, 배송중, 배송취소 상태에서는 구매확정할 수 없다")
    void confirmInvalidStatus() {
        for (DeliveryStatus status : new DeliveryStatus[]{
                DeliveryStatus.REQUESTED, DeliveryStatus.PREPARING,
                DeliveryStatus.SHIPPING, DeliveryStatus.CANCELED}) {
            Delivery delivery = newDelivery();
            ReflectionTestUtils.setField(delivery, "status", status);

            assertThatThrownBy(delivery::confirm)
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("409-DELIVERY_INVALID_STATUS");
            assertThat(delivery.getStatus()).isEqualTo(status);
            assertThat(delivery.getConfirmedAt()).isNull();
        }
    }

    @Test
    @DisplayName("구매확정된 배송은 준비·출발·완료 처리를 다시 할 수 없다")
    void confirmedCannotMoveBack() {
        Delivery delivery = newDelivery();
        ReflectionTestUtils.setField(delivery, "status", DeliveryStatus.CONFIRMED);

        assertThatThrownBy(() -> delivery.prepare("CJ대한통운", "658219384910"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("409-DELIVERY_INVALID_STATUS");
        assertThatThrownBy(delivery::ship)
                .isInstanceOf(ServiceException.class).hasMessageContaining("409-DELIVERY_INVALID_STATUS");
        assertThatThrownBy(delivery::deliver)
                .isInstanceOf(ServiceException.class).hasMessageContaining("409-DELIVERY_INVALID_STATUS");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CONFIRMED);
    }

    @Test
    @DisplayName("배송중, 배송완료, 배송취소 상태에서는 준비 처리할 수 없다")
    void prepareInvalidStatus() {
        for (DeliveryStatus status : new DeliveryStatus[]{
                DeliveryStatus.SHIPPING, DeliveryStatus.DELIVERED, DeliveryStatus.CANCELED}) {
            Delivery delivery = newDelivery();
            ReflectionTestUtils.setField(delivery, "status", status);

            assertThatThrownBy(() -> delivery.prepare("CJ대한통운", "658219384910"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("409-DELIVERY_INVALID_STATUS");
            assertThat(delivery.getStatus()).isEqualTo(status);
        }
    }
}
