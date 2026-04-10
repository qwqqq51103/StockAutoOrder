package com.stockgame.server.repository;

import com.stockgame.server.entity.GameAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface GameAccountRepository extends JpaRepository<GameAccount, Long> {

    Optional<GameAccount> findByUserId(Long userId);

    /** 悲觀鎖讀取（委託送出時使用，確保資金/持股不超賣） */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM GameAccount a WHERE a.user.id = :userId")
    Optional<GameAccount> findByUserIdForUpdate(Long userId);

    /** 排行榜：依已實現損益排序，取前 N 名 */
    @Query("SELECT a FROM GameAccount a ORDER BY a.realizedPnl DESC")
    List<GameAccount> findTopByRealizedPnl(org.springframework.data.domain.Pageable pageable);
}
