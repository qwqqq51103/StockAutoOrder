package com.stockgame.server.controller;

import com.stockgame.server.dto.PlaceOrderRequest;
import com.stockgame.server.entity.StockOrder;
import com.stockgame.server.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 委託管理 API（需 JWT）：
 *   POST   /api/orders         — 送出委託
 *   DELETE /api/orders/{id}    — 取消委託
 *   GET    /api/orders/active  — 我的有效委託
 *   GET    /api/orders/history — 我的歷史委託
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<StockOrder> placeOrder(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody PlaceOrderRequest req) {
        return ResponseEntity.ok(orderService.placeOrder(user.getUsername(), req));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long orderId) {
        orderService.cancelOrder(user.getUsername(), orderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/active")
    public ResponseEntity<List<StockOrder>> getActiveOrders(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(orderService.getActiveOrders(user.getUsername()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<StockOrder>> getOrderHistory(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(orderService.getOrderHistory(user.getUsername()));
    }
}
