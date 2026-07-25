package com.aiprovider.quant.market.history.model;

import java.util.List;

/**
 * K 线分页查询结果。
 *
 * 按 openTime 倒序返回，单次最多 500 根。
 */
public class HistoricalKlinePage {

    private List<HistoricalCandle> candles;
    private int page;
    private int pageSize;
    private long total;

    public List<HistoricalCandle> getCandles() { return candles; }
    public void setCandles(List<HistoricalCandle> candles) { this.candles = candles; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
