package com.aiprovider.service;

import com.aiprovider.model.vo.CryptoMarketHealthVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import com.aiprovider.model.vo.CryptoMarketSymbolVO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CryptoMarketProxyServiceTest {
    private MockRestServiceServer server;
    private CryptoMarketProxyService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new CryptoMarketProxyService("http://127.0.0.1:8890", restTemplate);
    }

    @Test
    void proxiesUnifiedCcxtOhlcvRequest() {
        server.expect(requestTo("http://127.0.0.1:8890/ohlcv?exchange=binance&symbol=BTC/USDT&timeframe=15m&limit=240"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"timestamp\":1,\"open\":10,\"high\":12,\"low\":9,\"close\":11,\"volume\":2}]", MediaType.APPLICATION_JSON));

        assertThat(service.klines("BINANCE", "btc/usdt", "15m", 240).isArray()).isTrue();
        server.verify();
    }

    @Test
    void returnsNormalizedSpotMarketCatalog() {
        server.expect(requestTo("http://127.0.0.1:8890/markets?exchange=okx&quote=USDT&limit=500"))
            .andRespond(withSuccess("[{\"symbol\":\"ETH/USDT\",\"baseAsset\":\"ETH\",\"quoteAsset\":\"USDT\"}]", MediaType.APPLICATION_JSON));

        List<CryptoMarketSymbolVO> symbols = service.symbols("okx", "usdt", 500);
        assertThat(symbols).extracting("symbol").containsExactly("ETH/USDT");
        assertThat(symbols.get(0).getExchangeId()).isEqualTo("okx");
        server.verify();
    }

    @Test
    void rejectsInvalidInputsBeforeCallingGateway() {
        assertThatThrownBy(() -> service.klines("bad host", "BTC/USDT", "15m", 240)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.klines("binance", "BTCUSDT", "15m", 240)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.klines("binance", "BTC/USDT", "7m", 240)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.depth("binance", "BTC/USDT", 500)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void healthReportsCcxtVersionAndExchangeCount() {
        server.expect(requestTo("http://127.0.0.1:8890/health"))
            .andRespond(withSuccess("{\"provider\":\"CCXT\",\"version\":\"4.5.66\",\"available\":true,\"exchangeCount\":9,\"checkedAt\":\"2026-07-17T06:00:00+08:00\"}", MediaType.APPLICATION_JSON));

        CryptoMarketHealthVO health = service.health();
        assertThat(health.isAvailable()).isTrue();
        assertThat(health.getProvider()).isEqualTo("CCXT");
        assertThat(health.getVersion()).isEqualTo("4.5.66");
        assertThat(health.getExchangeCount()).isEqualTo(9);
        server.verify();
    }

    @Test
    void refusesNonLoopbackGatewayHosts() {
        assertThatThrownBy(() -> new CryptoMarketProxyService("https://example.com", new RestTemplate()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
