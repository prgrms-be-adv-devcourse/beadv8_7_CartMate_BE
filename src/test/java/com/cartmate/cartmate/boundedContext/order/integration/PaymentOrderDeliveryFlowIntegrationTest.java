package com.cartmate.cartmate.boundedContext.order.integration;

import com.cartmate.cartmate.boundedContext.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.app.personal.CartFacade;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartItemResponse;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.cart.out.personal.CartRepository;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemOptionRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderStatus;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.boundedContext.order.dto.personal.DeliveryAddressRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderCreateRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderCreateResponse;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletTransactionRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class PaymentOrderDeliveryFlowIntegrationTest {

    private static final DeliveryAddressRequest ADDRESS =
            new DeliveryAddressRequest("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    private static final TeamDeliveryAddress TEAM_ADDRESS =
            new TeamDeliveryAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", null);

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    @Autowired
    private CartFacade cartFacade;

    @Autowired
    private SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @Autowired
    private WalletFacade walletFacade;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private SharedGroupPurchaseRepository sharedGroupPurchaseRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryItemRepository deliveryItemRepository;

    @Autowired
    private DeliveryItemOptionRepository deliveryItemOptionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> orderIds = new ArrayList<>();
    private final List<Long> sharedGroupOrderIds = new ArrayList<>();
    private final List<Long> purchaseIds = new ArrayList<>();
    private final List<PaymentKey> paymentKeys = new ArrayList<>();

    private final long base = 5_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            paymentKeys.forEach(key -> paymentRepository
                    .findByTypeAndOrderIdAndMemberId(key.type(), key.orderId(), key.memberId())
                    .ifPresent(payment -> {
                        holdingRepository.findByOrderIdAndPaymentId(key.orderId(), payment.getId())
                                .ifPresent(holdingRepository::delete);
                        paymentRepository.delete(payment);
                    }));
            memberIds.forEach(memberId -> {
                List<Delivery> deliveries = deliveriesOf(memberId);
                if (!deliveries.isEmpty()) {
                    List<DeliveryItem> items = deliveryItemRepository.findByDeliveryIdIn(
                            deliveries.stream().map(Delivery::getId).toList());
                    if (!items.isEmpty()) {
                        deliveryItemOptionRepository.deleteAll(deliveryItemOptionRepository
                                .findByDeliveryItemIdInOrderByDeliveryItemIdAscSortOrderAsc(
                                        items.stream().map(DeliveryItem::getId).toList()));
                        deliveryItemRepository.deleteAll(items);
                    }
                    deliveryRepository.deleteAll(deliveries);
                }
                walletRepository.findByMemberId(memberId).ifPresent(wallet -> {
                    walletTransactionRepository.deleteAll(walletTransactionRepository
                            .findByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId(), Pageable.unpaged()).getContent());
                    walletRepository.delete(wallet);
                });
                cartRepository.findByMemberId(memberId).ifPresent(cartRepository::delete);
            });
            orderIds.forEach(orderRepository::deleteById);
            sharedGroupOrderIds.forEach(sharedGroupOrderRepository::deleteById);
            purchaseIds.forEach(sharedGroupPurchaseRepository::deleteById);
        });
    }

    @Test
    @DisplayName("개인 주문을 결제하면 주문은 결제 완료, 예치금 차감, 배송 생성, 커밋 후 주문한 장바구니 상품 삭제가 모두 일어난다")
    void payPersonalOrder() {
        long buyer = nextMember();
        createWallet(buyer, 200_000L);
        OrderCreateResponse created = createOrderFromCart(buyer);

        PaymentResponse payment = paymentFacade.payOrder(buyer, created.orderId());

        Order order = orderRepository.findById(created.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentId()).isEqualTo(payment.paymentId());
        assertThat(payment.amount()).isEqualTo(103_000L);
        assertThat(walletFacade.getWallet(buyer).balance()).isEqualTo(97_000L);
        assertThat(deliveriesOf(buyer)).singleElement()
                .satisfies(delivery -> assertThat(delivery.getOrderId()).isEqualTo(created.orderId()));
        assertThat(cartFacade.getCart(buyer).items()).extracting(CartItemResponse::inventoryId).containsExactly(2L);
    }

    @Test
    @DisplayName("같은 주문을 두 번 결제하면 기존 결제를 돌려주고 예치금과 배송은 한 번만 처리된다")
    void payPersonalOrderTwice() {
        long buyer = nextMember();
        createWallet(buyer, 200_000L);
        OrderCreateResponse created = createOrderFromCart(buyer);

        PaymentResponse first = paymentFacade.payOrder(buyer, created.orderId());
        PaymentResponse second = paymentFacade.payOrder(buyer, created.orderId());

        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(walletFacade.getWallet(buyer).balance()).isEqualTo(97_000L);
        assertThat(deliveriesOf(buyer)).hasSize(1);
    }

    @Test
    @DisplayName("예치금이 부족하면 결제가 실패하고 결제, 주문, 배송, 장바구니가 모두 결제 전 상태로 남는다")
    void payPersonalOrderWithInsufficientBalance() {
        long buyer = nextMember();
        createWallet(buyer, 10_000L);
        OrderCreateResponse created = createOrderFromCart(buyer);

        assertThatThrownBy(() -> paymentFacade.payOrder(buyer, created.orderId()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE.getMsg());

        Order order = orderRepository.findById(created.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getPaymentId()).isNull();
        assertThat(paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL, created.orderId(), buyer))
                .isEmpty();
        assertThat(walletFacade.getWallet(buyer).balance()).isEqualTo(10_000L);
        assertThat(deliveriesOf(buyer)).isEmpty();
        assertThat(cartFacade.getCart(buyer).items()).hasSize(3);
    }

    @Test
    @DisplayName("공동 주문은 마지막 참여자가 결제할 때 확정되고 배송이 한 번만 만들어진다")
    void paySharedGroupOrder() {
        long creator = nextMember();
        long member = nextMember();
        createWallet(creator, 100_000L);
        createWallet(member, 100_000L);
        var purchase = sharedGroupPurchaseFacade.create(creator, new SharedGroupPurchaseCreateRequest("결제 흐름 통합", 5));
        purchaseIds.add(purchase.id());
        sharedGroupPurchaseFacade.join(member, purchase.inviteCode());
        sharedGroupPurchaseFacade.addItem(member, purchase.id(), 4L, 3);
        sharedGroupPurchaseFacade.close(creator, purchase.id());
        Long orderId = sharedGroupOrderFacade.getMyList(member).stream()
                .filter(order -> order.sharedGroupPurchaseId().equals(purchase.id()))
                .map(SharedGroupOrderSummaryResponse::id)
                .findFirst()
                .orElseThrow();
        sharedGroupOrderIds.add(orderId);
        paymentKeys.add(new PaymentKey(PaymentType.GROUP_SHARE, orderId, creator));
        paymentKeys.add(new PaymentKey(PaymentType.GROUP_SHARE, orderId, member));

        paymentFacade.paySharedGroupOrder(member, orderId, null);

        assertThat(sharedGroupOrderFacade.getDetail(creator, orderId).status()).isEqualTo(SharedGroupOrderStatus.PAYING);
        assertThat(deliveriesOf(creator)).isEmpty();
        assertThat(walletFacade.getWallet(member).balance()).isEqualTo(62_500L);

        PaymentResponse last = paymentFacade.paySharedGroupOrder(creator, orderId, TEAM_ADDRESS);
        PaymentResponse retried = paymentFacade.paySharedGroupOrder(creator, orderId, TEAM_ADDRESS);

        assertThat(retried.paymentId()).isEqualTo(last.paymentId());
        assertThat(sharedGroupOrderFacade.getDetail(creator, orderId).status()).isEqualTo(SharedGroupOrderStatus.CONFIRMED);
        assertThat(walletFacade.getWallet(creator).balance()).isEqualTo(62_500L);
        assertThat(deliveriesOf(creator)).singleElement()
                .satisfies(delivery -> assertThat(delivery.getOrderId()).isEqualTo(orderId));
        assertThat(deliveriesOf(member)).isEmpty();
    }

    private OrderCreateResponse createOrderFromCart(long buyer) {
        cartFacade.addItem(buyer, 1L, 2);
        cartFacade.addItem(buyer, 2L, 1);
        cartFacade.addItem(buyer, 4L, 1);
        List<Long> orderedCartItemIds = cartFacade.getCart(buyer).items().stream()
                .filter(item -> item.inventoryId() != 2L)
                .map(CartItemResponse::cartItemId)
                .toList();
        OrderCreateResponse created = orderFacade.createPersonal(buyer, new OrderCreateRequest(orderedCartItemIds, ADDRESS));
        orderIds.add(created.orderId());
        paymentKeys.add(new PaymentKey(PaymentType.INDIVIDUAL, created.orderId(), buyer));
        return created;
    }

    private void createWallet(long memberId, long balance) {
        Wallet wallet = Wallet.createWallet(memberId);
        wallet.charge(balance);
        walletRepository.save(wallet);
    }

    private List<Delivery> deliveriesOf(long memberId) {
        return deliveryRepository.findByMemberIdOrderByCreatedAtDescIdDesc(memberId, Pageable.unpaged()).getContent();
    }

    private long nextMember() {
        long memberId = base + sequence++;
        memberIds.add(memberId);
        return memberId;
    }

    private record PaymentKey(PaymentType type, Long orderId, Long memberId) {
    }
}
