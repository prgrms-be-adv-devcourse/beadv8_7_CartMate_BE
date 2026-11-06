package com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Getter
@Entity
@Table(name = "product_embedding")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEmbedding {

    @Id
    private Long productId;

    private Long categoryId;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(nullable = false, length = 20)
    private String textVersion;

    @Column(nullable = false)
    private int dimension;

    @Column(nullable = false, columnDefinition = "mediumblob")
    private byte[] vector;

    private ProductEmbedding(Long productId) {
        this.productId = productId;
    }

    public static ProductEmbedding of(ProductVector productVector) {
        ProductEmbedding embedding = new ProductEmbedding(productVector.productId());
        embedding.replace(productVector);
        return embedding;
    }

    public void replace(ProductVector productVector) {
        if (!productId.equals(productVector.productId())) {
            throw new IllegalArgumentException("productId mismatch: " + productId + " != " + productVector.productId());
        }
        float[] values = productVector.values();
        this.categoryId = productVector.categoryId();
        this.model = productVector.model();
        this.textVersion = productVector.textVersion();
        this.dimension = values.length;
        this.vector = encode(values);
    }

    public ProductVector toProductVector() {
        return new ProductVector(productId, categoryId, model, textVersion, decode(vector, dimension));
    }

    private static byte[] encode(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(values);
        return buffer.array();
    }

    private static float[] decode(byte[] bytes, int dimension) {
        if (bytes.length != dimension * Float.BYTES) {
            throw new IllegalStateException("stored vector length " + bytes.length + " does not match dimension " + dimension);
        }
        float[] values = new float[dimension];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values);
        return values;
    }
}
