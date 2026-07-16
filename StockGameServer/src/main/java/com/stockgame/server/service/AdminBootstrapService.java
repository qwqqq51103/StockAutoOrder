package com.stockgame.server.service;

import com.stockgame.server.entity.GameAccount;
import com.stockgame.server.entity.User;
import com.stockgame.server.repository.GameAccountRepository;
import com.stockgame.server.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapService {

    private final UserRepository userRepository;
    private final GameAccountRepository gameAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${game.admin.auto-create:true}")
    private boolean autoCreateAdmin;

    @Value("${game.admin.username:admin}")
    private String adminUsername;

    @Value("${game.admin.email:admin@stockgame.local}")
    private String adminEmail;

    @Value("${game.admin.password:admin123456}")
    private String adminPassword;

    @Value("${game.initial-cash:1000000.0}")
    private double initialCash;

    @PostConstruct
    @Transactional
    public void ensureAdminUser() {
        if (!autoCreateAdmin) {
            return;
        }

        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }

        User adminUser = User.builder()
                .username(adminUsername)
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(User.Role.ADMIN)
                .enabled(true)
                .build();
        userRepository.save(adminUser);

        GameAccount account = GameAccount.builder()
                .user(adminUser)
                .availableCash(initialCash)
                .build();
        gameAccountRepository.save(account);

        log.warn("Auto-created admin user '{}'. Change the default password immediately.", adminUsername);
    }
}
