package com.stockgame.server.service;

import com.stockgame.server.dto.PlaceOrderRequest;
import com.stockgame.server.engine.ServerMatchingEngine;
import com.stockgame.server.entity.StockOrder;
import com.stockgame.server.entity.User;
import com.stockgame.server.repository.StockOrderRepository;
import com.stockgame.server.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final ServerMatchingEngine matchingEngine;
    private final StockOrderRepository orderRepository;
    private final UserRepository userRepository;

    public StockOrder placeOrder(String username, PlaceOrderRequest req) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("找不到使用者：" + username));

        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            throw new IllegalArgumentException("委託數量必須大於 0。");
        }
        if ((req.getType() == StockOrder.OrderType.LIMIT || req.getType() == StockOrder.OrderType.FOK)
                && req.getPrice() <= 0) {
            throw new IllegalArgumentException("限價與 FOK 委託價格必須大於 0。");
        }

        StockOrder order = StockOrder.builder()
                .user(user)
                .side(req.getSide())
                .type(req.getType())
                .price(req.getPrice())
                .quantity(req.getQuantity())
                .build();

        StockOrder saved = matchingEngine.submitPlayerOrder(order);
        log.info(
                "Player {} submitted order: side={}, type={}, price={}, qty={}",
                username,
                req.getSide(),
                req.getType(),
                req.getPrice(),
                req.getQuantity()
        );
        return saved;
    }

    public void cancelOrder(String username, Long orderId) {
        StockOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("找不到委託：" + orderId));

        if (order.getUser() == null || !order.getUser().getUsername().equals(username)) {
            throw new SecurityException("無權取消此委託。");
        }

        matchingEngine.cancelOrder(order);
        log.info("Player {} cancelled order #{}", username, orderId);
    }

    public List<StockOrder> getActiveOrders(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("找不到使用者：" + username));
        return orderRepository.findByUserIdAndStatusIn(
                user.getId(),
                List.of(StockOrder.Status.PENDING, StockOrder.Status.PARTIAL));
    }

    public List<StockOrder> getOrderHistory(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("找不到使用者：" + username));
        return orderRepository.findTop100ByUser_IdOrderByCreatedAtDesc(user.getId());
    }
}
