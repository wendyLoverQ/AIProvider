package com.aiprovider.quant.market.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 永续合约元数据。
 *
 * 来源于 Binance /fapi/v1/exchangeInfo 中 contractType=PERPETUAL、status=TRADING、
 * quoteAsset=USDT、marginAsset=USDT 的 symbol。所有价格与数量使用 BigDecimal，
 * 禁止使用 double/float 保存。
 */
public class PerpetualContract {

    private MarketProviderId provider;
    private MarketType marketType;
    private String symbol;
    private String pair;
    private String baseAsset;
    private String quoteAsset;
    private String marginAsset;
    private String contractType;
    private String status;
    private Instant onboardDate;
    private int pricePrecision;
    private int quantityPrecision;
    private BigDecimal tickSize;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal stepSize;
    private BigDecimal minQty;
    private BigDecimal maxQty;
    private BigDecimal marketStepSize;
    private BigDecimal marketMinQty;
    private BigDecimal marketMaxQty;
    private BigDecimal minNotional;
    private List<KlineInterval> supportedIntervals;

    public PerpetualContract() {}

    public MarketProviderId getProvider() { return provider; }
    public void setProvider(MarketProviderId provider) { this.provider = provider; }

    public MarketType getMarketType() { return marketType; }
    public void setMarketType(MarketType marketType) { this.marketType = marketType; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }

    public String getBaseAsset() { return baseAsset; }
    public void setBaseAsset(String baseAsset) { this.baseAsset = baseAsset; }

    public String getQuoteAsset() { return quoteAsset; }
    public void setQuoteAsset(String quoteAsset) { this.quoteAsset = quoteAsset; }

    public String getMarginAsset() { return marginAsset; }
    public void setMarginAsset(String marginAsset) { this.marginAsset = marginAsset; }

    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getOnboardDate() { return onboardDate; }
    public void setOnboardDate(Instant onboardDate) { this.onboardDate = onboardDate; }

    public int getPricePrecision() { return pricePrecision; }
    public void setPricePrecision(int pricePrecision) { this.pricePrecision = pricePrecision; }

    public int getQuantityPrecision() { return quantityPrecision; }
    public void setQuantityPrecision(int quantityPrecision) { this.quantityPrecision = quantityPrecision; }

    public BigDecimal getTickSize() { return tickSize; }
    public void setTickSize(BigDecimal tickSize) { this.tickSize = tickSize; }

    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }

    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }

    public BigDecimal getStepSize() { return stepSize; }
    public void setStepSize(BigDecimal stepSize) { this.stepSize = stepSize; }

    public BigDecimal getMinQty() { return minQty; }
    public void setMinQty(BigDecimal minQty) { this.minQty = minQty; }

    public BigDecimal getMaxQty() { return maxQty; }
    public void setMaxQty(BigDecimal maxQty) { this.maxQty = maxQty; }

    public BigDecimal getMarketStepSize() { return marketStepSize; }
    public void setMarketStepSize(BigDecimal marketStepSize) { this.marketStepSize = marketStepSize; }

    public BigDecimal getMarketMinQty() { return marketMinQty; }
    public void setMarketMinQty(BigDecimal marketMinQty) { this.marketMinQty = marketMinQty; }

    public BigDecimal getMarketMaxQty() { return marketMaxQty; }
    public void setMarketMaxQty(BigDecimal marketMaxQty) { this.marketMaxQty = marketMaxQty; }

    public BigDecimal getMinNotional() { return minNotional; }
    public void setMinNotional(BigDecimal minNotional) { this.minNotional = minNotional; }

    public List<KlineInterval> getSupportedIntervals() { return supportedIntervals; }
    public void setSupportedIntervals(List<KlineInterval> supportedIntervals) { this.supportedIntervals = supportedIntervals; }
}
