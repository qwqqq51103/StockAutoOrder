package com.stockgame.server.engine;

import com.stockgame.server.entity.GameAccount;
import com.stockgame.server.entity.StockOrder;
import com.stockgame.server.entity.StockTransaction;
import com.stockgame.server.repository.GameAccountRepository;
import com.stockgame.server.repository.StockOrderRepository;
import com.stockgame.server.repository.StockTransactionRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServerMatchingEngine {

    private final ServerOrderBook orderBook;
    private final StockOrderRepository orderRepository;
    private final StockTransactionRepository txRepository;
    private final GameAccountRepository accountRepository;

    @Transactional
    public StockOrder submitPlayerOrder(StockOrder order) {
        validateOrder(order);

        if (order.getUser() != null) {
            GameAccount account = accountRepository.findByUserIdForUpdate(order.getUser().getId())
                    .orElseThrow(() -> new IllegalStateException("找不到遊戲帳戶。"));

            if (order.isFok()) {
                validateFokLiquidity(order);
            }

            if (order.getSide() == StockOrder.Side.BUY) {
                double reservedPrice = resolveBuyReservePrice(order);
                account.freezeCash(reservedPrice * order.getQuantity());

                if (order.isMarketOrder()) {
                    order.setPrice(reservedPrice);
                }
            } else {
                account.freezeStocks(order.getQuantity());
            }

            accountRepository.save(account);
        }

        StockOrder saved = orderRepository.save(order);
        addToOrderBook(saved);

        // 玩家送單後若價格可成交，應立即撮合，不應等待下一個 tick。
        runMatchingCycle();
        return saved;
    }

    public StockOrder submitAiOrder(StockOrder order) {
        validateOrder(order);
        StockOrder saved = orderRepository.save(order);
        addToOrderBook(saved);
        return saved;
    }

    @Transactional
    public List<StockTransaction> runMatchingCycle() {
        ServerOrderBook.MatchResult result = orderBook.matchOrders();
        List<StockTransaction> transactions = new ArrayList<>();

        for (ServerOrderBook.MatchEvent evt : result.events()) {
            StockTransaction tx = StockTransaction.builder()
                    .buyOrder(evt.buyOrder())
                    .sellOrder(evt.sellOrder())
                    .price(evt.price())
                    .quantity(evt.quantity())
                    .buyerInitiated(evt.buyerInitiated())
                    .build();
            tx = txRepository.save(tx);
            transactions.add(tx);

            orderRepository.save(evt.buyOrder());
            orderRepository.save(evt.sellOrder());

            updateAccountOnFill(evt.buyOrder(), evt.price(), evt.quantity(), true);
            updateAccountOnFill(evt.sellOrder(), evt.price(), evt.quantity(), false);

            log.info("Matched {} @ {}", evt.quantity(), evt.price());
        }

        for (StockOrder expiredOrder : result.expiredOrders()) {
            releaseRemainingReservation(expiredOrder);
            orderRepository.save(expiredOrder);
        }

        return transactions;
    }

    @Transactional
    public void cancelOrder(StockOrder order) {
        if (!order.isActive()) {
            throw new IllegalStateException("委託已完成或已取消。");
        }

        order.setStatus(StockOrder.Status.CANCELLED);
        orderBook.removeOrder(order);
        releaseRemainingReservation(order);
        orderRepository.save(order);
    }

    private void validateOrder(StockOrder order) {
        if (order.getSide() == null) {
            throw new IllegalArgumentException("請選擇買賣方向。");
        }
        if (order.getType() == null) {
            throw new IllegalArgumentException("請選擇委託類型。");
        }
        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException("委託數量必須大於 0。");
        }
        if ((order.getType() == StockOrder.OrderType.LIMIT || order.getType() == StockOrder.OrderType.FOK)
                && order.getPrice() <= 0) {
            throw new IllegalArgumentException("限價與 FOK 委託價格必須大於 0。");
        }
    }

    private void validateFokLiquidity(StockOrder order) {
        boolean canFill = order.getSide() == StockOrder.Side.BUY
                ? orderBook.canFullyFillLimitBuy(order.getPrice(), order.getQuantity())
                : orderBook.canFullyFillLimitSell(order.getPrice(), order.getQuantity());

        if (!canFill) {
            throw new IllegalArgumentException("FOK 委託無法立即全部成交。");
        }
    }

    private double resolveBuyReservePrice(StockOrder order) {
        if (!order.isMarketOrder()) {
            return order.getPrice();
        }

        return orderBook.findMarketBuyLimitPrice(order.getQuantity())
                .orElseThrow(() -> new IllegalArgumentException("市價買進的賣方流動性不足。"));
    }

    private void addToOrderBook(StockOrder order) {
        if (order.getSide() == StockOrder.Side.BUY) {
            orderBook.addBuyOrder(order);
        } else {
            orderBook.addSellOrder(order);
        }
    }

    private void releaseRemainingReservation(StockOrder order) {
        if (order.getUser() == null) {
            return;
        }

        int remainingQuantity = order.getRemainingQuantity();
        if (remainingQuantity <= 0) {
            return;
        }

        GameAccount account = accountRepository.findByUserIdForUpdate(order.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("找不到遊戲帳戶。"));

        if (order.getSide() == StockOrder.Side.BUY) {
            account.unfreezeCash(cashReservePrice(order, order.getPrice()) * remainingQuantity);
        } else {
            account.unfreezeStocks(remainingQuantity);
        }

        accountRepository.save(account);
    }

    private void updateAccountOnFill(StockOrder order, double price, int qty, boolean isBuy) {
        if (order.getUser() == null) {
            return;
        }

        GameAccount account = accountRepository.findByUserIdForUpdate(order.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("找不到遊戲帳戶。"));

        if (isBuy) {
            account.onBuyFilled(qty, price, cashReservePrice(order, price));
        } else {
            account.onSellFilled(qty, price);
        }

        accountRepository.save(account);
    }

    private double cashReservePrice(StockOrder order, double executionPrice) {
        if (order.getSide() != StockOrder.Side.BUY) {
            return 0.0;
        }
        if (order.getPrice() > 0) {
            return order.getPrice();
        }
        return executionPrice;
    }
}
