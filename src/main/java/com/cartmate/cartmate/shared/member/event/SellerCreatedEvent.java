package com.cartmate.cartmate.shared.member.event;

import com.cartmate.cartmate.shared.member.dto.SellerDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class SellerCreatedEvent {
    private final SellerDto seller;
}
