package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItemOption;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemOptionRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeliveryCreateUseCase {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryItemRepository deliveryItemRepository;
    private final DeliveryItemOptionRepository deliveryItemOptionRepository;

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
                throw new ServiceException(DeliveryErrorCode.DELIVERY_IDEMPOTENCY_CONFLICT);
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
                    item.orderItemId(), item.productName(), item.quantity()
            );

            deliveryItemRepository.save(deliveryItem);

            if (item.options() == null) {
                continue;
            }
            List<DeliveryItemOption> options = new ArrayList<>();
            for (int i = 0; i < item.options().size(); i++) {
                DeliveryCreateCommand.Option option = item.options().get(i);
                options.add(DeliveryItemOption.create(
                        deliveryItem, option.optionName(), option.optionValue(), i
                ));
            }
            deliveryItemOptionRepository.saveAll(options);
        }
    }

    private void validatedCreate(DeliveryCreateCommand createCommand) {
        if (createCommand == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CREATE_COMMAND_REQUIRED);
        }
        if (createCommand.memberId() == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_MEMBER_ID_REQUIRED);
        }

        if (createCommand.sellerId() == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_SELLER_ID_REQUIRED);
        }

        if (createCommand.orderId() == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ORDER_ID_REQUIRED);
        }
        if (createCommand.purchaseType() == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_PURCHASE_TYPE_REQUIRED);
        }
        if (createCommand.receiverName() == null || createCommand.receiverName().isBlank()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_RECEIVER_NAME_REQUIRED);
        }
        if (createCommand.receiverPhone() == null || createCommand.receiverPhone().isBlank()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_RECEIVER_PHONE_REQUIRED);
        }
        if (createCommand.zipCode() == null || createCommand.zipCode().isBlank()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ZIP_CODE_REQUIRED);
        }
        if (createCommand.address() == null || createCommand.address().isBlank()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ADDRESS_REQUIRED);
        }
        if (createCommand.addressDetail() == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ADDRESS_DETAIL_REQUIRED);
        }
        if (createCommand.deliveryItem() == null || createCommand.deliveryItem().isEmpty()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ITEMS_REQUIRED);
        }
        for (DeliveryCreateCommand.Item item : createCommand.deliveryItem()) {
            if (item == null) {
                throw new ServiceException(DeliveryErrorCode.DELIVERY_ITEMS_CONTAIN_NULL);
            }
            if (item.orderItemId() == null) {
                throw new ServiceException(DeliveryErrorCode.DELIVERY_ORDER_ITEM_ID_REQUIRED);
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new ServiceException(DeliveryErrorCode.DELIVERY_INVALID_QUANTITY);
            }
            if (item.options() != null) {
                for (DeliveryCreateCommand.Option option : item.options()) {
                    if (option == null) {
                        throw new ServiceException(DeliveryErrorCode.DELIVERY_OPTION_CONTAIN_NULL);
                    }
                    if (option.optionName() == null || option.optionName().isBlank()) {
                        throw new ServiceException(DeliveryErrorCode.DELIVERY_OPTION_NAME_REQUIRED);
                    }
                    if (option.optionValue() == null || option.optionValue().isBlank()) {
                        throw new ServiceException(DeliveryErrorCode.DELIVERY_OPTION_VALUE_REQUIRED);
                    }
                }
            }
        }

        if (createCommand.idempotencyKey() == null || createCommand.idempotencyKey().isBlank()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_IDEMPOTENCY_KEY_REQUIRED);
        }
    }
}
