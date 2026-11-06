package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.shared.cart.PaybackOverlapPolicy;
import com.cartmate.cartmate.shared.order.OrderStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class PaybackSettlement {

    private PaybackSettlement() {
    }

    public static Optional<List<ConfirmedLine>> confirmedLines(Collection<Order> orders, Set<Long> confirmedOrderIds) {
        List<ConfirmedLine> lines = new ArrayList<>();
        for (Order order : orders) {
            if (order.getStatus() == OrderStatus.CANCELED) {
                continue;
            }
            if (order.getStatus() != OrderStatus.PAID || !confirmedOrderIds.contains(order.getId())) {
                return Optional.empty();
            }
            order.getItems().stream()
                    .filter(item -> !item.isCanceled())
                    .forEach(item -> lines.add(
                            new ConfirmedLine(order.getBuyerMemberId(), item.getProductId(), item.getQuantity())));
        }
        return Optional.of(List.copyOf(lines));
    }

    public static Result settle(List<ConfirmedLine> lines, Collection<ReplicaPaybackCondition> conditions,
                                PaybackOverlapPolicy overlapPolicy) {
        PaybackOverlapPolicy policy = overlapPolicy == null ? PaybackOverlapPolicy.BOTH : overlapPolicy;
        Map<Long, Map<Long, Integer>> quantityByProductAndBuyer = new TreeMap<>();
        lines.forEach(line -> quantityByProductAndBuyer
                .computeIfAbsent(line.productId(), productId -> new TreeMap<>())
                .merge(line.buyerMemberId(), line.quantity(), Integer::sum));

        Map<Long, List<ReplicaPaybackCondition>> conditionsByProduct = conditions.stream()
                .collect(Collectors.groupingBy(ReplicaPaybackCondition::getProductId, TreeMap::new,
                        Collectors.toList()));

        List<ConditionResult> results = new ArrayList<>();
        List<PayoutLine> payouts = new ArrayList<>();
        conditionsByProduct.forEach((productId, productConditions) -> {
            Map<Long, Integer> quantityByBuyer = quantityByProductAndBuyer.getOrDefault(productId, Map.of());
            int confirmedQuantity = quantityByBuyer.values().stream().mapToInt(Integer::intValue).sum();
            List<ReplicaPaybackCondition> reached = productConditions.stream()
                    .filter(condition -> condition.getAmountPerUnit() > 0)
                    .filter(condition -> confirmedQuantity >= Math.max(1, condition.getMinQuantity()))
                    .toList();
            Set<ReplicaPaybackCondition> applied = applied(productConditions, reached, policy);
            productConditions.stream()
                    .sorted(Comparator.comparing(ReplicaPaybackCondition::getFunder))
                    .forEach(condition -> {
                        boolean isApplied = applied.contains(condition);
                        results.add(new ConditionResult(productId, condition.getFunder(), condition.getSellerId(),
                                condition.getMinQuantity(), condition.getAmountPerUnit(), confirmedQuantity,
                                reached.contains(condition), isApplied));
                        if (isApplied) {
                            quantityByBuyer.forEach((buyer, quantity) -> payouts.add(new PayoutLine(buyer, productId,
                                    condition.getFunder(), condition.getSellerId(), quantity,
                                    condition.getAmountPerUnit())));
                        }
                    });
        });
        return new Result(List.copyOf(results), List.copyOf(payouts));
    }

    private static Set<ReplicaPaybackCondition> applied(List<ReplicaPaybackCondition> conditions,
                                                        List<ReplicaPaybackCondition> reached,
                                                        PaybackOverlapPolicy policy) {
        boolean overlapping = conditions.stream().anyMatch(ReplicaPaybackCondition::isAdmin)
                && conditions.stream().anyMatch(condition -> !condition.isAdmin());
        if (!overlapping || policy == PaybackOverlapPolicy.BOTH) {
            return Set.copyOf(reached);
        }
        return switch (policy) {
            case ADMIN_ONLY -> reached.stream().filter(ReplicaPaybackCondition::isAdmin).collect(Collectors.toSet());
            case SELLER_ONLY -> reached.stream().filter(condition -> !condition.isAdmin())
                    .collect(Collectors.toSet());
            case LARGER -> reached.stream()
                    .max(Comparator.comparingLong(ReplicaPaybackCondition::getAmountPerUnit)
                            .thenComparing(ReplicaPaybackCondition::isAdmin))
                    .map(Set::of)
                    .orElseGet(Set::of);
            case BOTH -> Set.copyOf(reached);
        };
    }

    public record ConfirmedLine(Long buyerMemberId, Long productId, int quantity) {
    }

    public record ConditionResult(Long productId, PaybackFunder funder, Long sellerId, int minQuantity,
                                  long amountPerUnit, int confirmedQuantity, boolean reached, boolean applied) {
    }

    public record PayoutLine(Long memberId, Long productId, PaybackFunder funder, Long sellerId, int quantity,
                             long amountPerUnit) {

        public long amount() {
            return amountPerUnit * quantity;
        }
    }

    public record Result(List<ConditionResult> conditionResults, List<PayoutLine> payouts) {

        public boolean paid() {
            return !payouts.isEmpty();
        }
    }
}
