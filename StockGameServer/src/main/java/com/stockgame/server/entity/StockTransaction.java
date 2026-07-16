package com.stockgame.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 成交記錄實體（每次撮合產生一筆）。
 */
@Entity
@Table(name = "stock_transactions", indexes = {
        @Index(name = "idx_tx_buyer",  columnList = "buyer_order_id"),
        @Index(name = "idx_tx_seller", columnList = "seller_order_id"),
        @Index(name = "idx_tx_time",   columnList = "executedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_order_id")
    private StockOrder buyOrder;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_order_id")
    private StockOrder sellOrder;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private int quantity;

    /** true = 主動買方（買入吃單），false = 主動賣方 */
    @Column(nullable = false)
    @Builder.Default
    private boolean buyerInitiated = true;

    @Column(nullable = false)
    private LocalDateTime executedAt;

    @PrePersist
    protected void onCreate() {
        executedAt = LocalDateTime.now();
    }

    public double getTotalAmount() { return price * quantity; }
}
