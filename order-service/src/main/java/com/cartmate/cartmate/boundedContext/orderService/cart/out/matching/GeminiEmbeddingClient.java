package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
class GeminiEmbeddingClient implements ProductEmbeddingClient {

    private static final int MAX_BATCH = 100;

    private final GeminiApi geminiApi;
    private final List<String> models;

    GeminiEmbeddingClient(GeminiApi geminiApi,
                          @Value("${custom.matching.gemini.embedding-models:gemini-embedding-2,gemini-embedding-001}")
                          List<String> models) {
        if (models.isEmpty()) {
            throw new IllegalArgumentException("at least one embedding model is required");
        }
        this.geminiApi = geminiApi;
        this.models = List.copyOf(models);
    }

    @Override
    public Embeddings embed(List<String> texts) {
        if (!geminiApi.isConfigured()) {
            throw new GeminiApi.GeminiNotConfiguredException();
        }
        GeminiApi.GeminiUnavailableException last = null;
        for (String model : models) {
            try {
                Embeddings embeddings = new Embeddings(model, embedWith(model, texts));
                if (!model.equals(models.getFirst())) {
                    log.warn("Embedded {} texts with fallback model {} after {}", texts.size(), model, last.getMessage());
                }
                return embeddings;
            } catch (GeminiApi.GeminiUnavailableException e) {
                last = e;
            }
        }
        throw last;
    }

    private List<float[]> embedWith(String model, List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += MAX_BATCH) {
            List<String> chunk = texts.subList(from, Math.min(texts.size(), from + MAX_BATCH));
            List<Map<String, Object>> requests = chunk.stream()
                    .map(text -> Map.<String, Object>of(
                            "model", "models/" + model,
                            "content", Map.of("parts", List.of(Map.of("text", text))),
                            "taskType", "SEMANTIC_SIMILARITY"))
                    .toList();
            Map<?, ?> response = geminiApi.post("/models/" + model + ":batchEmbedContents", Map.of("requests", requests));
            vectors.addAll(parse(model, response, chunk.size()));
        }
        return vectors;
    }

    private static List<float[]> parse(String model, Map<?, ?> response, int expected) {
        if (!(response.get("embeddings") instanceof List<?> embeddings) || embeddings.size() != expected) {
            throw new GeminiApi.GeminiUnavailableException(
                    "Gemini " + model + " returned malformed embeddings for " + expected + " texts", null);
        }
        List<float[]> vectors = new ArrayList<>(expected);
        for (Object embedding : embeddings) {
            if (!(embedding instanceof Map<?, ?> map) || !(map.get("values") instanceof List<?> values) || values.isEmpty()) {
                throw new GeminiApi.GeminiUnavailableException("Gemini " + model + " returned an empty embedding", null);
            }
            float[] vector = new float[values.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = ((Number) values.get(i)).floatValue();
            }
            vectors.add(vector);
        }
        return vectors;
    }

    @Override
    public String primaryModel() {
        return models.getFirst();
    }
}
