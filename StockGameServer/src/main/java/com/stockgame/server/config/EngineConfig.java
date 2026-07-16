package com.stockgame.server.config;

import com.stockgame.server.engine.ServerOrderBook;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 撮合引擎相關 Bean 宣告。
 * ServerOrderBook 為有狀態的單例，整個應用共享一個實例。
 */
@Configuration
public class EngineConfig {

    @Bean
    public ServerOrderBook serverOrderBook() {
        return new ServerOrderBook();
    }
}
