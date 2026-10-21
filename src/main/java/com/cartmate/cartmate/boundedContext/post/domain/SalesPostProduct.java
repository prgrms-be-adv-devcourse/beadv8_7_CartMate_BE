package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostProduct;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostProductImage;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostProductOption;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "POST_PRODUCT")
@NoArgsConstructor
@Getter
public class SalesPostProduct extends BaseManualIdAndTime {
    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long sellerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_post_id", nullable = false)
    private SalesPost salesPost;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long categoryId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long basePrice;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    @OneToMany(mappedBy = "salesPostProduct", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    @SQLRestriction("status in ('ACTIVE')")
    private List<SalesPostProductImage> imageList = new ArrayList<>();

    @OneToMany(mappedBy = "salesPostProduct", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    private List<SalesPostProductOption> optionList = new ArrayList<>();


    public SalesPostProduct(Long id, Long sellerId, SalesPost salesPost, Long categoryId, String name, String description,
                            Long basePrice, ProductStatus status, LocalDateTime createDate, LocalDateTime modifyDate) {
        super(id, createDate, modifyDate);
        this.sellerId = sellerId;
        this.salesPost = salesPost;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.status = status;
    }

    public SalesPostProduct delete() {
        this.status = ProductStatus.DELETED;
        imageList.forEach(SalesPostProductImage::delete);
        optionList.clear();
        return this;
    }

}
