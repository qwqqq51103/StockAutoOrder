package com.stockgame.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 委託單實體（對應訂單簿中的每一筆掛單）。
 */
@Entity
@Table(name = "stock_orders", indexes = {
        @Index(name = "idx_orders_user",   columnList = "user_id"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;                  // null = AI 委託

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Side side;                  // BUY / SELL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrderType type;             // LIMIT / MARKET / FOK

    @Column(nullable = false)
    private double price;               // 限價委託價；市價單為 0

    @Column(nullable = false)
    private int quantity;               // 委託數量

    @Column(nullable = false)
    @Builder.Default
    private int filledQuantity = 0;     // 已成交數量

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ── 列舉 ─────────────────────────────────────────────────────────────────

    public enum Side       { BUY, SELL }
    public enum OrderType  { LIMIT, MARKET, FOK }
    public enum Status     { PENDING, PARTIAL, FILLED, CANCELLED }

    // ── 便利方法 ──────────────────────────────────────────────────────────────

    public int getRemainingQuantity() { return quantity - filledQuantity; }
    public boolean isFullyFilled()    { return filledQuantity >= quantity; }
    public boolean isActive()         { return status == Status.PENDING || status == Status.PARTIAL; }
    public boolean isMarketOrder()    { return type == OrderType.MARKET; }
    public boolean isFok()            { return type == OrderType.FOK; }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
