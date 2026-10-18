package com.cartmate.cartmate.boundedContext.delivery.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
