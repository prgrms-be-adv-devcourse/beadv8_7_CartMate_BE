package com.cartmate.cartmate.boundedContext.orderService.abuse.simulation;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseAccountLink;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseMemberState;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseAccountLinkRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
@EnabledIfEnvironmentVariable(named = "ABUSE_SIMULATION", matches = "true")
class AbuseSimulationTest {

    static final Path REPORT = Path.of("build", "reports", "abuse-simulation.md");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AbuseMemberStateRepository abuseMemberStateRepository;

    @Autowired
    private AbuseAccountLinkRepository abuseAccountLinkRepository;

    @Test
    @DisplayName("정답을 아는 회원 집단을 실제 소비 경로로 흘려 리포트를 내고, 정상 · 가족은 제한되지 않고 어뷰저는 모두 제한되는지 확인한다")
    void simulate() throws IOException {
        AbuseSimulationScenario scenario = new AbuseSimulationScenario(LocalDateTime.now());
        Map<Long, SimulatedMember> members = new HashMap<>();
        scenario.members().forEach(member -> members.put(member.id(), member));
        Map<Long, Integer> actionCounts = new HashMap<>();
        Map<Long, Integer> actionsUntilRestricted = new HashMap<>();

        for (SimulatedAction action : scenario.actions()) {
            eventPublisher.publishEvent(relayed(action));
            for (Long actor : action.actorIds()) {
                int count = actionCounts.merge(actor, 1, Integer::sum);
                if (!actionsUntilRestricted.containsKey(actor) && restricted(actor)) {
                    actionsUntilRestricted.put(actor, count);
                }
            }
        }

        String report = report(members, actionCounts, actionsUntilRestricted);
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report);
        System.out.println(report);

        assertThat(REPORT).exists();
        assertThat(membersOf(members, type -> !type.abuser() && type != SimulatedMemberType.BORDERLINE))
                .as("정상 · 가족 회원은 제한되지 않는다")
                .noneMatch(m -> actionsUntilRestricted.containsKey(m.id()));
        assertThat(membersOf(members, SimulatedMemberType::abuser))
                .as("단독 어뷰저 · 다계정 무리는 모두 제한된다")
                .allMatch(m -> actionsUntilRestricted.containsKey(m.id()));
    }

    private boolean restricted(Long memberId) {
        return abuseMemberStateRepository.findById(memberId)
                .map(AbuseMemberState::getRestrictedUntil)
                .filter(until -> until.isAfter(LocalDateTime.now()))
                .isPresent();
    }

    private Optional<AbuseLevel> level(Long memberId) {
        return abuseMemberStateRepository.findById(memberId).map(AbuseMemberState::getCurrentLevel);
    }

    private String report(Map<Long, SimulatedMember> members, Map<Long, Integer> actionCounts,
                          Map<Long, Integer> actionsUntilRestricted) {
        List<AbuseAccountLink> links = abuseAccountLinkRepository.findAll();
        StringBuilder out = new StringBuilder();
        out.append("# 어뷰징 시뮬레이션 리포트\n\n");
        out.append("- 시드: ").append(AbuseSimulationScenario.SEED).append(" · 회원 ").append(members.size())
                .append("명 · 행동 ").append(actionCounts.values().stream().mapToInt(Integer::intValue).sum()).append("건\n");
        out.append("- 기준: 주의 30 · 의심 60 · 제한 100점, 신호당 점수 = ceil(100 ÷ 30일 허용 횟수), 무리 가산 50%(상한 100)\n\n");
        out.append("## 유형별 결과\n\n");
        out.append("| 유형 | 인원 | 제한 | 제한 비율 | 의심 | 주의 | 제한까지 걸린 행동 수(중앙값 / 최대) |\n");
        out.append("|---|---|---|---|---|---|---|\n");
        for (SimulatedMemberType type : SimulatedMemberType.values()) {
            List<SimulatedMember> group = members.values().stream().filter(m -> m.type() == type).toList();
            long restrictedCount = count(group, m -> actionsUntilRestricted.containsKey(m.id()) || restricted(m.id()));
            long suspicious = count(group, m -> level(m.id()).filter(l -> l == AbuseLevel.SUSPICIOUS).isPresent());
            long caution = count(group, m -> level(m.id()).filter(l -> l == AbuseLevel.CAUTION).isPresent());
            List<Integer> untils = group.stream().map(m -> actionsUntilRestricted.get(m.id()))
                    .filter(v -> v != null).sorted().toList();
            String until = untils.isEmpty() ? "-" : untils.get(untils.size() / 2) + " / " + untils.getLast();
            out.append("| ").append(type.label()).append(" | ").append(group.size()).append(" | ")
                    .append(restrictedCount).append(" | ").append(percent(restrictedCount, group.size())).append(" | ")
                    .append(suspicious).append(" | ").append(caution).append(" | ").append(until).append(" |\n");
        }
        out.append("\n## 다계정 무리\n\n");
        out.append("| 무리 | 계정 수 | 연결된 계정 | 제한된 계정 | 제한까지 걸린 행동 수 |\n|---|---|---|---|---|\n");
        members.values().stream().filter(m -> m.ringId() != null).map(SimulatedMember::ringId).distinct().sorted()
                .forEach(ringId -> {
                    List<SimulatedMember> ring = members.values().stream()
                            .filter(m -> ringId.equals(m.ringId())).sorted(Comparator.comparingLong(SimulatedMember::id)).toList();
                    long linked = count(ring, m -> links.stream().anyMatch(l -> l.getMemberLowId() == m.id() || l.getMemberHighId() == m.id()));
                    long restrictedCount = count(ring, m -> actionsUntilRestricted.containsKey(m.id()));
                    List<String> untils = new ArrayList<>();
                    ring.forEach(m -> untils.add(String.valueOf(actionsUntilRestricted.getOrDefault(m.id(), 0))));
                    out.append("| ").append(ringId).append(" | ").append(ring.size()).append(" | ").append(linked)
                            .append(" | ").append(restrictedCount).append(" | ").append(String.join(", ", untils)).append(" |\n");
                });
        long falseLinks = links.stream()
                .filter(l -> !sameRing(members.get(l.getMemberLowId()), members.get(l.getMemberHighId())))
                .count();
        long familyLinks = links.stream()
                .filter(l -> members.get(l.getMemberLowId()).type() == SimulatedMemberType.FAMILY
                        && members.get(l.getMemberHighId()).type() == SimulatedMemberType.FAMILY)
                .count();
        out.append("\n## 연결\n\n");
        out.append("- 전체 연결 ").append(links.size()).append("개 · 무리 밖 연결 ").append(falseLinks)
                .append("개(그중 가족 ").append(familyLinks).append("개)\n");
        return out.toString();
    }

    private static List<SimulatedMember> membersOf(Map<Long, SimulatedMember> members,
                                                   Predicate<SimulatedMemberType> type) {
        return members.values().stream().filter(m -> type.test(m.type())).toList();
    }

    private static boolean sameRing(SimulatedMember a, SimulatedMember b) {
        return a.ringId() != null && a.ringId().equals(b.ringId());
    }

    private static long count(List<SimulatedMember> group, Predicate<SimulatedMember> predicate) {
        return group.stream().filter(predicate).count();
    }

    private static String percent(long part, int whole) {
        return whole == 0 ? "-" : String.format("%.0f%%", part * 100.0 / whole);
    }

    private static OutboxMessageRelayed relayed(SimulatedAction action) {
        var event = action.event();
        return new OutboxMessageRelayed(new EventEnvelope(UUID.randomUUID().toString(),
                event.getClass().getSimpleName(), "simulation", event.aggregateType(), event.aggregateId(),
                LocalDateTime.now(), 1, "{}"), event);
    }
}
