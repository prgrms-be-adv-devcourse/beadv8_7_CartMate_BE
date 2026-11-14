package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiEmbeddingClientTest {

    private static final String PRIMARY = GeminiApi.BASE_URL + "/models/gemini-embedding-2:batchEmbedContents";
    private static final String FALLBACK = GeminiApi.BASE_URL + "/models/gemini-embedding-001:batchEmbedContents";
    private static final String TWO_VECTORS = "{\"embeddings\":[{\"values\":[0.1,0.2]},{\"values\":[0.3,0.4]}]}";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GeminiEmbeddingClient client = new GeminiEmbeddingClient(
            new GeminiApi(builder, "test-key"), List.of("gemini-embedding-2", "gemini-embedding-001"));

    @Test
    @DisplayName("기본 모델로 임베딩하고 결과에 모델 이름을 남긴다")
    void primary() {
        server.expect(requestTo(PRIMARY)).andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withSuccess(TWO_VECTORS, MediaType.APPLICATION_JSON));

        ProductEmbeddingClient.Embeddings result = client.embed(List.of("축구공", "축구화"));

        assertThat(result.model()).isEqualTo("gemini-embedding-2");
        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors().get(1)).containsExactly(0.3f, 0.4f);
        server.verify();
    }

    @Test
    @DisplayName("기본 모델이 한도 초과(429)면 묶음 전체를 대체 모델로 다시 만든다")
    void fallbackOnRateLimit() {
        server.expect(requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(FALLBACK)).andRespond(withSuccess(TWO_VECTORS, MediaType.APPLICATION_JSON));

        ProductEmbeddingClient.Embeddings result = client.embed(List.of("축구공", "축구화"));

        assertThat(result.model()).isEqualTo("gemini-embedding-001");
        assertThat(result.vectors()).hasSize(2);
        server.verify();
    }

    @Test
    @DisplayName("키가 없으면 호출하지 않고 사용할 수 없다고 알린다")
    void noKey() {
        GeminiEmbeddingClient noKey = new GeminiEmbeddingClient(new GeminiApi(RestClient.builder(), ""),
                List.of("gemini-embedding-2"));

        assertThatThrownBy(() -> noKey.embed(List.of("축구공")))
                .isInstanceOf(GeminiApi.GeminiNotConfiguredException.class);
    }

    @Test
    @DisplayName("응답 벡터 수가 요청 수와 다르면 대체 모델로 다시 만든다")
    void fallbackOnCountMismatch() {
        server.expect(requestTo(PRIMARY))
                .andRespond(withSuccess("{\"embeddings\":[{\"values\":[0.1,0.2]}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(FALLBACK)).andRespond(withSuccess(TWO_VECTORS, MediaType.APPLICATION_JSON));

        assertThat(client.embed(List.of("축구공", "축구화")).model()).isEqualTo("gemini-embedding-001");
        server.verify();
    }

    @Test
    @DisplayName("서버 오류(5xx)도 대체 모델로 넘기고, 모두 실패하면 사용할 수 없다고 알린다")
    void allModelsFail() {
        server.expect(requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(FALLBACK)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.embed(List.of("축구공", "축구화")))
                .isInstanceOf(GeminiApi.GeminiUnavailableException.class);
        server.verify();
    }
}
