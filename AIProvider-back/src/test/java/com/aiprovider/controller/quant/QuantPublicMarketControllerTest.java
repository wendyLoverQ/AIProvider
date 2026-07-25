package com.aiprovider.controller.quant;

import com.aiprovider.common.Result;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketSnapshot;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.model.PerpetualContract;
import com.aiprovider.quant.market.model.PublicMarketHealth;
import com.aiprovider.quant.market.service.PublicMarketQueryService;
import com.aiprovider.quant.market.service.PublicMarketQueryService.ProviderInfo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QuantPublicMarketController} 单元测试。
 *
 * 验证各端点委托 {@link PublicMarketQueryService}，并使用统一 {@link Result} 封装。
 * snapshot 与 klines 的 symbol 存在性校验通过 mock contracts 返回值实现。
 */
class QuantPublicMarketControllerTest {

    @Test
    void providersDelegatesToService() {
        PublicMarketQueryService service = mock(PublicMarketQueryService.class);
        List<ProviderInfo> infos = List.of(new ProviderInfo(MarketProviderId.BINANCE_USDM, MarketType.USDM_PERPETUAL));
        when(service.providers()).thenReturn(infos);

        QuantPublicMarketController controller = new QuantPublicMarketController(service);
        Result<List<ProviderInfo>> result = controller.providers();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(infos);
        verify(service).providers();
    }

    @Test
    void healthDelegatesToService() {
        PublicMarketQueryService service = mock(PublicMarketQueryService.class);
        PublicMarketHealth health = new PublicMarketHealth();
        health.setProvider(MarketProviderId.BINANCE_USDM);
        health.setAvailable(true);
        when(service.health(MarketProviderId.BINANCE_USDM)).thenReturn(health);

        QuantPublicMarketController controller = new QuantPublicMarketController(service);
        Result<PublicMarketHealth> result = controller.health("BINANCE_USDM");

        assertThat(result.getData()).isSameAs(health);
        verify(service).health(MarketProviderId.BINANCE_USDM);
    }

    @Test
    void contractsDelegatesToService() {
        PublicMarketQueryService service = mock(PublicMarketQueryService.class);
        PerpetualContract contract = new PerpetualContract();
        contract.setSymbol("BTCUSDT");
        when(service.contracts(MarketProviderId.BINANCE_USDM, "USDT")).thenReturn(List.of(contract));

        QuantPublicMarketController controller = new QuantPublicMarketController(service);
        Result<List<PerpetualContract>> result = controller.contracts("BINANCE_USDM", "USDT");

        assertThat(result.getData()).hasSize(1);
        verify(service).contracts(MarketProviderId.BINANCE_USDM, "USDT");
    }

    @Test
    void snapshotDelegatesToServiceAfterSymbolExistsCheck() {
        PublicMarketQueryService service = mock(PublicMarketQueryService.class);
        PerpetualContract contract = new PerpetualContract();
        contract.setSymbol("BTCUSDT");
        // 存在性校验调用 contracts
        when(service.contracts(MarketProviderId.BINANCE_USDM, "USDT")).thenReturn(List.of(contract));
        MarketSnapshot snapshot = new MarketSnapshot();
        snapshot.setSymbol("BTCUSDT");
        snapshot.setLastPrice(new BigDecimal("50000.00"));
        when(service.snapshot(MarketProviderId.BINANCE_USDM, "BTCUSDT")).thenReturn(snapshot);

        QuantPublicMarketController controller = new QuantPublicMarketController(service);
        Result<MarketSnapshot> result = controller.snapshot("BINANCE_USDM", "BTCUSDT");

        assertThat(result.getData()).isSameAs(snapshot);
        verify(service).contracts(MarketProviderId.BINANCE_USDM, "USDT");
        verify(service).snapshot(MarketProviderId.BINANCE_USDM, "BTCUSDT");
    }

    @Test
    void snapshotRejectsNonExistentSymbol() {
        PublicMarketQueryService service = mock(PublicMarketQueryService.class);
        when(service.contracts(MarketProviderId.BINANCE_USDM, "USDT")).thenReturn(Collections.emptyList());

        QuantPublicMarketController controller = new QuantPublicMarketController(service);
        assertThatThrownBy(() -> controller.snapshot("BINANCE_USDM", "BTCUSDT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void klinesDelegatesToServiceAfterValidation() {
        PublicMarketQueryService service = mock(PublicMarketQueryService.class);
        PerpetualContract contract = new PerpetualContract();
        contract.setSymbol("BTCUSDT");
        when(service.contracts(MarketProviderId.BINANCE_USDM, "USDT")).thenReturn(List.of(contract));
        MarketCandle candle = new MarketCandle();
        candle.setSymbol("BTCUSDT");
        candle.setInterval(KlineInterval.M15);
        when(service.klines(MarketProviderId.BINANCE_USDM, "BTCUSDT", KlineInterval.M15, 120))
                .thenReturn(List.of(candle));

        QuantPublicMarketController controller = new QuantPublicMarketController(service);
        Result<List<MarketCandle>> result = controller.klines("BINANCE_USDM", "BTCUSDT", "15m", 120);

        assertThat(result.getData()).hasSize(1);
        verify(service).klines(MarketProviderId.BINANCE_USDM, "BTCUSDT", KlineInterval.M15, 120);
    }

    @Test
    void klinesRejectsInvalidInterval() {
        PublicMarketQueryService service = mock(PublicMarketQueryService.class);
        PerpetualContract contract = new PerpetualContract();
        contract.setSymbol("BTCUSDT");
        when(service.contracts(MarketProviderId.BINANCE_USDM, "USDT")).thenReturn(List.of(contract));

        QuantPublicMarketController controller = new QuantPublicMarketController(service);
        assertThatThrownBy(() -> controller.klines("BINANCE_USDM", "BTCUSDT", "2m", 120))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void klinesRejectsLimitOutOfRange() {
        PublicMarketQueryService service = mock(PublicMarketQueryService.class);
        PerpetualContract contract = new PerpetualContract();
        contract.setSymbol("BTCUSDT");
        when(service.contracts(MarketProviderId.BINANCE_USDM, "USDT")).thenReturn(List.of(contract));

        QuantPublicMarketController controller = new QuantPublicMarketController(service);
        assertThatThrownBy(() -> controller.klines("BINANCE_USDM", "BTCUSDT", "15m", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20");
        assertThatThrownBy(() -> controller.klines("BINANCE_USDM", "BTCUSDT", "15m", 600))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidProviderThrowsIllegalArgumentException() {
        PublicMarketQueryService service = mock(PublicMarketQueryService.class);
        QuantPublicMarketController controller = new QuantPublicMarketController(service);

        assertThatThrownBy(() -> controller.health("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的行情提供方");
    }
}
