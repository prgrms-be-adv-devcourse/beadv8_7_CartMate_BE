package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.EmbeddingText;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductToIndex;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "custom.matching.embedding-text", havingValue = "rule", matchIfMissing = true)
class RuleBasedEmbeddingTextBuilder implements EmbeddingTextBuilder {

    static final String VERSION = "rule-v1";

    private static final String WORD_START = "(?<![가-힣A-Za-z0-9])";
    private static final String WORD_END = "(?![가-힣A-Za-z0-9])";

    private static final List<String> BRANDS = List.of(
            "나이키", "아디다스", "푸마", "뉴발란스", "언더아머", "미즈노", "아식스", "엄브로", "데상트", "르꼬끄",
            "스타", "셀렉트", "몰텐", "스팔딩", "윌슨", "미카사",
            "머큐리얼", "프레데터", "코파", "팬텀", "티엠포", "페가수스");

    private static final List<Pattern> NOISE = List.of(
            Pattern.compile("\\([^)]*\\)|\\[[^]]*]"),
            Pattern.compile(words(BRANDS)),
            Pattern.compile("\\d+(\\.\\d+)?\\s*(호|ml|mL|L|l|mm|cm|kg|g|켤레|개입|개|벌|족|매|세트)" + WORD_END),
            Pattern.compile("(사이즈|size)\\s*\\d+" + WORD_END, Pattern.CASE_INSENSITIVE),
            Pattern.compile(words(List.of("XXS", "XS", "S", "M", "L", "XL", "XXL", "FG", "AG", "TF", "IC", "MG", "SG"))),
            Pattern.compile(WORD_START + "\\d{2,4}" + WORD_END),
            Pattern.compile(words(List.of("성인용", "성인", "아동용", "아동", "키즈", "어린이", "유아용", "유아", "초등학생",
                    "저학년용", "고학년용", "주니어", "남성용", "여성용", "남성", "여성", "남녀공용"))),
            Pattern.compile(words(List.of("무료", "인쇄", "마킹", "프린팅", "공식", "정품", "인증", "FIFA", "BPA", "프리",
                    "이상", "가능", "포함", "일체형", "타입", "슬립인", "천연잔디용", "인조잔디", "실내외 겸용", "실내용", "실외용"))));

    private static final Map<Pattern, String> SYNONYMS = new LinkedHashMap<>();

    static {
        synonym("풋살공", "축구공");
        synonym("풋살볼", "축구공");
        synonym("풋살화", "축구화");
        synonym("터프화", "축구화");
        synonym("신가드", "정강이 보호대");
        synonym("저지", "유니폼");
        synonym("단체복", "단체 유니폼");
        synonym("물통", "물병");
        synonym("보틀", "물병");
        synonym("워터저그", "물병");
    }

    @Override
    public List<EmbeddingText> build(List<ProductToIndex> products) {
        return products.stream()
                .map(product -> new EmbeddingText(normalize(product.name()), VERSION))
                .toList();
    }

    static String normalize(String name) {
        String text = name == null ? "" : name;
        for (Pattern pattern : NOISE) {
            text = pattern.matcher(text).replaceAll(" ");
        }
        for (Map.Entry<Pattern, String> synonym : SYNONYMS.entrySet()) {
            text = synonym.getKey().matcher(text).replaceAll(Matcher.quoteReplacement(synonym.getValue()));
        }
        String normalized = text.replaceAll("\\s+", " ").strip();
        return normalized.isEmpty() ? name == null ? "" : name.strip() : normalized;
    }

    private static String words(List<String> words) {
        return WORD_START + "(" + String.join("|", words.stream().map(Pattern::quote).toList()) + ")" + WORD_END;
    }

    private static void synonym(String word, String replacement) {
        SYNONYMS.put(Pattern.compile(words(List.of(word))), replacement);
    }

    @Override
    public String version() {
        return VERSION;
    }
}
