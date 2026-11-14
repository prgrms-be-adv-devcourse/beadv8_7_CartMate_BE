package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Inventory;
import com.cartmate.cartmate.boundedContext.productService.product.domain.InventoryReservation;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.out.InventoryRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.InventoryReservationRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.product.dto.InventoryReserveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 재고 선점 · 선점 해제 (주문 결제 직전 / 결제 실패).
 * 선점은 조건부 UPDATE(남은 수량이 충분할 때만 선점 수량 증가)라 별도 락이 없다.
 * 주문 하나의 재고는 한 트랜잭션에서 재고 ID 순서로 처리한다 → 하나라도 부족하면 전부 롤백, 주문끼리 데드락 없음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationUseCase {
    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    @Transactional
    public void reserve(InventoryReserveRequest request) {
        validate(request);
        // 같은 주문을 다시 선점하지 않는다(결제 재시도 등). 결제 실패로 풀린 주문은 다시 선점한다
        List<InventoryReservation> existing = inventoryReservationRepository
                .findByOrderTypeAndOrderIdOrderByInventoryIdAsc(request.orderType(), request.orderId());
        if (existing.stream().anyMatch(InventoryReservation::isHolding)) {
            return;
        }

        // 같은 재고가 여러 번 오면 수량을 합친다. TreeMap = 재고 ID 순서
        Map<Long, Integer> quantityByInventoryId = request.items().stream()
                .collect(Collectors.toMap(InventoryReserveRequest.Item::inventoryId,
                        InventoryReserveRequest.Item::quantity, Integer::sum, TreeMap::new));

        Map<Long, Inventory> inventoryById = inventoryRepository.findAllByIdIn(quantityByInventoryId.keySet()).stream()
                .collect(Collectors.toMap(Inventory::getId, Function.identity()));
        Map<Long, Long> sellerIdByInventoryId = quantityByInventoryId.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), id -> purchasableSellerId(inventoryById.get(id))));

        quantityByInventoryId.forEach((inventoryId, quantity) -> {
            if (inventoryRepository.reserve(inventoryId, quantity) == 0) {
                throw new ServiceException(ProductErrorCode.PRODUCT_INVENTORY_OUT_OF_STOCK);
            }
        });

        inventoryReservationRepository.saveAll(quantityByInventoryId.entrySet().stream()
                .map(entry -> new InventoryReservation(request.orderType(), request.orderId(), entry.getKey(),
                        sellerIdByInventoryId.get(entry.getKey()), entry.getValue()))
                .toList());
    }

    // 그 주문의 살아 있는 선점을 푼다. 없으면(이미 풀림 · 확정 · 선점 안 함) 아무것도 하지 않는다
    @Transactional
    public void release(OrderType orderType, Long orderId) {
        List<InventoryReservation> reserved = inventoryReservationRepository
                .findByOrderTypeAndOrderIdOrderByInventoryIdAsc(orderType, orderId).stream()
                .filter(InventoryReservation::isReserved)
                .toList();
        for (InventoryReservation reservation : reserved) {
            if (inventoryRepository.release(reservation.getInventoryId(), reservation.getQuantity()) == 0) {
                log.warn("선점 해제: 재고의 선점 수량이 기록보다 적음 — 확인 필요. order={} #{} inventory #{} quantity={}",
                        orderType, orderId, reservation.getInventoryId(), reservation.getQuantity());
            }
            reservation.release();
        }
        inventoryReservationRepository.saveAll(reserved);
    }

    private void validate(InventoryReserveRequest request) {
        if (request.orderType() == null || request.orderId() == null
                || request.items() == null || request.items().isEmpty()
                || request.items().stream().anyMatch(item -> item.inventoryId() == null || item.quantity() <= 0)) {
            throw new ServiceException(ProductErrorCode.PRODUCT_INVENTORY_RESERVE_INVALID);
        }
    }

    // 상품 판매 중 + 재고 살아 있음일 때만 선점한다
    private Long purchasableSellerId(Inventory inventory) {
        if (inventory == null) {
            throw new ServiceException(ProductErrorCode.PRODUCT_INVENTORY_NOT_FOUND);
        }
        if (inventory.getProduct().getStatus() != ProductStatus.ON_SALE || !inventory.isActive()) {
            throw new ServiceException(ProductErrorCode.PRODUCT_INVENTORY_NOT_PURCHASABLE);
        }
        return inventory.getProduct().getSellerId();
    }
}
