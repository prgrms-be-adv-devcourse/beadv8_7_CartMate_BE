package com.cartmate.cartmate.boundedContext.cart.domain.personal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Entity
@Table(name = "cart")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long memberId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Cart(Long memberId) {
        this.memberId = memberId;
    }

    public static Cart create(Long memberId) {
        return new Cart(memberId);
    }

    public CartItem addItem(Long productId, Long inventoryId, int quantity) {
        Optional<CartItem> existing = findItemByInventoryId(inventoryId);
        if (existing.isPresent()) {
            existing.get().increaseQuantity(quantity);
            return existing.get();
        }
        CartItem item = CartItem.create(this, productId, inventoryId, quantity);
        items.add(item);
        return item;
    }

    public CartItem getItem(Long cartItemId) {
        return items.stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("장바구니에 없는 상품입니다."));
    }

    public void removeItem(Long cartItemId) {
        items.remove(getItem(cartItemId));
    }

    public void clear() {
        items.clear();
    }

    private Optional<CartItem> findItemByInventoryId(Long inventoryId) {
        return items.stream()
                .filter(item -> item.getInventoryId().equals(inventoryId))
                .findFirst();
    }
}
