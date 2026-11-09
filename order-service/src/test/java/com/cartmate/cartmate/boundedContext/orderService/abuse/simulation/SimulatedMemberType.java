package com.cartmate.cartmate.boundedContext.orderService.abuse.simulation;

enum SimulatedMemberType {
    NORMAL("정상", false),
    FAMILY("가족(주소 공유 · 정상 행동)", false),
    BORDERLINE("경계선", false),
    SOLO_ABUSER("단독 어뷰저", true),
    RING_ABUSER("다계정 무리", true);

    private final String label;
    private final boolean abuser;

    SimulatedMemberType(String label, boolean abuser) {
        this.label = label;
        this.abuser = abuser;
    }

    String label() {
        return label;
    }

    boolean abuser() {
        return abuser;
    }
}
