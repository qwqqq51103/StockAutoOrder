package com.stockgame.server.repository;

import com.stockgame.server.entity.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    /** 最近 200 筆成交（成交帶 / 走勢圖） */
    List<StockTransaction> findTop200ByOrderByExecutedAtDesc();

    /** 指定時間後的成交（K 線計算） */
    List<StockTransaction> findByExecutedAtAfterOrderByExecutedAtAsc(LocalDateTime since);

    /** 玩家相關成交（買或賣皆含） */
    @Query("SELECT t FROM StockTransaction t WHERE t.buyOrder.user.id = :userId OR t.sellOrder.user.id = :userId ORDER BY t.executedAt DESC")
    List<StockTransaction> findByUserId(@Param("userId") Long userId);

    /** 最新成交價（用 LIMIT 1） */
    @Query("SELECT t.price FROM StockTransaction t ORDER BY t.executedAt DESC LIMIT 1")
    Double findLatestPrice();
}
