package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "individual_group_purchase_payback")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchasePayback {

    @Id
    private Long individualGroupPurchaseId;

    @Column(nullable = false)
    private boolean paid;

    @Column(nullable = false)
    private LocalDateTime judgedAt;

    @ElementCollection
    @CollectionTable(name = "individual_group_purchase_payback_condition_judgment",
            joinColumns = @JoinColumn(name = "individual_group_purchase_id"))
    @OrderColumn(name = "line_no")
    private List<PaybackConditionJudgment> conditionJudgments = new ArrayList<>();

    private IndividualGroupPurchasePayback(Long individualGroupPurchaseId, PaybackSettlement.Result result,
                                           LocalDateTime judgedAt) {
        this.individualGroupPurchaseId = individualGroupPurchaseId;
        this.paid = result.paid();
        this.judgedAt = judgedAt;
        result.conditionResults().stream()
                .map(PaybackConditionJudgment::from)
                .forEach(conditionJudgments::add);
    }

    public static IndividualGroupPurchasePayback judge(Long individualGroupPurchaseId, PaybackSettlement.Result result,
                                                       LocalDateTime judgedAt) {
        return new IndividualGroupPurchasePayback(individualGroupPurchaseId, result, judgedAt);
    }
}
