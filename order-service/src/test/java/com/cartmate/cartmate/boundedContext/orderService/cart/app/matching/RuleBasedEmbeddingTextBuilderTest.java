package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedEmbeddingTextBuilderTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("브랜드 · 모델명 · 크기 · 연령 · 홍보 문구를 빼고 동의어를 맞춘다")
    @CsvSource(delimiter = '|', value = {
            "나이키 머큐리얼 축구화 FG 천연잔디용 | 축구화",
            "레알 마드리드 어웨이 레플리카 저지 | 레알 마드리드 어웨이 레플리카 유니폼",
            "축구 신가드 슬립인 타입 성인용 L | 축구 정강이 보호대",
            "풋살공 4호 저반발 (실내용) | 풋살공 저반발",
            "스포츠 물병 1L BPA 프리 스퀴즈 보틀 | 스포츠 물병 스퀴즈 물병",
            "아동 축구화 찍찍이 벨크로 200mm | 축구화 찍찍이 벨크로",
            "축구 스타킹 프리미엄 | 축구 스타킹 프리미엄",
            "저지방 프로틴바 3개월분 | 저지방 프로틴바 3개월분",
            "스타 축구공 5호 | 축구공"
    })
    void normalize(String name, String expected) {
        assertThat(RuleBasedEmbeddingTextBuilder.normalize(name)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("전부 지워지면 원래 이름을 쓴다")
    @CsvSource({"나이키", "'  '"})
    void fallbackToName(String name) {
        assertThat(RuleBasedEmbeddingTextBuilder.normalize(name)).isEqualTo(name.strip());
    }
}
