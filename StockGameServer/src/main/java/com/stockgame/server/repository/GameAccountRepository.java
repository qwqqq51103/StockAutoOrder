package com.stockgame.server.repository;

import com.stockgame.server.entity.GameAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GameAccountRepository extends JpaRepository<GameAccount, Long> {

    Optional<GameAccount> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM GameAccount a WHERE a.user.id = :userId")
    Optional<GameAccount> findByUserIdForUpdate(Long userId);

    @Query("SELECT a FROM GameAccount a ORDER BY a.realizedPnl DESC")
    List<GameAccount> findTopByRealizedPnl(Pageable pageable);

    @Query("SELECT a FROM GameAccount a JOIN FETCH a.user ORDER BY a.realizedPnl DESC")
    List<GameAccount> findTopWithUsers(Pageable pageable);
}
