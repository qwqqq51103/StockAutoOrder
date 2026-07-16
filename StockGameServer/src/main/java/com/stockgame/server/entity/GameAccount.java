package com.stockgame.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameAccount {

    private static final double EPSILON = 0.000001;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private double availableCash = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private double frozenCash = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private int availableStocks = 0;

    @Column(nullable = false)
    @Builder.Default
    private int frozenStocks = 0;

    @Column(nullable = false)
    @Builder.Default
    private double realizedPnl = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private double avgCostPrice = 0.0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public double getTotalCash() {
        return availableCash + frozenCash;
    }

    public int getTotalStocks() {
        return availableStocks + frozenStocks;
    }

    public void freezeCash(double amount) {
        if (amount <= EPSILON) {
            return;
        }
        if (availableCash + EPSILON < amount) {
            throw new IllegalStateException("可用資金不足。");
        }
        availableCash = Math.max(0.0, availableCash - amount);
        frozenCash += amount;
    }

    public void unfreezeCash(double amount) {
        if (amount <= EPSILON) {
            return;
        }
        double unfreeze = Math.min(frozenCash, amount);
        frozenCash = Math.max(0.0, frozenCash - unfreeze);
        availableCash += unfreeze;
    }

    public void freezeStocks(int qty) {
        if (qty <= 0) {
            return;
        }
        if (availableStocks < qty) {
            throw new IllegalStateException("可用持股不足。");
        }
        availableStocks -= qty;
        frozenStocks += qty;
    }

    public void unfreezeStocks(int qty) {
        if (qty <= 0) {
            return;
        }
        int unfreeze = Math.min(frozenStocks, qty);
        frozenStocks -= unfreeze;
        availableStocks += unfreeze;
    }

    public void onBuyFilled(int qty, double price) {
        onBuyFilled(qty, price, price);
    }

    public void onBuyFilled(int qty, double executionPrice, double reservedPrice) {
        if (qty <= 0) {
            return;
        }

        double reserved = Math.max(0.0, reservedPrice) * qty;
        double cost = executionPrice * qty;
        double released = Math.min(frozenCash, reserved);

        frozenCash = Math.max(0.0, frozenCash - released);

        if (released + EPSILON >= cost) {
            availableCash += released - cost;
        } else {
            double shortfall = cost - released;
            if (availableCash + EPSILON < shortfall) {
                throw new IllegalStateException("買進成交的凍結資金不足。");
            }
            availableCash = Math.max(0.0, availableCash - shortfall);
        }

        int previousStocks = getTotalStocks();
        int totalStocks = previousStocks + qty;
        double totalCost = avgCostPrice * previousStocks + cost;
        avgCostPrice = totalStocks > 0 ? totalCost / totalStocks : 0.0;
        availableStocks += qty;
    }

    public void onSellFilled(int qty, double price) {
        if (qty <= 0) {
            return;
        }

        frozenStocks = Math.max(0, frozenStocks - qty);
        double revenue = qty * price;
        availableCash += revenue;
        realizedPnl += (price - avgCostPrice) * qty;

        if (getTotalStocks() == 0) {
            avgCostPrice = 0.0;
        }
    }
}
