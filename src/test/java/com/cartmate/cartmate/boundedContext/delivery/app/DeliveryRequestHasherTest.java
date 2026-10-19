package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand.Item;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand.Option;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryRequestHasherTest {

    private static final Item SHIRT = new Item(1000L, "티셔츠", 2L,
            List.of(new Option("사이즈", "L"), new Option("색상", "블랙")));
    private static final Item SOCKS = new Item(1001L, "양말", 1L, List.of());

    private DeliveryCreateCommand command(String key, Long sellerId, String address, List<Item> items) {
        return new DeliveryCreateCommand(1L, sellerId, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", address, "101호", key, items);
    }

    @Test
    @DisplayName("같은 내용이면 같은 해시가 나오고 64자리 16진수다")
    void sameContentSameHash() {
        String a = DeliveryRequestHasher.hash(command("k1", 10L, "서울", List.of(SHIRT, SOCKS)));
        String b = DeliveryRequestHasher.hash(command("k1", 10L, "서울", List.of(SHIRT, SOCKS)));

        assertThat(a).isEqualTo(b).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("멱등키는 해시에 영향을 주지 않는다")
    void idempotencyKeyExcluded() {
        assertThat(DeliveryRequestHasher.hash(command("k1", 10L, "서울", List.of(SHIRT))))
                .isEqualTo(DeliveryRequestHasher.hash(command("k2", 10L, "서울", List.of(SHIRT))));
    }

    @Test
    @DisplayName("아이템 입력 순서가 달라도 같은 해시가 나온다")
    void itemOrderIgnored() {
        assertThat(DeliveryRequestHasher.hash(command("k", 10L, "서울", List.of(SHIRT, SOCKS))))
                .isEqualTo(DeliveryRequestHasher.hash(command("k", 10L, "서울", List.of(SOCKS, SHIRT))));
    }

    @Test
    @DisplayName("옵션 순서가 달라지면 다른 해시가 나온다")
    void optionOrderMatters() {
        Item reversed = new Item(1000L, "티셔츠", 2L,
                List.of(new Option("색상", "블랙"), new Option("사이즈", "L")));

        assertThat(DeliveryRequestHasher.hash(command("k", 10L, "서울", List.of(SHIRT))))
                .isNotEqualTo(DeliveryRequestHasher.hash(command("k", 10L, "서울", List.of(reversed))));
    }

    @Test
    @DisplayName("옵션이 null이거나 비어 있으면 같은 해시가 나온다")
    void nullOptionsEqualsEmpty() {
        Item withNull = new Item(1001L, "양말", 1L, null);

        assertThat(DeliveryRequestHasher.hash(command("k", 10L, "서울", List.of(withNull))))
                .isEqualTo(DeliveryRequestHasher.hash(command("k", 10L, "서울", List.of(SOCKS))));
    }

    @Test
    @DisplayName("판매자, 주소, 수량, 옵션값 중 하나라도 다르면 다른 해시가 나온다")
    void differentContentDifferentHash() {
        String base = DeliveryRequestHasher.hash(command("k", 10L, "서울", List.of(SHIRT)));

        assertThat(DeliveryRequestHasher.hash(command("k", 11L, "서울", List.of(SHIRT)))).isNotEqualTo(base);
        assertThat(DeliveryRequestHasher.hash(command("k", 10L, "부산", List.of(SHIRT)))).isNotEqualTo(base);
        assertThat(DeliveryRequestHasher.hash(command("k", 10L, "서울",
                List.of(new Item(1000L, "티셔츠", 3L, SHIRT.options()))))).isNotEqualTo(base);
        assertThat(DeliveryRequestHasher.hash(command("k", 10L, "서울",
                List.of(new Item(1000L, "티셔츠", 2L,
                        List.of(new Option("사이즈", "M"), new Option("색상", "블랙"))))))).isNotEqualTo(base);
    }

    @Test
    @DisplayName("구분자가 값에 섞여도 다른 입력이 같은 해시로 합쳐지지 않는다")
    void delimiterInValueIsUnambiguous() {
        Item a = new Item(1000L, "a|1:b", 1L, List.of());
        Item b = new Item(1000L, "a", 1L, List.of(new Option("b", "1")));

        assertThat(DeliveryRequestHasher.hash(command("k", 10L, "서울", List.of(a))))
                .isNotEqualTo(DeliveryRequestHasher.hash(command("k", 10L, "서울", List.of(b))));
    }
}
