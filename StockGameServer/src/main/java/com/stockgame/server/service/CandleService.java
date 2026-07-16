package com.stockgame.server.service;

import com.stockgame.server.dto.CandleDto;
import com.stockgame.server.repository.StockTransactionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.TreeMap;

/**
 * K 線蠟燭累積服務。
 * 每秒一次 tick，將連續的成交資料聚合為 1 分鐘 OHLC K 線。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CandleService {

    private static final int MAX_CANDLES = 500;

    private final StockTransactionRepository txRepository;

    private final Deque<CandleDto> completedCandles = new ArrayDeque<>();
    private CandleBuilder currentCandle  = null;
    private long          currentMinuteTs = -1;

    // ── 初始化：從 DB 載入過去 3 小時的歷史 K 線 ──────────────────────────
    @PostConstruct
    public void init() {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(3);
            var txList = txRepository.findByExecutedAtAfterOrderByExecutedAtAsc(since);
            if (txList.isEmpty()) return;

            TreeMap<Long, CandleBuilder> minuteMap = new TreeMap<>();
            for (var tx : txList) {
                long ts = toMinuteTs(tx.getExecutedAt());
                CandleBuilder cb = minuteMap.computeIfAbsent(ts,
                        t -> new CandleBuilder(t, tx.getPrice()));
                cb.update(tx.getPrice(), tx.getQuantity(),
                        tx.isBuyerInitiated() ? tx.getQuantity() : 0,
                        tx.isBuyerInitiated() ? 0 : tx.getQuantity());
            }
            minuteMap.values().forEach(cb -> completedCandles.addLast(cb.toDto()));
            while (completedCandles.size() > MAX_CANDLES) completedCandles.pollFirst();
            log.info("K 線歷史載入完成，共 {} 根", completedCandles.size());
        } catch (Exception e) {
            log.warn("K 線歷史載入失敗：{}", e.getMessage());
        }
    }

    // ── 每次 market tick 呼叫 ─────────────────────────────────────────────

    public synchronized void recordTick(double price, long volume, long buyVol, long sellVol) {
        long minuteTs = toMinuteTs(LocalDateTime.now());

        if (currentCandle == null || minuteTs != currentMinuteTs) {
            if (currentCandle != null) {
                completedCandles.addLast(currentCandle.toDto());
                while (completedCandles.size() > MAX_CANDLES) completedCandles.pollFirst();
            }
            currentCandle   = new CandleBuilder(minuteTs, price);
            currentMinuteTs = minuteTs;
        }

        currentCandle.update(price, volume, buyVol, sellVol);
    }

    // ── 查詢 ─────────────────────────────────────────────────────────────

    public synchronized List<CandleDto> getHistory() {
        List<CandleDto> result = new ArrayList<>(completedCandles);
        if (currentCandle != null) result.add(currentCandle.toDto());
        return result;
    }

    public synchronized CandleDto getLatestCandle() {
        return currentCandle != null ? currentCandle.toDto() : null;
    }

    // ── 工具 ─────────────────────────────────────────────────────────────

    private long toMinuteTs(LocalDateTime ldt) {
        Instant instant = ldt.atZone(ZoneId.systemDefault()).toInstant();
        long epochSec   = instant.getEpochSecond();
        return (epochSec / 60) * 60;          // 截斷至分鐘邊界
    }

    // ── 可變 K 線建構器（內部使用）────────────────────────────────────────

    private static class CandleBuilder {
        long   time;
        double open, high, low, close;
        long   volume, buyVol, sellVol;

        CandleBuilder(long time, double openPrice) {
            this.time  = time;
            this.open  = this.high = this.low = this.close = openPrice;
        }

        void update(double price, long vol, long bv, long sv) {
            high     = Math.max(high, price);
            low      = Math.min(low, price);
            close    = price;
            volume  += vol;
            buyVol  += bv;
            sellVol += sv;
        }

        CandleDto toDto() {
            return CandleDto.builder()
                    .time(time).open(open).high(high).low(low).close(close)
                    .volume(volume).buyVolume(buyVol).sellVolume(sellVol)
                    .build();
        }
    }
}
