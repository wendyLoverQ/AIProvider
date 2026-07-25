package com.aiprovider.repository.quant;

import com.aiprovider.mapper.MarketCandleMapper;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.history.port.MarketCandleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link MarketCandleRepository} 的 MyBatis 实现。
 *
 * 直接委托 {@link MarketCandleMapper}，不包含业务校验逻辑。
 * 时间字段的 epoch 毫秒互转由 Mapper 层的 {@link com.aiprovider.config.quant.InstantEpochMillisTypeHandler} 完成。
 */
@Repository
public class MarketCandleRepositoryImpl implements MarketCandleRepository {

    private final MarketCandleMapper mapper;

    public MarketCandleRepositoryImpl(MarketCandleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<HistoricalCandle> findByOpenTimes(long datasetId, List<Long> openTimeMs) {
        return mapper.findByOpenTimes(datasetId, openTimeMs);
    }

    @Override
    public int insertBatch(List<HistoricalCandle> candles) {
        if (candles == null || candles.isEmpty()) {
            return 0;
        }
        return mapper.insertBatch(candles);
    }

    @Override
    public List<HistoricalCandle> findPage(long datasetId, Long startOpenTimeMs, Long endOpenTimeMs,
                                            int limit, int offset) {
        return mapper.findPage(datasetId, startOpenTimeMs, endOpenTimeMs, limit, offset);
    }

    @Override
    public long countByDataset(long datasetId) {
        return mapper.countByDataset(datasetId);
    }

    @Override
    public List<Long> streamOpenTimesAscending(long datasetId, int batchSize, long afterOpenTimeMs) {
        return mapper.streamOpenTimesAscending(datasetId, batchSize, afterOpenTimeMs);
    }
}
