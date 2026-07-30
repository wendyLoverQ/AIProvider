package com.aiprovider.quant.engine.paper;

import com.aiprovider.quant.account.paper.DefaultPaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderStateMachine;
import com.aiprovider.quant.execution.order.ExecutionOrderType;
import com.aiprovider.quant.execution.simulation.DefaultSimulatedMarketOrderEngine;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionPolicy;
import com.aiprovider.quant.execution.simulation.SimulatedTopOfBook;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.portfolio.sizing.MarketOrderQuantityRules;
import com.aiprovider.quant.portfolio.sizing.PositionSizingPolicyType;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskPolicy;
import com.aiprovider.quant.strategy.runtime.StrategyRuntimePosition;
import com.aiprovider.quant.strategy.runtime.StrategySignalDecision;
import com.aiprovider.quant.strategy.runtime.StrategySignalDecisionReason;
import com.aiprovider.quant.strategy.runtime.StrategySignalEngine;
import com.aiprovider.quant.strategy.runtime.StrategySignalRequest;
import com.aiprovider.quant.strategy.runtime.StrategySignalType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPaperTradingEngineRestoreTest {
    private static final MarketProviderId PROVIDER = MarketProviderId.BINANCE_USDM;
    private static final MarketType MARKET = MarketType.USDM_PERPETUAL;
    private static final String SYMBOL = "BTCUSDT";
    private static final Instant BASE = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void restoresSubmittedSessionAndContinuesOrderExecution() {
        PaperTradingEngine engine = new DefaultPaperTradingEngine(new EnterSignalEngine(),
                new com.aiprovider.quant.portfolio.sizing.DefaultPositionSizingEngine(),
                new com.aiprovider.quant.risk.pretrade.DefaultPreTradeRiskEngine(),
                new ExecutionOrderStateMachine(), new DefaultSimulatedMarketOrderEngine(),
                new DefaultPaperAccountEngine());
        PaperTradingSessionConfig config = config();
        PaperAccountSnapshot account = new DefaultPaperAccountEngine().initialize(
                "account-1", PROVIDER, MARKET, "USDT", new BigDecimal("10000"),
                LocalDate.of(2026, 7, 30), BASE);
        HistoricalCandle candle = candle("100");
        PaperTradingSessionSnapshot submitted = engine.evaluateClosedCandles(
                engine.createSession(config, account), List.of(candle), BASE.plusSeconds(61)).getSession();

        PaperTradingSessionSnapshot restored = engine.restore(new PaperTradingSessionRestoreRequest(
                submitted.getConfig(), submitted.getPaperAccountSnapshot(), submitted.getPendingOrderSnapshot(),
                submitted.getLastOrderSnapshot(), submitted.getLastSignalDecision(),
                submitted.getLastSizingResult(), submitted.getLastRiskDecision(),
                submitted.getLastEvaluatedCandle(), submitted.getLastUpdatedAt()));

        assertThat(restored).isEqualTo(submitted);
        SimulatedTopOfBook book = new SimulatedTopOfBook(
                PROVIDER, MARKET, SYMBOL, BASE.plusSeconds(62), new BigDecimal("99"), BigDecimal.TEN,
                new BigDecimal("100"), BigDecimal.TEN);
        assertThat(engine.executePendingOrder(restored, book).getSession())
                .isEqualTo(engine.executePendingOrder(submitted, book).getSession());
    }

    @Test
    void rejectsOrderContextAndSignalCandleTemporalMismatch() {
        PaperTradingSessionConfig config = config();
        PaperAccountEngine accounts = new DefaultPaperAccountEngine();
        PaperAccountSnapshot account = accounts.initialize("account-1", PROVIDER, MARKET, "USDT",
                new BigDecimal("10000"), LocalDate.of(2026, 7, 30), BASE);
        PaperTradingSessionSnapshot session = new PaperTradingSessionSnapshot(
                config, account, null, null, null, null, null, signalCandleSnapshot("100"), BASE);
        PaperTradingEngine engine = new DefaultPaperTradingEngine();

        assertThatThrownBy(() -> engine.restore(new PaperTradingSessionRestoreRequest(
                config, account, null, null, null, null, null,
                session.getLastEvaluatedCandle(), BASE)))
                .isInstanceOf(PaperTradingException.class)
                .extracting("errorCode")
                .isEqualTo(PaperTradingException.PAPER_TRADING_RESTORE_INVALID);
    }

    private PaperTradingSessionConfig config() {
        return new PaperTradingSessionConfig("session-1", PROVIDER, MARKET, SYMBOL, KlineInterval.M1,
                "TEST", "1", Map.of("period", 3), PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                new BigDecimal("2.5"), null, new MarketOrderQuantityRules(PROVIDER, MARKET, SYMBOL,
                "USDT", 3, new BigDecimal("0.001"), new BigDecimal("0.001"),
                new BigDecimal("1000"), new BigDecimal("5")), BigDecimal.ONE,
                new PreTradeRiskPolicy(new BigDecimal(".9"), new BigDecimal(".9"), new BigDecimal(".01"),
                        new BigDecimal(".5"), 5), new SimulatedExecutionPolicy(new BigDecimal(".001"), "USDT", BigDecimal.ZERO));
    }

    private HistoricalCandle candle(String close) {
        return candleSource(close);
    }

    private HistoricalCandle candleSource(String close) {
        BigDecimal price = new BigDecimal(close);
        HistoricalCandle candle = new HistoricalCandle();
        candle.setProvider(PROVIDER);
        candle.setMarketType(MARKET);
        candle.setSymbol(SYMBOL);
        candle.setInterval(KlineInterval.M1);
        candle.setOpenTime(BASE);
        candle.setCloseTime(BASE.plusSeconds(60));
        candle.setOpenPrice(price);
        candle.setHighPrice(price);
        candle.setLowPrice(price);
        candle.setClosePrice(price);
        candle.setVolume(BigDecimal.TEN);
        candle.setQuoteVolume(new BigDecimal("1000"));
        candle.setTradeCount(10);
        return candle;
    }

    private PaperSignalCandleSnapshot signalCandleSnapshot(String close) {
        HistoricalCandle candle = candleSource(close);
        return PaperSignalCandleSnapshot.from(candle);
    }

    private static final class EnterSignalEngine implements StrategySignalEngine {
        @Override
        public StrategySignalDecision evaluate(StrategySignalRequest request) {
            HistoricalCandle candle = request.getCandles().get(request.getCandles().size() - 1);
            return new StrategySignalDecision(request.getStrategyCode(), request.getStrategyVersion(),
                    request.getStrategyParameters(), request.getProvider(), request.getMarketType(),
                    request.getSymbol(), request.getInterval(), request.getCurrentPosition(),
                    StrategySignalType.ENTER_LONG, request.getCandles().size() - 1, candle,
                    StrategySignalDecisionReason.ENTRY_RULE_MATCHED);
        }
    }
}
