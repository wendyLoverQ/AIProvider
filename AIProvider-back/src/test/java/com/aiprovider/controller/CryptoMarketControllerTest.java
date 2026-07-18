package com.aiprovider.controller;

import com.aiprovider.model.vo.CryptoMarketHealthVO;
import com.aiprovider.service.CryptoMarketProxyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CryptoMarketControllerTest {
    @Test
    void endpointsDelegateToMarketServiceAndUseUnifiedEnvelope() throws Exception {
        CryptoMarketProxyService service = mock(CryptoMarketProxyService.class);
        CryptoMarketHealthVO health = new CryptoMarketHealthVO("CCXT", true, 12, OffsetDateTime.now(), "4.5.66", 9);
        when(service.health()).thenReturn(health);
        when(service.klines("binance", "BTC/USDT", "15m", 240)).thenReturn(new ObjectMapper().readTree("[]"));
        when(service.symbols("binance", "USDT", 500)).thenReturn(Collections.emptyList());
        CryptoMarketController controller = new CryptoMarketController(service);

        assertThat(controller.health().getData()).isSameAs(health);
        assertThat(controller.klines("binance", "BTC/USDT", "15m", 240).getData().isArray()).isTrue();
        assertThat(controller.symbols("binance", "USDT", 500).getData()).isEmpty();
        verify(service).health();
        verify(service).klines("binance", "BTC/USDT", "15m", 240);
        verify(service).symbols("binance", "USDT", 500);
    }
}
