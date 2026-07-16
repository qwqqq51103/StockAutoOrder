package com.stockgame.server.repository;

import com.stockgame.server.entity.StockOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StockOrderRepository extends JpaRepository<StockOrder, Long> {

    /** 玩家的有效委託（PENDING / PARTIAL）— 用 IN :statuses 傳入集合 */
    @Query("SELECT o FROM StockOrder o WHERE o.user.id = :userId AND o.status IN :statuses")
    List<StockOrder> findByUserIdAndStatusIn(
            @Param("userId")   Long userId,
            @Param("statuses") Collection<StockOrder.Status> statuses);

    /** 玩家歷史委託（最新 100 筆） */
    List<StockOrder> findTop100ByUser_IdOrderByCreatedAtDesc(Long userId);

    /** 訂單簿用：所有有效買單（價高在前） */
    @Query("SELECT o FROM StockOrder o WHERE o.side = :side AND o.status IN :statuses ORDER BY o.price DESC, o.createdAt ASC")
    List<StockOrder> findActiveBySideOrderByPrice(
            @Param("side")     StockOrder.Side side,
            @Param("statuses") Collection<StockOrder.Status> statuses);
}
