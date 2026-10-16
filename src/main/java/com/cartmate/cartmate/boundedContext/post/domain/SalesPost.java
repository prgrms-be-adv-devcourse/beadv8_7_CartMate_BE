package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.boundedContext.post.dto.create.CreateSalesPostRequest;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "POST_POST")
@NoArgsConstructor
@Getter
public class SalesPost extends BaseIdAndTime {
    private Long sellerId;
    private String title;
    private String content;
    @Enumerated(EnumType.STRING)
    private SalesPostSaleStatus saleStatus;
    private SalesPostSaveStatus saveStatus;
    private Long viewCount;

    public SalesPost(CreateSalesPostRequest postDto) {
        this.sellerId = postDto.getSellerId();
    }

    public boolean isVisible() {
        return saleStatus.isVisible() && saveStatus.isVisible();
    }

    public void publish(SalesPost salesPost) {
        this.title = salesPost.getTitle();
        this.content = salesPost.getContent();
        this.saleStatus = SalesPostSaleStatus.ON_SALE;
        this.saveStatus = SalesPostSaveStatus.PUBLISHED;
    }

}
