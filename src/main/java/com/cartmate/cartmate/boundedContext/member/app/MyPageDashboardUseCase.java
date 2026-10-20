package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.dto.MyPageDashboardResponse;
import com.cartmate.cartmate.boundedContext.member.dto.MyPageDashboardResponse.GroupPurchase;
import com.cartmate.cartmate.boundedContext.member.dto.MyPageDashboardResponse.RecentOrder;
import com.cartmate.cartmate.boundedContext.member.out.MyPageActivityClient;
import com.cartmate.cartmate.boundedContext.member.out.MyPageActivityClient.Delivery;
import com.cartmate.cartmate.boundedContext.member.out.MyPageActivityClient.SharedPurchase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MyPageDashboardUseCase {
    private static final int RECENT_ORDER_LIMIT = 5;
    private static final Set<String> RECRUITING_SHARED = Set.of("OPEN", "FROZEN");
    private static final String ORDER_PURCHASE_TYPE = "INDIVIDUAL_GROUP"; // orders 테이블 주문의 배송 구매 유형

    private final MyPageActivityClient activityClient;

    public MyPageDashboardResponse getDashboard(Long memberId) {
        List<Delivery> deliveries = activityClient.getDeliveries(memberId);

        return new MyPageDashboardResponse(
                new MyPageDashboardResponse.Wallet(activityClient.getWalletBalance(memberId), 0L),
                groupPurchases(memberId),
                recentOrders(memberId, deliveries),
                deliveries.stream().filter(d -> "SHIPPING".equals(d.status())).count()
        );
    }

    // 진행 중인 공동구매: 공동물품 모집·결제 중 + 개인물품 모집 중. 마감이 가까운 순
    private List<GroupPurchase> groupPurchases(Long memberId) {
        List<SharedPurchase> sharedPurchases = activityClient.getSharedPurchases(memberId);
        Map<Long, String> titles = sharedPurchases.stream()
                .collect(Collectors.toMap(SharedPurchase::id, p -> nullToDefault(p.title()), (a, b) -> a));

        List<GroupPurchase> result = new ArrayList<>();

        sharedPurchases.stream()
                .filter(p -> RECRUITING_SHARED.contains(p.status()))
                .forEach(p -> result.add(new GroupPurchase(p.id(), "SHARED", "RECRUITING", nullToDefault(p.title()),
                        0, 0L, p.participantCount(), p.maxParticipants(), null, p.editDeadlineAt(), null)));

        activityClient.getSharedOrders(memberId).stream()
                .filter(o -> "PAYING".equals(o.status()))
                .forEach(o -> result.add(new GroupPurchase(o.sharedGroupPurchaseId(), "SHARED", "PAYING",
                        titles.getOrDefault(o.sharedGroupPurchaseId(), "공동물품 구매"), 0, o.myShareAmount(),
                        o.paidCount(), o.participantCount(), o.myPaymentStatus(), o.paymentDeadlineAt(), o.id())));

        activityClient.getIndividualPurchases(memberId).stream()
                .filter(MyPageActivityClient.IndividualPurchase::open)
                .forEach(p -> result.add(new GroupPurchase(p.id(), "PER_PERSON", "RECRUITING", nullToDefault(p.title()),
                        p.productCount(), 0L, 0, 0, null, p.deadlineAt(), null)));

        result.sort(Comparator.comparing(GroupPurchase::deadlineAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    // 최근 주문 5건. 배송 상태는 그 주문의 배송 중 가장 최근 것
    private List<RecentOrder> recentOrders(Long memberId, List<Delivery> deliveries) {
        Map<Long, Delivery> latestDelivery = deliveries.stream()
                .filter(d -> ORDER_PURCHASE_TYPE.equals(d.purchaseType()))
                .collect(Collectors.toMap(Delivery::orderId, Function.identity(),
                        (a, b) -> a.createdAt().isAfter(b.createdAt()) ? a : b));

        return activityClient.getOrders(memberId).stream()
                .sorted(Comparator.comparing(MyPageActivityClient.Order::createdAt,
                        Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())).reversed())
                .limit(RECENT_ORDER_LIMIT)
                .map(o -> {
                    Delivery d = latestDelivery.get(o.id());
                    return new RecentOrder(o.id(), o.orderNumber(), o.orderType(), o.createdAt(),
                            nullToDefault(o.firstProductName()), o.itemCount(), o.totalAmount(),
                            o.status(), d == null ? null : d.status());
                })
                .toList();
    }

    private String nullToDefault(String value) {
        return value == null || value.isBlank() ? "상품 정보 없음" : value;
    }
}