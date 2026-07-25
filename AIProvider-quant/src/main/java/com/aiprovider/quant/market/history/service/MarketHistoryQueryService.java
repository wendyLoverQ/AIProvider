package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.history.model.HistoricalKlinePage;
import com.aiprovider.quant.market.history.model.MarketDataGap;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.port.MarketCandleRepository;
import com.aiprovider.quant.market.history.port.MarketDataGapRepository;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.model.MarketProviderId;

import java.util.List;

/**
 * 历史行情查询服务。
 *
 * 提供数据集列表、数据集详情、缺口列表和 K 线预览的查询能力。
 * 只读操作，不修改数据。
 */
public class MarketHistoryQueryService {

    private static final int MAX_PREVIEW_PAGE_SIZE = 500;
    private static final int DEFAULT_PREVIEW_PAGE_SIZE = 100;

    private final MarketDatasetRepository datasetRepository;
    private final MarketCandleRepository candleRepository;
    private final MarketDataGapRepository gapRepository;

    public MarketHistoryQueryService(MarketDatasetRepository datasetRepository,
                                      MarketCandleRepository candleRepository,
                                      MarketDataGapRepository gapRepository) {
        this.datasetRepository = datasetRepository;
        this.candleRepository = candleRepository;
        this.gapRepository = gapRepository;
    }

    /**
     * 分页查询数据集列表。
     */
    public List<MarketDataset> listDatasets(MarketProviderId provider, String symbol,
                                            String intervalCode, String status,
                                            int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return datasetRepository.findPage(provider, symbol, intervalCode, status, pageSize, offset);
    }

    /**
     * 查询单个数据集。
     */
    public MarketDataset getDataset(long datasetId) {
        return datasetRepository.findById(datasetId);
    }

    /**
     * 查询数据集缺口列表。
     */
    public List<MarketDataGap> getGaps(long datasetId) {
        return gapRepository.findByDataset(datasetId);
    }

    /**
     * 分页查询 K 线预览，按 openTime 倒序。
     *
     * @param datasetId     数据集 ID
     * @param startOpenTimeMs 起始 openTime（包含），null 表示不限制
     * @param endOpenTimeMs   结束 openTime（包含），null 表示不限制
     * @param page          页码（从 1 开始）
     * @param pageSize      每页大小，默认 100，最大 500
     */
    public HistoricalKlinePage getCandles(long datasetId, Long startOpenTimeMs, Long endOpenTimeMs,
                                           int page, int pageSize) {
        int effectivePageSize = pageSize <= 0 ? DEFAULT_PREVIEW_PAGE_SIZE : Math.min(pageSize, MAX_PREVIEW_PAGE_SIZE);
        int offset = Math.max(0, (page - 1) * effectivePageSize);

        // total 与 records 使用完全相同的筛选范围
        long total = candleRepository.countByDatasetAndRange(datasetId, startOpenTimeMs, endOpenTimeMs);
        List<HistoricalCandle> candles = candleRepository.findPage(datasetId, startOpenTimeMs, endOpenTimeMs,
                effectivePageSize, offset);

        HistoricalKlinePage result = new HistoricalKlinePage();
        result.setCandles(candles);
        result.setPage(page);
        result.setPageSize(effectivePageSize);
        result.setTotal(total);
        return result;
    }
}
