package com.stockgame.server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 玩家的遊戲帳戶（資金、持股、凍結資產）。
 * 與 User 1:1 對應；分開設計以便未來支援多組帳戶（不同賽季）。
 */
@Entity
@Table(name = "game_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** 可動用現金（未凍結） */
    @Column(nullable = false)
    @Builder.Default
    private double availableCash = 0.0;

    /** 凍結現金（已掛出買單但尚未成交的金額） */
    @Column(nullable = false)
    @Builder.Default
    private double frozenCash = 0.0;

    /** 可賣持股（未凍結） */
    @Column(nullable = false)
    @Builder.Default
    private int availableStocks = 0;

    /** 凍結持股（已掛出賣單但尚未成交的股數） */
    @Column(nullable = false)
    @Builder.Default
    private int frozenStocks = 0;

    /** 累計已實現損益 */
    @Column(nullable = false)
    @Builder.Default
    private double realizedPnl = 0.0;

    /** 平均持股成本（加權平均） */
    @Column(nullable = false)
    @Builder.Default
    private double avgCostPrice = 0.0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;   // 樂觀鎖，防止並發帳戶修改衝突

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── 便利方法 ─────────────────────────────────────────────────────────────

    public double getTotalCash()   { return availableCash + frozenCash; }
    public int    getTotalStocks() { return availableStocks + frozenStocks; }

    /** 凍結買單資金 */
    public void freezeCash(double amount) {
        if (availableCash < amount) throw new IllegalStateException("可用資金不足");
        availableCash -= amount;
        frozenCash    += amount;
    }

    /** 解凍資金（委託取消） */
    public void unfreezeCash(double amount) {
        double unfreeze = Math.min(frozenCash, amount);
        frozenCash    -= unfreeze;
        availableCash += unfreeze;
    }

    /** 凍結賣單持股 */
    public void freezeStocks(int qty) {
        if (availableStocks < qty) throw new IllegalStateException("可用持股不足");
        availableStocks -= qty;
        frozenStocks    += qty;
    }

    /** 解凍持股（委託取消） */
    public void unfreezeStocks(int qty) {
        int unfreeze = Math.min(frozenStocks, qty);
        frozenStocks    -= unfreeze;
        availableStocks += unfreeze;
    }

    /**
     * 買入成交後更新帳戶（釋放凍結資金、增加持股、更新均價）。
     * @param qty   成交股數
     * @param price 成交價格
     */
    public void onBuyFilled(int qty, double price) {
        double cost = qty * price;
        frozenCash = Math.max(0, frozenCash - cost);
        // 加權更新持股均價
        int    totalStocks = availableStocks + qty;
        double totalCost   = avgCostPrice * availableStocks + cost;
        avgCostPrice       = totalStocks > 0 ? totalCost / totalStocks : 0.0;
        availableStocks   += qty;
    }

    /**
     * 賣出成交後更新帳戶（釋放凍結持股、增加現金、計算損益）。
     * @param qty   成交股數
     * @param price 成交價格
     */
    public void onSellFilled(int qty, double price) {
        frozenStocks  = Math.max(0, frozenStocks - qty);
        double revenue = qty * price;
        availableCash += revenue;
        realizedPnl   += (price - avgCostPrice) * qty;
        // 若全部賣出則重置均價
        if (getTotalStocks() == 0) avgCostPrice = 0.0;
    }
}
