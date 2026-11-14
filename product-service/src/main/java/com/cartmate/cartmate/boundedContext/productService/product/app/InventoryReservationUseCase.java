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
 * 재고 선점 · 선점 해제 (주문 결제 직전 / 결제 실패), 판매 확정 (결제 완료), 선점 해제 · 재고 복구 (주문 취소).
 * 선점은 조건부 UPDATE(남은 수량이 충분할 때만 선점 수량 증가)라 별도 락이 없다.
 * 주문 하나의 재고는 한 트랜잭션에서 재고 ID 순서로 처리한다 → 하나라도 부족하면 전부 롤백, 주문끼리 데드락 없음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationUseCase {
    // 주문 테이블(Order) 하나를 같이 쓰는 주문 종류
    private static final List<OrderType> ORDER_TABLE_TYPES = List.of(OrderType.PERSONAL, OrderType.INDIVIDUAL_GROUP);

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

        // 주문 · 재고마다 한 줄: 선점 해제된 옛 기록(결제 실패 후 다시 결제)이 있으면 다시 쓰고, 없으면 새로 만든다
        Map<Long, InventoryReservation> existingByInventoryId = existing.stream()
                .collect(Collectors.toMap(InventoryReservation::getInventoryId, Function.identity()));
        List<InventoryReservation> reservations = quantityByInventoryId.entrySet().stream()
                .map(entry -> {
                    InventoryReservation old = existingByInventoryId.get(entry.getKey());
                    if (old == null) {
                        return new InventoryReservation(request.orderType(), request.orderId(), entry.getKey(),
                                sellerIdByInventoryId.get(entry.getKey()), entry.getValue());
                    }
                    old.reserveAgain(entry.getValue());
                    return old;
                })
                .toList();
        // 바로 flush: 같은 주문이 동시에 선점되면 여기서 유니크 키 · 버전 충돌이 나서 이 트랜잭션 전체(재고 선점 포함)가 롤백된다
        inventoryReservationRepository.saveAllAndFlush(reservations);
    }

    // 그 주문의 살아 있는 선점을 푼다. 없으면(이미 풀림 · 확정 · 선점 안 함) 아무것도 하지 않는다
    @Transactional
    public void release(OrderType orderType, Long orderId) {
        List<InventoryReservation> reserved = inventoryReservationRepository
                .findByOrderTypeAndOrderIdOrderByInventoryIdAsc(orderType, orderId).stream()
                .filter(InventoryReservation::isReserved)
                .toList();
        reserved.forEach(this::releaseOne);
        // 조건부 UPDATE가 영속성 컨텍스트를 비워서(clearAutomatically) 기록 상태 변경은 saveAll로 저장한다
        inventoryReservationRepository.saveAll(reserved);
    }

    // 판매 확정(결제 완료 이벤트): 선점한 수량을 재고에서 뺀다. 이미 확정 · 해제된 기록은 건너뛴다(이벤트 중복 · 순서 뒤바뀜)
    @Transactional
    public void confirm(OrderType orderType, Long orderId) {
        List<InventoryReservation> reserved = inventoryReservationRepository
                .findByOrderTypeAndOrderIdOrderByInventoryIdAsc(orderType, orderId).stream()
                .filter(InventoryReservation::isReserved)
                .toList();
        for (InventoryReservation reservation : reserved) {
            if (inventoryRepository.confirm(reservation.getInventoryId(), reservation.getQuantity()) == 0) {
                log.warn("판매 확정: 재고 · 선점 수량이 기록보다 적음 — 확인 필요. order={} #{} inventory #{} quantity={}",
                        orderType, orderId, reservation.getInventoryId(), reservation.getQuantity());
            }
            reservation.confirm();
        }
        inventoryReservationRepository.saveAll(reserved);
    }

    // 주문 취소 이벤트: 그 주문의 재고 전부
    @Transactional
    public void cancel(OrderType orderType, Long orderId) {
        cancelAll(inventoryReservationRepository.findByOrderTypeAndOrderIdOrderByInventoryIdAsc(orderType, orderId));
    }

    // 판매자 몫 취소 이벤트: 그 판매자 재고만. 이 이벤트에는 주문 종류가 없어
    // 같은 주문 테이블(Order)을 쓰는 일반 주문 · 개인 공동구매 중에서 찾는다(주문 ID가 겹치지 않음)
    @Transactional
    public void cancelSeller(Long orderId, Long sellerId) {
        cancelAll(inventoryReservationRepository
                .findByOrderTypeInAndOrderIdOrderByInventoryIdAsc(ORDER_TABLE_TYPES, orderId).stream()
                .filter(reservation -> sellerId.equals(reservation.getSellerId()))
                .toList());
    }

    // 선점 상태 → 선점 해제(결제 전 취소, 또는 결제 완료 이벤트보다 취소가 먼저 온 경우), 판매 확정 상태 → 재고 복구
    // 이미 해제 · 복구된 기록은 건너뛴다(이벤트 중복)
    private void cancelAll(List<InventoryReservation> reservations) {
        List<InventoryReservation> targets = reservations.stream()
                .filter(reservation -> reservation.isReserved() || reservation.isConfirmed())
                .toList();
        for (InventoryReservation reservation : targets) {
            if (reservation.isReserved()) {
                releaseOne(reservation);
            } else {
                inventoryRepository.restore(reservation.getInventoryId(), reservation.getQuantity());
                reservation.restore();
            }
        }
        inventoryReservationRepository.saveAll(targets);
    }

    private void releaseOne(InventoryReservation reservation) {
        if (inventoryRepository.release(reservation.getInventoryId(), reservation.getQuantity()) == 0) {
            log.warn("선점 해제: 재고의 선점 수량이 기록보다 적음 — 확인 필요. order={} #{} inventory #{} quantity={}",
                    reservation.getOrderType(), reservation.getOrderId(), reservation.getInventoryId(),
                    reservation.getQuantity());
        }
        reservation.release();
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
