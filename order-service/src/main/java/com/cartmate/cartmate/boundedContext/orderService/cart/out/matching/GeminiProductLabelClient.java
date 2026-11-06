package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
class GeminiProductLabelClient implements ProductLabelClient {

    private static final String PROMPT = """
            각 상품의 '상품 종류'를 공동구매로 묶을 수 있는 수준의 짧은 한국어 명사구로 써라.
            브랜드, 모델명, 크기, 용량, 연령(성인/아동), 색상, 홍보 문구는 빼라. 예: "나이키 머큐리얼 축구화 FG 270" → "축구화".
            응답은 입력 순서대로 문자열만 담은 JSON 배열.
            상품:
            %s""";

    private final GeminiApi geminiApi;
    private final List<String> models;
    private final ObjectMapper objectMapper = new ObjectMapper();

    GeminiProductLabelClient(GeminiApi geminiApi,
                             @Value("${custom.matching.gemini.label-models:gemini-2.5-flash,gemini-flash-lite-latest}")
                             List<String> models) {
        this.geminiApi = geminiApi;
        this.models = List.copyOf(models);
    }

    @Override
    public List<String> labelProductTypes(List<String> productNames) {
        if (productNames.isEmpty()) {
            return List.of();
        }
        String listing = IntStream.range(0, productNames.size())
                .mapToObj(i -> (i + 1) + ". " + productNames.get(i))
                .collect(Collectors.joining("\n"));
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", PROMPT.formatted(listing))))),
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0));
        for (String model : models) {
            try {
                Map<?, ?> response = geminiApi.post("/models/" + model + ":generateContent", body);
                return parse(response);
            } catch (RuntimeException e) {
                continue;
            }
        }
        return List.of();
    }

    private List<String> parse(Map<?, ?> response) {
        try {
            if (response == null) {
                return List.of();
            }
            Map<?, ?> candidate = (Map<?, ?>) ((List<?>) response.get("candidates")).get(0);
            Map<?, ?> part = (Map<?, ?>) ((List<?>) ((Map<?, ?>) candidate.get("content")).get("parts")).get(0);
            return objectMapper.readValue((String) part.get("text"), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
