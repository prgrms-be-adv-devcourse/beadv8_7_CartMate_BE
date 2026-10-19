package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

// 배송 생성 요청 내용을 SHA-256 해시로 만든다. 같은 내용이면 항상 같은 값이 나온다.
// 멱등키와 서버 생성값은 제외하고, 규칙을 바꾸면 기존 해시와 달라지므로 VERSION을 올린다.
public final class DeliveryRequestHasher {

    private static final String VERSION = "v2";

    private DeliveryRequestHasher() {
    }

    public static String hash(DeliveryCreateCommand command) {
        StringBuilder sb = new StringBuilder(VERSION);

        append(sb, command.memberId());
        append(sb, command.sellerId());
        append(sb, command.orderId());
        append(sb, command.purchaseType());
        append(sb, command.receiverName());
        append(sb, command.receiverPhone());
        append(sb, command.zipCode());
        append(sb, command.address());
        append(sb, command.addressDetail());

        // 아이템은 입력 순서와 무관하게 같은 해시가 나오도록 정렬한다.
        List<DeliveryCreateCommand.Item> items = command.deliveryItem().stream()
                .sorted(Comparator
                        .comparing(DeliveryCreateCommand.Item::orderItemId)
                        .thenComparing(DeliveryCreateCommand.Item::productName,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(DeliveryCreateCommand.Item::unitPrice,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(DeliveryCreateCommand.Item::quantity))
                .toList();

        append(sb, items.size());
        for (DeliveryCreateCommand.Item item : items) {
            append(sb, item.orderItemId());
            append(sb, item.productName());
            append(sb, item.unitPrice());
            append(sb, item.quantity());

            // 옵션은 입력 순서가 sortOrder로 저장되므로 순서를 유지한다. null과 빈 목록은 같게 취급한다.
            List<DeliveryCreateCommand.Option> options =
                    item.options() == null ? List.of() : item.options();
            append(sb, options.size());
            for (DeliveryCreateCommand.Option option : options) {
                append(sb, option.optionName());
                append(sb, option.optionValue());
            }
        }

        return sha256(sb.toString());
    }

    // 값 구분자가 값에 섞여도 모호하지 않도록 길이를 함께 넣는다. null은 "-"로 표현한다.
    private static void append(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("|-");
            return;
        }
        String text = value.toString();
        sb.append('|').append(text.length()).append(':').append(text);
    }

    private static String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
