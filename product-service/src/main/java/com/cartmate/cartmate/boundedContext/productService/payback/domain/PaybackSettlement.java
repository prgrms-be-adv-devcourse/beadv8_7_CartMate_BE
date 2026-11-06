package com.cartmate.cartmate.boundedContext.productService.payback.domain;

import com.cartmate.cartmate.shared.product.PaybackFunder;
import com.cartmate.cartmate.shared.product.PaybackOverlapPolicy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class PaybackSettlement {

    private PaybackSettlement() {
    }

    public static Optional<List<ConfirmedLine>> confirmedLines(Collection<PaybackOrder> orders) {
        List<ConfirmedLine> lines = new ArrayList<>();
        for (PaybackOrder order : orders) {
            if (order.isCanceled()) {
                continue;
            }
            if (!order.isPaid()) {
                return Optional.empty();
            }
            for (PaybackOrderItem item : order.getItems()) {
                if (order.isSellerCanceled(item.getSellerId())) {
                    continue;
                }
                if (!order.isSellerConfirmed(item.getSellerId())) {
                    return Optional.empty();
                }
                lines.add(new ConfirmedLine(order.getBuyerMemberId(), item.getProductId(), item.getQuantity()));
            }
        }
        return Optional.of(List.copyOf(lines));
    }

    public static Result settle(List<ConfirmedLine> lines, Collection<PaybackConditionSnapshot> conditions) {
        Map<Long, Map<Long, Integer>> quantityByProductAndBuyer = new TreeMap<>();
        lines.forEach(line -> quantityByProductAndBuyer
                .computeIfAbsent(line.productId(), productId -> new TreeMap<>())
                .merge(line.buyerMemberId(), line.quantity(), Integer::sum));

        Map<Long, List<PaybackConditionSnapshot>> conditionsByProduct = conditions.stream()
                .collect(Collectors.groupingBy(PaybackConditionSnapshot::getProductId, TreeMap::new,
                        Collectors.toList()));

        List<ConditionResult> results = new ArrayList<>();
        List<PayoutLine> payouts = new ArrayList<>();
        conditionsByProduct.forEach((productId, productConditions) -> {
            Map<Long, Integer> quantityByBuyer = quantityByProductAndBuyer.getOrDefault(productId, Map.of());
            int confirmedQuantity = quantityByBuyer.values().stream().mapToInt(Integer::intValue).sum();
            List<PaybackConditionSnapshot> reached = productConditions.stream()
                    .filter(condition -> condition.getAmountPerUnit() > 0)
                    .filter(condition -> confirmedQuantity >= Math.max(1, condition.getMinQuantity()))
                    .toList();
            Set<PaybackConditionSnapshot> applied = applied(productConditions, reached, overlapPolicyOf(productConditions));
            productConditions.stream()
                    .sorted(Comparator.comparing(PaybackConditionSnapshot::getFunder))
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

    private static PaybackOverlapPolicy overlapPolicyOf(List<PaybackConditionSnapshot> conditions) {
        return conditions.stream()
                .filter(PaybackConditionSnapshot::isAdmin)
                .map(PaybackConditionSnapshot::getOverlapPolicy)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(PaybackOverlapPolicy.BOTH);
    }

    private static Set<PaybackConditionSnapshot> applied(List<PaybackConditionSnapshot> conditions,
                                                         List<PaybackConditionSnapshot> reached,
                                                         PaybackOverlapPolicy policy) {
        boolean overlapping = conditions.stream().anyMatch(PaybackConditionSnapshot::isAdmin)
                && conditions.stream().anyMatch(condition -> !condition.isAdmin());
        if (!overlapping || policy == PaybackOverlapPolicy.BOTH) {
            return Set.copyOf(reached);
        }
        return switch (policy) {
            case ADMIN_ONLY -> reached.stream().filter(PaybackConditionSnapshot::isAdmin).collect(Collectors.toSet());
            case SELLER_ONLY -> reached.stream().filter(condition -> !condition.isAdmin())
                    .collect(Collectors.toSet());
            case LARGER -> reached.stream()
                    .max(Comparator.comparingLong(PaybackConditionSnapshot::getAmountPerUnit)
                            .thenComparing(PaybackConditionSnapshot::isAdmin))
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
