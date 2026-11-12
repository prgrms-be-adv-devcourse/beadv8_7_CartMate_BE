package com.cartmate.cartmate.boundedContext.orderService.abuse.simulation;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseCancelReason;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseCanceledEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberJoinedEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberLeftEvent;
import com.cartmate.cartmate.shared.order.OrderCancelReason;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.shared.order.dto.DeliveryAddressInfo;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class AbuseSimulationScenario {

    static final long SEED = 20261109L;
    static final int NORMAL_COUNT = 200;
    static final int FAMILY_PAIRS = 10;
    static final int BORDERLINE_COUNT = 20;
    static final int SOLO_ABUSER_COUNT = 10;
    static final List<Integer> RING_SIZES = List.of(3, 4, 5);
    static final int RING_HOARDED_PURCHASES = 3;

    private final Random random = new Random(SEED);
    private final LocalDateTime base;
    private final List<SimulatedMember> members = new ArrayList<>();
    private final List<SimulatedAction> identities = new ArrayList<>();
    private final List<List<SimulatedAction>> behaviors = new ArrayList<>();
    private long nextMemberId = 1;
    private long nextPurchaseId = 1;
    private long nextOrderId = 1;
    private int sequence;

    AbuseSimulationScenario(LocalDateTime now) {
        this.base = now.minusDays(1);
        build();
    }

    List<SimulatedMember> members() {
        return members;
    }

    List<SimulatedAction> actions() {
        List<SimulatedAction> ordered = new ArrayList<>(identities);
        List<List<SimulatedAction>> queues = new ArrayList<>();
        behaviors.stream().filter(queue -> !queue.isEmpty()).forEach(queue -> queues.add(new ArrayList<>(queue)));
        while (!queues.isEmpty()) {
            int pick = random.nextInt(queues.size());
            List<SimulatedAction> queue = queues.get(pick);
            ordered.add(queue.removeFirst());
            if (queue.isEmpty()) {
                queues.remove(pick);
            }
        }
        return ordered;
    }

    private void build() {
        for (int i = 0; i < NORMAL_COUNT; i++) {
            SimulatedMember member = member(SimulatedMemberType.NORMAL, null);
            identity(member, phoneOf(member), addressOf(member));
            behaviors.add(normalBehavior(member));
        }
        for (int i = 0; i < FAMILY_PAIRS; i++) {
            SimulatedMember first = member(SimulatedMemberType.FAMILY, null);
            SimulatedMember second = member(SimulatedMemberType.FAMILY, null);
            String home = addressOf(first);
            identity(first, phoneOf(first), home);
            identity(second, phoneOf(second), home);
            List<SimulatedAction> family = new ArrayList<>();
            for (int purchase = 0; purchase < 2; purchase++) {
                long purchaseId = nextPurchaseId++;
                family.add(left(purchaseId, first));
                family.add(left(purchaseId, second));
            }
            behaviors.add(family);
        }
        for (int i = 0; i < BORDERLINE_COUNT; i++) {
            SimulatedMember member = member(SimulatedMemberType.BORDERLINE, null);
            identity(member, phoneOf(member), addressOf(member));
            List<SimulatedAction> borderline = new ArrayList<>();
            int leaves = 4 + random.nextInt(3);
            for (int leave = 0; leave < leaves; leave++) {
                borderline.add(left(nextPurchaseId++, member));
            }
            borderline.add(random.nextBoolean() ? creatorCanceled(member) : individualExpired(member));
            Collections.shuffle(borderline, random);
            behaviors.add(borderline);
        }
        for (int i = 0; i < SOLO_ABUSER_COUNT; i++) {
            SimulatedMember member = member(SimulatedMemberType.SOLO_ABUSER, null);
            identity(member, phoneOf(member), addressOf(member));
            List<SimulatedAction> solo = new ArrayList<>();
            for (int act = 0; act < 5; act++) {
                solo.add(switch (i % 3) {
                    case 0 -> sharedExpired(List.of(member));
                    case 1 -> creatorCanceled(member);
                    default -> individualExpired(member);
                });
            }
            behaviors.add(solo);
        }
        for (int ring = 0; ring < RING_SIZES.size(); ring++) {
            List<SimulatedMember> accounts = new ArrayList<>();
            for (int account = 0; account < RING_SIZES.get(ring); account++) {
                accounts.add(member(SimulatedMemberType.RING_ABUSER, ring + 1));
            }
            String sharedPhone = "010-77" + (ring + 1) + "0-0000";
            accounts.forEach(account -> identity(account, sharedPhone, addressOf(account)));
            List<SimulatedAction> hoarding = new ArrayList<>();
            for (int purchase = 0; purchase < RING_HOARDED_PURCHASES; purchase++) {
                hoarding.add(sharedExpired(accounts));
            }
            behaviors.add(hoarding);
        }
    }

    private List<SimulatedAction> normalBehavior(SimulatedMember member) {
        List<SimulatedAction> actions = new ArrayList<>();
        int leaves = random.nextInt(4);
        for (int leave = 0; leave < leaves; leave++) {
            actions.add(left(nextPurchaseId++, member));
        }
        if (random.nextDouble() < 0.05) {
            actions.add(sharedExpired(List.of(member)));
        }
        if (random.nextDouble() < 0.05) {
            actions.add(creatorCanceled(member));
        }
        if (random.nextDouble() < 0.05) {
            actions.add(individualExpired(member));
        }
        if (random.nextDouble() < 0.05) {
            actions.add(concurrentJoin(member));
        }
        Collections.shuffle(actions, random);
        return actions;
    }

    private SimulatedMember member(SimulatedMemberType type, Integer ringId) {
        SimulatedMember member = new SimulatedMember(nextMemberId++, type, ringId);
        members.add(member);
        return member;
    }

    private void identity(SimulatedMember member, String phone, String address) {
        DeliveryAddressInfo delivery = new DeliveryAddressInfo("회원" + member.id(), phone, "06236", address, "1호");
        identities.add(new SimulatedAction(new OrderPaidEvent(nextOrderId++, "SIM-" + member.id(), member.id(),
                OrderType.PERSONAL, null, at(), delivery, List.of()), List.of()));
    }

    private SimulatedAction left(long purchaseId, SimulatedMember member) {
        LocalDateTime leftAt = at();
        return action(new SharedGroupPurchaseMemberLeftEvent(purchaseId, member.id(), leftAt.minusHours(1), leftAt),
                List.of(member.id()));
    }

    private SimulatedAction creatorCanceled(SimulatedMember member) {
        return action(new SharedGroupPurchaseCanceledEvent(nextPurchaseId++, member.id(), List.of(member.id()),
                SharedGroupPurchaseCancelReason.CREATOR_CANCELED, at()), List.of(member.id()));
    }

    private SimulatedAction individualExpired(SimulatedMember member) {
        return action(new OrderCanceledEvent(nextOrderId++, member.id(), OrderType.INDIVIDUAL_GROUP,
                OrderCancelReason.PAYMENT_EXPIRED, false, at(), null), List.of(member.id()));
    }

    private SimulatedAction concurrentJoin(SimulatedMember member) {
        return action(new SharedGroupPurchaseMemberJoinedEvent(nextPurchaseId++, member.id(), 5, at()),
                List.of(member.id()));
    }

    private SimulatedAction sharedExpired(List<SimulatedMember> unpaid) {
        long purchaseId = nextPurchaseId++;
        List<Long> ids = unpaid.stream().map(SimulatedMember::id).toList();
        return action(new SharedGroupOrderCanceledEvent(nextOrderId++, purchaseId,
                SharedGroupOrderCancelReason.DEADLINE_EXPIRED, at(), ids, null), ids);
    }

    private SimulatedAction action(DomainEvent event, List<Long> actors) {
        return new SimulatedAction(event, actors);
    }

    private LocalDateTime at() {
        return base.plusSeconds(sequence++);
    }

    private static String phoneOf(SimulatedMember member) {
        return String.format("010-%04d-%04d", 1000 + member.id() / 10000, member.id() % 10000);
    }

    private static String addressOf(SimulatedMember member) {
        return "서울 시뮬구 " + member.id() + "길";
    }
}
