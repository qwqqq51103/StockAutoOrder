package com.stockgame.server.service;

import com.stockgame.server.config.JwtTokenProvider;
import com.stockgame.server.dto.AuthResponse;
import com.stockgame.server.dto.LoginRequest;
import com.stockgame.server.dto.RegisterRequest;
import com.stockgame.server.entity.GameAccount;
import com.stockgame.server.entity.User;
import com.stockgame.server.repository.GameAccountRepository;
import com.stockgame.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository        userRepository;
    private final GameAccountRepository gameAccountRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtTokenProvider      jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @Value("${game.initial-cash:1000000.0}")
    private double initialCash;

    /**
     * 玩家註冊：建立 User + 初始遊戲帳戶。
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("使用者名稱已被使用：" + req.getUsername());
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email 已被使用：" + req.getEmail());
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(User.Role.PLAYER)
                .build();
        userRepository.save(user);

        // 自動建立遊戲帳戶並賦予初始資金
        GameAccount account = GameAccount.builder()
                .user(user)
                .availableCash(initialCash)
                .build();
        gameAccountRepository.save(account);

        log.info("新玩家註冊成功：{}", user.getUsername());

        String token = jwtTokenProvider.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole().name(), "註冊成功，初始資金：" + initialCash);
    }

    /**
     * 玩家登入：驗證密碼，回傳 JWT。
     */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("找不到使用者"));

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("玩家登入：{}", user.getUsername());

        String token = jwtTokenProvider.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole().name(), "登入成功");
    }
}
