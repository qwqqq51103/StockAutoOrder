package com.stockgame.server.engine;

import com.stockgame.server.entity.GameAccount;
import com.stockgame.server.entity.StockOrder;
import com.stockgame.server.entity.StockTransaction;
import com.stockgame.server.repository.GameAccountRepository;
import com.stockgame.server.repository.StockOrderRepository;
import com.stockgame.server.repository.StockTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 撮合引擎：銜接 ServerOrderBook（記憶體）與 JPA 持久層。
 *
 * 流程：
 *   1. 玩家/AI 下單 → validateAndFreeze（凍結資金/持股）→ 加入訂單簿
 *   2. 每個 tick 呼叫 runMatchingCycle → 撮合 → 持久化成交 → 更新帳戶
 *   3. 回傳成交事件列表 → MarketService → WebSocket 廣播
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ServerMatchingEngine {

    private final ServerOrderBook          orderBook;
    private final StockOrderRepository     orderRepository;
    private final StockTransactionRepository txRepository;
    private final GameAccountRepository    accountRepository;

    /**
     * 驗證玩家委託、凍結資金/持股，並加入訂單簿。
     * @return 已儲存的委託單
     */
    @Transactional
    public StockOrder submitPlayerOrder(StockOrder order) {
        if (order.getUser() != null) {
            GameAccount account = accountRepository.findByUserIdForUpdate(order.getUser().getId())
                    .orElseThrow(() -> new IllegalStateException("找不到帳戶"));

            if (order.getSide() == StockOrder.Side.BUY) {
                double needCash = order.getPrice() * order.getQuantity();
                if (order.isMarketOrder()) {
                    // 市價單：以當前賣一估算（或用帳戶全部可用現金上限）
                    needCash = accountRepository.findByUserId(order.getUser().getId())
                            .map(GameAccount::getAvailableCash).orElse(0.0);
                    needCash = Math.min(needCash, needCash); // 允許市價單佔用所有可用現金
                }
                double actualFreeze = Math.min(account.getAvailableCash(), order.getPrice() * order.getQuantity());
                account.freezeCash(actualFreeze);
            } else {
                account.freezeStocks(order.getQuantity());
            }
            accountRepository.save(account);
        }

        StockOrder saved = orderRepository.save(order);

        if (saved.getSide() == StockOrder.Side.BUY) {
            orderBook.addBuyOrder(saved);
        } else {
            orderBook.addSellOrder(saved);
        }

        return saved;
    }

    /**
     * AI 直接加入訂單簿（不需凍結帳戶）。
     */
    public StockOrder submitAiOrder(StockOrder order) {
        StockOrder saved = orderRepository.save(order);
        if (saved.getSide() == StockOrder.Side.BUY) {
            orderBook.addBuyOrder(saved);
        } else {
            orderBook.addSellOrder(saved);
        }
        return saved;
    }

    /**
     * 執行一輪撮合，持久化成交記錄，更新玩家帳戶。
     * @return 本輪所有成交事件
     */
    @Transactional
    public List<StockTransaction> runMatchingCycle() {
        List<ServerOrderBook.MatchEvent> events = orderBook.matchOrders();
        List<StockTransaction> transactions = new ArrayList<>();

        for (ServerOrderBook.MatchEvent evt : events) {
            // 1. 持久化成交
            StockTransaction tx = StockTransaction.builder()
                    .buyOrder(evt.buyOrder())
                    .sellOrder(evt.sellOrder())
                    .price(evt.price())
                    .quantity(evt.quantity())
                    .buyerInitiated(evt.buyerInitiated())
                    .build();
            tx = txRepository.save(tx);
            transactions.add(tx);

            // 2. 更新委託狀態
            orderRepository.save(evt.buyOrder());
            orderRepository.save(evt.sellOrder());

            // 3. 更新玩家帳戶（買方、賣方各別處理）
            updateAccountOnFill(evt.buyOrder(),  evt.price(), evt.quantity(), true);
            updateAccountOnFill(evt.sellOrder(), evt.price(), evt.quantity(), false);

            log.info("成交：{} 股 @ {}", evt.quantity(), evt.price());
        }

        return transactions;
    }

    /**
     * 取消玩家委託，解凍資金/持股。
     */
    @Transactional
    public void cancelOrder(StockOrder order) {
        if (!order.isActive()) throw new IllegalStateException("委託已完成或已取消");

        order.setStatus(StockOrder.Status.CANCELLED);
        orderBook.removeOrder(order);
        orderRepository.save(order);

        if (order.getUser() != null) {
            GameAccount account = accountRepository.findByUserIdForUpdate(order.getUser().getId())
                    .orElseThrow(() -> new IllegalStateException("找不到帳戶"));
            if (order.getSide() == StockOrder.Side.BUY) {
                double refund = order.getPrice() * order.getRemainingQuantity();
                account.unfreezeCash(refund);
            } else {
                account.unfreezeStocks(order.getRemainingQuantity());
            }
            accountRepository.save(account);
        }
    }

    // ── 私有輔助 ──────────────────────────────────────────────────────────────

    private void updateAccountOnFill(StockOrder order, double price, int qty, boolean isBuy) {
        if (order.getUser() == null) return;  // AI 委託，不更新帳戶
        accountRepository.findByUserId(order.getUser().getId()).ifPresent(account -> {
            if (isBuy) {
                account.onBuyFilled(qty, price);
            } else {
                account.onSellFilled(qty, price);
            }
            accountRepository.save(account);
        });
    }
}
