package com.stockgame.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** K 線蠟燭資料（1 分鐘為一根） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandleDto {
    private long   time;        // Unix 時間戳（UTC 秒）
    private double open;
    private double high;
    private double low;
    private double close;
    private long   volume;      // 總成交量
    private long   buyVolume;   // 主動買量（外盤）
    private long   sellVolume;  // 主動賣量（內盤）
}
