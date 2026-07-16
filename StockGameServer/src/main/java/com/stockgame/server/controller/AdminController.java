package com.stockgame.server.controller;

import com.stockgame.server.dto.AdminAnnouncementRequest;
import com.stockgame.server.dto.AdminMarketStatusDto;
import com.stockgame.server.dto.AdminSimulatorConfigRequest;
import com.stockgame.server.dto.AdminUserAccountDto;
import com.stockgame.server.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/status")
    public ResponseEntity<AdminMarketStatusDto> getStatus() {
        return ResponseEntity.ok(adminService.getMarketStatus());
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AdminUserAccountDto>> getAccounts() {
        return ResponseEntity.ok(adminService.getAccounts());
    }

    @PostMapping("/market/open")
    public ResponseEntity<AdminMarketStatusDto> openMarket() {
        return ResponseEntity.ok(adminService.setMarketOpen(true));
    }

    @PostMapping("/market/close")
    public ResponseEntity<AdminMarketStatusDto> closeMarket() {
        return ResponseEntity.ok(adminService.setMarketOpen(false));
    }

    @PostMapping("/market/tick")
    public ResponseEntity<AdminMarketStatusDto> runTick() {
        return ResponseEntity.ok(adminService.runTick());
    }

    @PostMapping("/simulator/config")
    public ResponseEntity<AdminMarketStatusDto> updateSimulatorConfig(
            @Valid @RequestBody AdminSimulatorConfigRequest request
    ) {
        return ResponseEntity.ok(adminService.updateSimulatorConfig(request));
    }

    @PostMapping("/announcement")
    public ResponseEntity<Map<String, String>> announce(@Valid @RequestBody AdminAnnouncementRequest request) {
        adminService.broadcastAnnouncement(request);
        return ResponseEntity.ok(Map.of("message", "公告已送出。"));
    }

    @PostMapping("/users/{username}/enable")
    public ResponseEntity<AdminUserAccountDto> enableUser(@PathVariable String username) {
        return ResponseEntity.ok(adminService.setUserEnabled(username, true));
    }

    @PostMapping("/users/{username}/disable")
    public ResponseEntity<AdminUserAccountDto> disableUser(@PathVariable String username) {
        return ResponseEntity.ok(adminService.setUserEnabled(username, false));
    }

    @PostMapping("/accounts/{username}/reset")
    public ResponseEntity<AdminUserAccountDto> resetAccount(@PathVariable String username) {
        return ResponseEntity.ok(adminService.resetAccount(username));
    }
}
