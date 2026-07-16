package com.stockgame.server.controller;

import com.stockgame.server.dto.AccountInfoDto;
import com.stockgame.server.dto.LeaderboardEntryDto;
import com.stockgame.server.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 玩家帳戶 API（需 JWT）：
 *   GET /api/account/info        — 帳戶資金/持股/損益
 *   GET /api/account/leaderboard — 排行榜（公開）
 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/info")
    public ResponseEntity<AccountInfoDto> getAccountInfo(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(accountService.getAccountInfo(user.getUsername()));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntryDto>> getLeaderboard() {
        return ResponseEntity.ok(accountService.getLeaderboard());
    }
}
