package com.aiprovider.model.vo;

public class CryptoMarketSymbolVO {
    private final String exchangeId;
    private final String symbol;
    private final String baseAsset;
    private final String quoteAsset;

    public CryptoMarketSymbolVO(String symbol, String baseAsset, String quoteAsset) {
        this(null, symbol, baseAsset, quoteAsset);
    }

    public CryptoMarketSymbolVO(String exchangeId, String symbol, String baseAsset, String quoteAsset) {
        this.exchangeId = exchangeId;
        this.symbol = symbol;
        this.baseAsset = baseAsset;
        this.quoteAsset = quoteAsset;
    }

    public String getSymbol() { return symbol; }
    public String getExchangeId() { return exchangeId; }
    public String getBaseAsset() { return baseAsset; }
    public String getQuoteAsset() { return quoteAsset; }
}
