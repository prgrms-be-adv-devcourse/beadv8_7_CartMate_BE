package com.cartmate.cartmate.domain.cart.groupindividual.dto;

import com.cartmate.cartmate.domain.cart.client.CartProductOptions;
import com.cartmate.cartmate.domain.cart.groupindividual.client.IndividualGroupPurchaseOrderLine;
import com.cartmate.cartmate.domain.cart.groupindividual.entity.IndividualGroupPurchase;
import com.cartmate.cartmate.domain.cart.groupindividual.entity.IndividualGroupPurchaseProduct;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public record IndividualGroupPurchaseDashboardResponse(
        Long id,
        String title,
        boolean open,
        LocalDateTime deadlineAt,
        int orderCount,
        int buyerCount,
        int totalQuantity,
        long totalAmount,
        List<ProductSummary> products,
        List<BuyerSummary> buyers
) {


    public static IndividualGroupPurchaseDashboardResponse of(IndividualGroupPurchase purchase,
                                                              Map<Long, CartProductOptions> productOptions,
                                                              List<IndividualGroupPurchaseOrderLine> lines,
                                                              LocalDateTime now) {
        List<ProductSummary> products = purchase.getProducts().stream()
                .map(IndividualGroupPurchaseProduct::getProductId)
                .map(productId -> ProductSummary.of(productId, productOptions.get(productId), lines))
                .toList();

        Map<Long, String> optionNames = productOptions.values().stream()
                .flatMap(info -> info.options().stream())
                .collect(Collectors.toMap(CartProductOptions.Option::inventoryId, CartProductOptions.Option::optionName,
                        (first, second) -> first));
        Map<Long, String> productNames = productOptions.values().stream()
                .collect(Collectors.toMap(CartProductOptions::productId, CartProductOptions::productName));

        return new IndividualGroupPurchaseDashboardResponse(
                purchase.getId(),
                purchase.getTitle(),
                purchase.isOpen(now),
                purchase.getDeadlineAt(),
                (int) lines.stream().map(IndividualGroupPurchaseOrderLine::orderId).distinct().count(),
                (int) lines.stream().map(IndividualGroupPurchaseOrderLine::buyerMemberId).distinct().count(),
                lines.stream().mapToInt(IndividualGroupPurchaseOrderLine::quantity).sum(),
                lines.stream().mapToLong(IndividualGroupPurchaseOrderLine::amount).sum(),
                products,
                BuyerSummary.groupByBuyer(lines, productNames, optionNames)
        );
    }

    public record ProductSummary(
            Long productId,
            String productName,
            int quantity,
            long amount,
            List<OptionSummary> options
    ) {

        static ProductSummary of(Long productId, CartProductOptions info, List<IndividualGroupPurchaseOrderLine> lines) {
            List<IndividualGroupPurchaseOrderLine> productLines = lines.stream()
                    .filter(line -> line.productId().equals(productId))
                    .toList();

            List<OptionSummary> options = new ArrayList<>();
            List<Long> knownInventoryIds = new ArrayList<>();
            if (info != null) {
                for (CartProductOptions.Option option : info.options()) {
                    options.add(OptionSummary.of(option.inventoryId(), option.optionName(), productLines));
                    knownInventoryIds.add(option.inventoryId());
                }
            }
            productLines.stream()
                    .map(IndividualGroupPurchaseOrderLine::inventoryId)
                    .distinct()
                    .filter(inventoryId -> !knownInventoryIds.contains(inventoryId))
                    .forEach(inventoryId -> options.add(OptionSummary.of(inventoryId, null, productLines)));

            return new ProductSummary(
                    productId,
                    info == null ? null : info.productName(),
                    productLines.stream().mapToInt(IndividualGroupPurchaseOrderLine::quantity).sum(),
                    productLines.stream().mapToLong(IndividualGroupPurchaseOrderLine::amount).sum(),
                    List.copyOf(options)
            );
        }
    }

    public record OptionSummary(
            Long inventoryId,
            String optionName,
            int quantity,
            long amount
    ) {
        static OptionSummary of(Long inventoryId, String optionName, List<IndividualGroupPurchaseOrderLine> productLines) {
            List<IndividualGroupPurchaseOrderLine> optionLines = productLines.stream()
                    .filter(line -> line.inventoryId().equals(inventoryId))
                    .toList();
            return new OptionSummary(
                    inventoryId,
                    optionName,
                    optionLines.stream().mapToInt(IndividualGroupPurchaseOrderLine::quantity).sum(),
                    optionLines.stream().mapToLong(IndividualGroupPurchaseOrderLine::amount).sum()
            );
        }
    }

    public record BuyerSummary(
            Long memberId,
            int quantity,
            long amount,
            List<BuyerLine> lines
    ) {

        static List<BuyerSummary> groupByBuyer(List<IndividualGroupPurchaseOrderLine> lines,
                                               Map<Long, String> productNames,
                                               Map<Long, String> optionNames) {
            Map<Long, List<IndividualGroupPurchaseOrderLine>> byBuyer = lines.stream()
                    .collect(Collectors.groupingBy(IndividualGroupPurchaseOrderLine::buyerMemberId,
                            LinkedHashMap::new, Collectors.toList()));

            return byBuyer.entrySet().stream()
                    .map(entry -> new BuyerSummary(
                            entry.getKey(),
                            entry.getValue().stream().mapToInt(IndividualGroupPurchaseOrderLine::quantity).sum(),
                            entry.getValue().stream().mapToLong(IndividualGroupPurchaseOrderLine::amount).sum(),
                            entry.getValue().stream()
                                    .map(line -> BuyerLine.of(line, productNames, optionNames))
                                    .toList()))
                    .toList();
        }
    }

    public record BuyerLine(
            Long orderId,
            Long productId,
            String productName,
            Long inventoryId,
            String optionName,
            int quantity,
            long amount,
            LocalDateTime orderedAt
    ) {
        static BuyerLine of(IndividualGroupPurchaseOrderLine line, Map<Long, String> productNames, Map<Long, String> optionNames) {
            return new BuyerLine(
                    line.orderId(),
                    line.productId(),
                    productNames.get(line.productId()),
                    line.inventoryId(),
                    optionNames.get(line.inventoryId()),
                    line.quantity(),
                    line.amount(),
                    line.orderedAt()
            );
        }
    }
}
