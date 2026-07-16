package com.stockgame.server.controller;

import com.stockgame.server.dto.AuthResponse;
import com.stockgame.server.dto.LoginRequest;
import com.stockgame.server.dto.RegisterRequest;
import com.stockgame.server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 認證 API：
 *   POST /api/auth/register — 新玩家註冊
 *   POST /api/auth/login    — 登入取得 JWT
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
