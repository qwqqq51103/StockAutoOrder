package com.stockgame.server.service;

import com.stockgame.server.dto.AccountInfoDto;
import com.stockgame.server.dto.AccountPositionDto;
import com.stockgame.server.dto.LeaderboardEntryDto;
import com.stockgame.server.entity.GameAccount;
import com.stockgame.server.repository.GameAccountRepository;
import com.stockgame.server.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final GameAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final MarketService marketService;

    @Value("${game.initial-cash:1000000.0}")
    private double initialCash;

    @Transactional(readOnly = true)
    public AccountInfoDto getAccountInfo(String username) {
        GameAccount account = findAccount(username);
        double currentPrice = marketService.getLastPrice();
        double unrealizedPnl = (currentPrice - account.getAvgCostPrice()) * account.getTotalStocks();
        double totalAssets = account.getTotalCash() + account.getTotalStocks() * currentPrice;
        double totalPnl = account.getRealizedPnl() + unrealizedPnl;
        double returnRate = initialCash > 0 ? totalPnl / initialCash * 100 : 0;

        return AccountInfoDto.builder()
                .username(username)
                .marketSymbol(marketService.getSymbol())
                .availableCash(account.getAvailableCash())
                .frozenCash(account.getFrozenCash())
                .totalCash(account.getTotalCash())
                .availableStocks(account.getAvailableStocks())
                .frozenStocks(account.getFrozenStocks())
                .totalStocks(account.getTotalStocks())
                .avgCostPrice(account.getAvgCostPrice())
                .realizedPnl(account.getRealizedPnl())
                .unrealizedPnl(unrealizedPnl)
                .totalPnl(totalPnl)
                .totalAssets(totalAssets)
                .initialCash(initialCash)
                .returnRate(returnRate)
                .positions(buildPositions(account, currentPrice, totalAssets))
                .build();
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> getLeaderboard() {
        List<GameAccount> accounts = accountRepository.findTopWithUsers(PageRequest.of(0, 20));
        AtomicInteger rank = new AtomicInteger(1);
        return accounts.stream()
                .map(acc -> LeaderboardEntryDto.builder()
                        .rank(rank.getAndIncrement())
                        .username(acc.getUser() != null ? acc.getUser().getUsername() : "AI")
                        .realizedPnl(acc.getRealizedPnl())
                        .availableCash(acc.getAvailableCash())
                        .totalStocks(acc.getTotalStocks())
                        .build())
                .collect(Collectors.toList());
    }

    private List<AccountPositionDto> buildPositions(GameAccount account, double currentPrice, double totalAssets) {
        List<AccountPositionDto> positions = new ArrayList<>();
        int totalQuantity = account.getTotalStocks();
        if (totalQuantity <= 0) {
            return positions;
        }

        double marketValue = totalQuantity * currentPrice;
        double allocationPct = totalAssets > 0 ? marketValue / totalAssets * 100 : 0;

        positions.add(AccountPositionDto.builder()
                .symbol(marketService.getSymbol())
                .availableQuantity(account.getAvailableStocks())
                .frozenQuantity(account.getFrozenStocks())
                .totalQuantity(totalQuantity)
                .avgCostPrice(account.getAvgCostPrice())
                .lastPrice(currentPrice)
                .marketValue(marketValue)
                .unrealizedPnl((currentPrice - account.getAvgCostPrice()) * totalQuantity)
                .allocationPct(allocationPct)
                .build());

        return positions;
    }

    private GameAccount findAccount(String username) {
        Long userId = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("找不到使用者：" + username))
                .getId();
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("找不到遊戲帳戶。"));
    }
}
