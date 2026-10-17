package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeliveryCreateUseCase {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryItemRepository deliveryItemRepository;

    public void create(DeliveryCreateCommand createCommand) {
        validatedCreate(createCommand);

        Optional<Delivery> existing = deliveryRepository.findByIdempotencyKey(createCommand.idempotencyKey());

        if (existing.isPresent()) {
            Delivery delivery = existing.get();

            if (!delivery.getMemberId().equals(createCommand.memberId())
                    || !delivery.getOrderId().equals(createCommand.orderId())
                    || delivery.getPurchaseType() != createCommand.purchaseType()
                    || !delivery.getReceiverName().equals(createCommand.receiverName())
                    || !delivery.getReceiverPhone().equals(createCommand.receiverPhone())
                    || !delivery.getZipCode().equals(createCommand.zipCode())
                    || !delivery.getAddress().equals(createCommand.address())
                    || !delivery.getAddressDetail().equals(createCommand.addressDetail())
            ) {
                throw new IllegalArgumentException(
                        "동일한 멱등키에 다른 배송 요청이 전달되었습니다."
                );
            }
            return;
        }

        Delivery delivery = Delivery.create(
                createCommand.memberId(),
                createCommand.sellerId(),
                createCommand.orderId(),
                createCommand.purchaseType(),
                createCommand.receiverName(),
                createCommand.receiverPhone(),
                createCommand.zipCode(),
                createCommand.address(),
                createCommand.addressDetail(),
                createCommand.idempotencyKey()
                );

        deliveryRepository.save(delivery);

        for (DeliveryCreateCommand.Item item : createCommand.deliveryItem()) {
            DeliveryItem deliveryItem = DeliveryItem.create(
                    delivery,
                    item.orderItemId(), item.quantity()
            );

            deliveryItemRepository.save(deliveryItem);
        }
    }

    private void validatedCreate(DeliveryCreateCommand createCommand) {
        if (createCommand == null) {
            throw new IllegalArgumentException("배송 생성 정보가 필요합니다.");
        }
        if (createCommand.memberId() == null) {
            throw new IllegalArgumentException("회원 ID가 필요합니다.");
        }

        if (createCommand.sellerId() == null) {
            throw new IllegalArgumentException("판매자 ID가 필요합니다.");
        }

        if (createCommand.orderId() == null) {
            throw new IllegalArgumentException("주문 ID가 필요합니다.");
        }
        if (createCommand.purchaseType() == null) {
            throw new IllegalArgumentException("배송 유형이 필요합니다.");
        }
        if (createCommand.receiverName() == null || createCommand.receiverName().isBlank()) {
            throw new IllegalArgumentException("수령인 이름이 필요합니다.");
        }
        if (createCommand.receiverPhone() == null || createCommand.receiverPhone().isBlank()) {
            throw new IllegalArgumentException("수령인 연락처가 필요합니다.");
        }
        if (createCommand.zipCode() == null || createCommand.zipCode().isBlank()) {
            throw new IllegalArgumentException("우편번호가 필요합니다.");
        }
        if (createCommand.address() == null || createCommand.address().isBlank()) {
            throw new IllegalArgumentException("기본주소가 필요합니다.");
        }
        if (createCommand.addressDetail() == null) {
            throw new IllegalArgumentException("상세주소는 null일 수 없습니다.");
        }
        if (createCommand.deliveryItem() == null || createCommand.deliveryItem().isEmpty()) {
            throw new IllegalArgumentException("배송 상품 목록이 필요합니다.");
        }
        for (DeliveryCreateCommand.Item item : createCommand.deliveryItem()) {
            if (item == null) {
                throw new IllegalArgumentException("배송 상품 목록에 null을 포함할 수 없습니다.");
            }
            if (item.orderItemId() == null) {
                throw new IllegalArgumentException("주문상품 ID가 필요합니다.");
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("배송 수량은 0보다 커야 합니다.");
            }
        }

        if (createCommand.idempotencyKey() == null || createCommand.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("멱등키가 필요합니다.");
        }
    }
}
