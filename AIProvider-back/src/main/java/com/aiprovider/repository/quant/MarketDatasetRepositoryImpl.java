package com.aiprovider.repository.quant;

import com.aiprovider.mapper.MarketDatasetMapper;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * {@link MarketDatasetRepository} 的 MyBatis 实现。
 *
 * 数据库列以枚举 name() 字符串存储（Provider、MarketType、DataType、Status、IntervalCode），
 * Mapper 的 findByKey/findPage 接受 String 参数，本实现负责将端口层的枚举转换为字符串。
 */
@Repository
public class MarketDatasetRepositoryImpl implements MarketDatasetRepository {

    private final MarketDatasetMapper mapper;

    public MarketDatasetRepositoryImpl(MarketDatasetMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public MarketDataset findByKey(MarketProviderId provider, MarketType marketType,
                                    String dataType, String symbol, String intervalCode) {
        return mapper.findByKey(provider.name(), marketType.name(), dataType, symbol, intervalCode);
    }

    @Override
    public MarketDataset findById(long datasetId) {
        return mapper.findById(datasetId);
    }

    @Override
    public List<MarketDataset> findPage(MarketProviderId provider, String symbol,
                                         String intervalCode, String status, int limit, int offset) {
        String providerStr = provider != null ? provider.name() : null;
        return mapper.findPage(providerStr, symbol, intervalCode, status, limit, offset);
    }

    @Override
    public long insert(MarketDataset dataset) {
        mapper.insert(dataset);
        return dataset.getId();
    }

    @Override
    public int updateStats(MarketDataset dataset) {
        return mapper.updateStats(dataset);
    }

    @Override
    public int updateLastSync(long datasetId, String lastSyncTaskId, Instant lastSuccessfulSyncAt) {
        return mapper.updateLastSync(datasetId, lastSyncTaskId, lastSuccessfulSyncAt);
    }
}
