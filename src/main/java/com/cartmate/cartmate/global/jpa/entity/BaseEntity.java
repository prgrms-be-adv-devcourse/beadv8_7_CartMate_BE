package com.cartmate.cartmate.global.jpa.entity;

import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.standard.modelType.HasModelTypeCode;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
public abstract class BaseEntity implements HasModelTypeCode {
    public abstract Long getId();

    public abstract LocalDateTime getCreateDate();

    public abstract LocalDateTime getModifyDate();

    @Override
    public String getModelTypeCode() {
        return this.getClass().getSimpleName();
    }

    protected void publishEvent(Object event) {
        GlobalConfig.getEventPublisher().publish(event);
    }
}
