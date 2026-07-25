package com.aiprovider.config;

import com.aiprovider.config.quant.QuantMarketWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalHandler signalHandler;
    private final QuantMarketWebSocketHandler quantMarketHandler;

    public WebSocketConfig(SignalHandler signalHandler, QuantMarketWebSocketHandler quantMarketHandler) {
        this.signalHandler = signalHandler;
        this.quantMarketHandler = quantMarketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalHandler, "/ws/signal").setAllowedOrigins();
        registry.addHandler(quantMarketHandler, "/ws/quant/market").setAllowedOrigins();
    }
}
