package com.stockgame.server.service;

import com.stockgame.server.dto.AccountInfoDto;
import com.stockgame.server.dto.LeaderboardEntryDto;
import com.stockgame.server.entity.GameAccount;
import com.stockgame.server.repository.GameAccountRepository;
import com.stockgame.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final GameAccountRepository accountRepository;
    private final UserRepository        userRepository;
    private final MarketService         marketService;

    @Value("${game.initial-cash:1000000.0}")
    private double initialCash;

    public AccountInfoDto getAccountInfo(String username) {
        GameAccount account = findAccount(username);
        double currentPrice = marketService.getLastPrice();
        double unrealizedPnl = (currentPrice - account.getAvgCostPrice()) * account.getTotalStocks();
        double totalAssets   = account.getTotalCash() + account.getTotalStocks() * currentPrice;

        double totalPnl    = account.getRealizedPnl() + unrealizedPnl;
        double returnRate  = initialCash > 0 ? totalPnl / initialCash * 100 : 0;

        return AccountInfoDto.builder()
                .username(username)
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
                .build();
    }

    /** 排行榜（前 20 名，依已實現損益，回傳 DTO 避免 Lazy 問題） */
    public List<LeaderboardEntryDto> getLeaderboard() {
        List<GameAccount> accounts = accountRepository.findTopByRealizedPnl(PageRequest.of(0, 20));
        AtomicInteger rank = new AtomicInteger(1);
        return accounts.stream().map(acc -> LeaderboardEntryDto.builder()
                .rank(rank.getAndIncrement())
                .username(acc.getUser() != null ? acc.getUser().getUsername() : "AI")
                .realizedPnl(acc.getRealizedPnl())
                .availableCash(acc.getAvailableCash())
                .totalStocks(acc.getTotalStocks())
                .build())
                .collect(Collectors.toList());
    }

    private GameAccount findAccount(String username) {
        Long userId = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("找不到使用者：" + username))
                .getId();
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("找不到帳戶"));
    }
}
