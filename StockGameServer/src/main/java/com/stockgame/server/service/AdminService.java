package com.stockgame.server.service;

import com.stockgame.server.dto.AdminAnnouncementRequest;
import com.stockgame.server.dto.AdminMarketStatusDto;
import com.stockgame.server.dto.AdminSimulatorConfigRequest;
import com.stockgame.server.dto.AdminUserAccountDto;
import com.stockgame.server.engine.MarketSimulator;
import com.stockgame.server.engine.ServerMatchingEngine;
import com.stockgame.server.entity.GameAccount;
import com.stockgame.server.entity.StockOrder;
import com.stockgame.server.entity.User;
import com.stockgame.server.repository.GameAccountRepository;
import com.stockgame.server.repository.StockOrderRepository;
import com.stockgame.server.repository.UserRepository;
import com.stockgame.server.websocket.MarketBroadcastService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminService {

    private final MarketService marketService;
    private final MarketSimulator marketSimulator;
    private final GameAccountRepository gameAccountRepository;
    private final UserRepository userRepository;
    private final StockOrderRepository stockOrderRepository;
    private final ServerMatchingEngine serverMatchingEngine;
    private final MarketBroadcastService marketBroadcastService;

    @Value("${game.initial-cash:1000000.0}")
    private double initialCash;

    public AdminService(
            MarketService marketService,
            MarketSimulator marketSimulator,
            GameAccountRepository gameAccountRepository,
            UserRepository userRepository,
            StockOrderRepository stockOrderRepository,
            ServerMatchingEngine serverMatchingEngine,
            MarketBroadcastService marketBroadcastService
    ) {
        this.marketService = marketService;
        this.marketSimulator = marketSimulator;
        this.gameAccountRepository = gameAccountRepository;
        this.userRepository = userRepository;
        this.stockOrderRepository = stockOrderRepository;
        this.serverMatchingEngine = serverMatchingEngine;
        this.marketBroadcastService = marketBroadcastService;
    }

    public AdminMarketStatusDto getMarketStatus() {
        var quote = marketService.getQuote();
        return AdminMarketStatusDto.builder()
                .marketOpen(marketService.isMarketOpen())
                .symbol(marketService.getSymbol())
                .lastPrice(quote.getLastPrice())
                .bestBid(quote.getBestBid())
                .bestAsk(quote.getBestAsk())
                .totalVolume(quote.getTotalVolume())
                .totalAmount(quote.getTotalAmount())
                .buyVolume(quote.getBuyVolume())
                .sellVolume(quote.getSellVolume())
                .tickIntervalMs(marketService.getTickIntervalMs())
                .aiRetailCount(marketSimulator.getRetailCount())
                .aiNoiseCount(marketSimulator.getNoiseCount())
                .accountCount(gameAccountRepository.count())
                .build();
    }

    public List<AdminUserAccountDto> getAccounts() {
        double currentPrice = marketService.getLastPrice();
        return gameAccountRepository.findTopWithUsers(PageRequest.of(0, 50)).stream()
                .map(account -> toAdminUserAccountDto(account, currentPrice))
                .toList();
    }

    @Transactional
    public AdminMarketStatusDto setMarketOpen(boolean open) {
        marketService.setMarketOpen(open);
        return getMarketStatus();
    }

    @Transactional
    public AdminMarketStatusDto runTick() {
        marketService.runAdminTick();
        return getMarketStatus();
    }

    @Transactional
    public AdminMarketStatusDto updateSimulatorConfig(AdminSimulatorConfigRequest request) {
        if (request.getRetailCount() != null) {
            marketSimulator.setRetailCount(request.getRetailCount());
        }
        if (request.getNoiseCount() != null) {
            marketSimulator.setNoiseCount(request.getNoiseCount());
        }
        marketService.broadcastMarketSnapshot();
        return getMarketStatus();
    }

    @Transactional
    public void broadcastAnnouncement(AdminAnnouncementRequest request) {
        marketBroadcastService.broadcastAnnouncement(request.getMessage().trim());
    }

    @Transactional
    public AdminUserAccountDto setUserEnabled(String username, boolean enabled) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者：" + username));

        user.setEnabled(enabled);
        userRepository.save(user);

        GameAccount account = gameAccountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("找不到使用者帳戶：" + username));

        return toAdminUserAccountDto(account, marketService.getLastPrice());
    }

    @Transactional
    public AdminUserAccountDto resetAccount(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者：" + username));

        List<StockOrder> activeOrders = stockOrderRepository.findByUserIdAndStatusIn(
                user.getId(),
                List.of(StockOrder.Status.PENDING, StockOrder.Status.PARTIAL)
        );

        for (StockOrder order : activeOrders) {
            serverMatchingEngine.cancelOrder(order);
        }

        GameAccount account = gameAccountRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new IllegalStateException("找不到使用者帳戶：" + username));

        account.setAvailableCash(initialCash);
        account.setFrozenCash(0.0);
        account.setAvailableStocks(0);
        account.setFrozenStocks(0);
        account.setRealizedPnl(0.0);
        account.setAvgCostPrice(0.0);
        gameAccountRepository.save(account);

        return toAdminUserAccountDto(account, marketService.getLastPrice());
    }

    private AdminUserAccountDto toAdminUserAccountDto(GameAccount account, double currentPrice) {
        double unrealizedPnl = (currentPrice - account.getAvgCostPrice()) * account.getTotalStocks();
        double totalAssets = account.getTotalCash() + account.getTotalStocks() * currentPrice;
        double totalPnl = account.getRealizedPnl() + unrealizedPnl;
        double returnRate = initialCash > 0 ? totalPnl / initialCash * 100 : 0;

        return AdminUserAccountDto.builder()
                .username(account.getUser().getUsername())
                .role(account.getUser().getRole().name())
                .enabled(account.getUser().isEnabled())
                .availableCash(account.getAvailableCash())
                .totalAssets(totalAssets)
                .totalStocks(account.getTotalStocks())
                .realizedPnl(account.getRealizedPnl())
                .unrealizedPnl(unrealizedPnl)
                .returnRate(returnRate)
                .createdAt(account.getUser().getCreatedAt())
                .lastLoginAt(account.getUser().getLastLoginAt())
                .build();
    }
}
