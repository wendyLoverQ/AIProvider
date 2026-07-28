package com.aiprovider.quant.engine.paper;

import com.aiprovider.quant.execution.DirectionMode;
import com.aiprovider.quant.execution.order.ExecutionOrderType;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionPolicy;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.portfolio.sizing.MarketOrderQuantityRules;
import com.aiprovider.quant.portfolio.sizing.PositionSizingPolicyType;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskPolicy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public final class PaperTradingSessionConfig {
    private final String sessionId;
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final KlineInterval klineInterval;
    private final String strategyCode;
    private final String strategyVersion;
    private final Map<String, Integer> strategyParameters;
    private final DirectionMode directionMode;
    private final ExecutionOrderType orderType;
    private final PositionSizingPolicyType positionSizingPolicyType;
    private final BigDecimal fixedBaseQuantity;
    private final BigDecimal equityFraction;
    private final MarketOrderQuantityRules marketOrderQuantityRules;
    private final BigDecimal leverage;
    private final PreTradeRiskPolicy preTradeRiskPolicy;
    private final SimulatedExecutionPolicy simulatedExecutionPolicy;

    public PaperTradingSessionConfig(
            String sessionId,
            MarketProviderId provider,
            MarketType marketType,
            String symbol,
            KlineInterval klineInterval,
            String strategyCode,
            String strategyVersion,
            Map<String, Integer> strategyParameters,
            PositionSizingPolicyType positionSizingPolicyType,
            BigDecimal fixedBaseQuantity,
            BigDecimal equityFraction,
            MarketOrderQuantityRules marketOrderQuantityRules,
            BigDecimal leverage,
            PreTradeRiskPolicy preTradeRiskPolicy,
            SimulatedExecutionPolicy simulatedExecutionPolicy) {
        this(sessionId, provider, marketType, symbol, klineInterval, strategyCode, strategyVersion,
                strategyParameters, DirectionMode.LONG_ONLY, ExecutionOrderType.MARKET,
                positionSizingPolicyType, fixedBaseQuantity, equityFraction, marketOrderQuantityRules,
                leverage, preTradeRiskPolicy, simulatedExecutionPolicy);
    }

    public PaperTradingSessionConfig(
            String sessionId,
            MarketProviderId provider,
            MarketType marketType,
            String symbol,
            KlineInterval klineInterval,
            String strategyCode,
            String strategyVersion,
            Map<String, Integer> strategyParameters,
            DirectionMode directionMode,
            ExecutionOrderType orderType,
            PositionSizingPolicyType positionSizingPolicyType,
            BigDecimal fixedBaseQuantity,
            BigDecimal equityFraction,
            MarketOrderQuantityRules marketOrderQuantityRules,
            BigDecimal leverage,
            PreTradeRiskPolicy preTradeRiskPolicy,
            SimulatedExecutionPolicy simulatedExecutionPolicy) {
        validate(sessionId, provider, marketType, symbol, klineInterval, strategyCode, strategyVersion,
                strategyParameters, directionMode, orderType, positionSizingPolicyType, fixedBaseQuantity,
                equityFraction, marketOrderQuantityRules, leverage, preTradeRiskPolicy,
                simulatedExecutionPolicy);
        this.sessionId = sessionId;
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.klineInterval = klineInterval;
        this.strategyCode = strategyCode;
        this.strategyVersion = strategyVersion;
        this.strategyParameters = Map.copyOf(strategyParameters);
        this.directionMode = directionMode;
        this.orderType = orderType;
        this.positionSizingPolicyType = positionSizingPolicyType;
        this.fixedBaseQuantity = fixedBaseQuantity;
        this.equityFraction = equityFraction;
        this.marketOrderQuantityRules = marketOrderQuantityRules;
        this.leverage = leverage;
        this.preTradeRiskPolicy = preTradeRiskPolicy;
        this.simulatedExecutionPolicy = simulatedExecutionPolicy;
    }

    private static void validate(
            String sessionId, MarketProviderId provider, MarketType marketType, String symbol,
            KlineInterval interval, String strategyCode, String strategyVersion,
            Map<String, Integer> strategyParameters, DirectionMode directionMode,
            ExecutionOrderType orderType, PositionSizingPolicyType sizingPolicy,
            BigDecimal fixedBaseQuantity, BigDecimal equityFraction,
            MarketOrderQuantityRules rules, BigDecimal leverage,
            PreTradeRiskPolicy riskPolicy, SimulatedExecutionPolicy executionPolicy) {
        if (blank(sessionId) || provider == null || marketType == null || blank(symbol)
                || interval == null || blank(strategyCode) || blank(strategyVersion)
                || strategyParameters == null || directionMode == null || orderType == null
                || sizingPolicy == null || rules == null || leverage == null
                || riskPolicy == null || executionPolicy == null) {
            throw invalid("required configuration field is missing");
        }
        for (Map.Entry<String, Integer> entry : strategyParameters.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw invalid("strategyParameters must not contain null keys or values");
            }
        }
        if (marketType != MarketType.USDM_PERPETUAL
                || directionMode != DirectionMode.LONG_ONLY
                || orderType != ExecutionOrderType.MARKET
                || leverage.compareTo(BigDecimal.ONE) != 0) {
            throw invalid("only USDM_PERPETUAL, LONG_ONLY, MARKET and leverage 1 are supported");
        }
        if (rules.provider() != provider || rules.marketType() != marketType
                || !rules.symbol().equals(symbol)) {
            throw invalid("marketOrderQuantityRules context does not match the session");
        }
        if (!executionPolicy.getFeeAsset().equals(rules.quoteAsset())) {
            throw invalid("execution feeAsset must equal quantity-rules quoteAsset");
        }
        if (sizingPolicy == PositionSizingPolicyType.FIXED_BASE_QUANTITY) {
            if (fixedBaseQuantity == null || fixedBaseQuantity.signum() <= 0 || equityFraction != null) {
                throw invalid("fixed quantity mode requires only fixedBaseQuantity");
            }
        } else if (sizingPolicy == PositionSizingPolicyType.EQUITY_FRACTION) {
            if (equityFraction == null || equityFraction.signum() <= 0
                    || equityFraction.compareTo(BigDecimal.ONE) > 0 || fixedBaseQuantity != null) {
                throw invalid("equity fraction mode requires only equityFraction in (0, 1]");
            }
        } else {
            throw invalid("position sizing policy is not supported");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static PaperTradingException invalid(String message) {
        return new PaperTradingException(PaperTradingException.PAPER_TRADING_CONFIG_INVALID, message);
    }

    public String getSessionId() { return sessionId; }
    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public KlineInterval getKlineInterval() { return klineInterval; }
    public String getStrategyCode() { return strategyCode; }
    public String getStrategyVersion() { return strategyVersion; }
    public Map<String, Integer> getStrategyParameters() { return strategyParameters; }
    public DirectionMode getDirectionMode() { return directionMode; }
    public ExecutionOrderType getOrderType() { return orderType; }
    public PositionSizingPolicyType getPositionSizingPolicyType() { return positionSizingPolicyType; }
    public BigDecimal getFixedBaseQuantity() { return fixedBaseQuantity; }
    public BigDecimal getEquityFraction() { return equityFraction; }
    public MarketOrderQuantityRules getMarketOrderQuantityRules() { return marketOrderQuantityRules; }
    public BigDecimal getLeverage() { return leverage; }
    public PreTradeRiskPolicy getPreTradeRiskPolicy() { return preTradeRiskPolicy; }
    public SimulatedExecutionPolicy getSimulatedExecutionPolicy() { return simulatedExecutionPolicy; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperTradingSessionConfig that)) return false;
        return Objects.equals(sessionId, that.sessionId) && provider == that.provider
                && marketType == that.marketType && Objects.equals(symbol, that.symbol)
                && klineInterval == that.klineInterval && Objects.equals(strategyCode, that.strategyCode)
                && Objects.equals(strategyVersion, that.strategyVersion)
                && Objects.equals(strategyParameters, that.strategyParameters)
                && directionMode == that.directionMode && orderType == that.orderType
                && positionSizingPolicyType == that.positionSizingPolicyType
                && Objects.equals(fixedBaseQuantity, that.fixedBaseQuantity)
                && Objects.equals(equityFraction, that.equityFraction)
                && Objects.equals(marketOrderQuantityRules, that.marketOrderQuantityRules)
                && Objects.equals(leverage, that.leverage)
                && riskPolicyEquals(preTradeRiskPolicy, that.preTradeRiskPolicy)
                && Objects.equals(simulatedExecutionPolicy, that.simulatedExecutionPolicy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, provider, marketType, symbol, klineInterval, strategyCode,
                strategyVersion, strategyParameters, directionMode, orderType, positionSizingPolicyType,
                fixedBaseQuantity, equityFraction, marketOrderQuantityRules, leverage,
                preTradeRiskPolicy.getMaxOrderNotionalRatio(),
                preTradeRiskPolicy.getMaxTotalExposureRatio(),
                preTradeRiskPolicy.getMinimumRemainingCapitalRatio(),
                preTradeRiskPolicy.getMaxDailyLossRatio(),
                preTradeRiskPolicy.getMaxConsecutiveLosses(), simulatedExecutionPolicy);
    }

    private static boolean riskPolicyEquals(PreTradeRiskPolicy left, PreTradeRiskPolicy right) {
        return Objects.equals(left.getMaxOrderNotionalRatio(), right.getMaxOrderNotionalRatio())
                && Objects.equals(left.getMaxTotalExposureRatio(), right.getMaxTotalExposureRatio())
                && Objects.equals(left.getMinimumRemainingCapitalRatio(), right.getMinimumRemainingCapitalRatio())
                && Objects.equals(left.getMaxDailyLossRatio(), right.getMaxDailyLossRatio())
                && left.getMaxConsecutiveLosses() == right.getMaxConsecutiveLosses();
    }
}
