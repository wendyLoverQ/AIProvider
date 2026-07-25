package com.aiprovider.repository.quant;

import com.aiprovider.mapper.MarketDataGapMapper;
import com.aiprovider.quant.market.history.model.MarketDataGap;
import com.aiprovider.quant.market.history.port.MarketDataGapRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link MarketDataGapRepository} 的 MyBatis 实现。
 *
 * 直接委托 {@link MarketDataGapMapper}，不包含业务校验逻辑。
 * 时间字段的 epoch 毫秒互转由 Mapper 层的
 * {@link com.aiprovider.config.quant.InstantEpochMillisTypeHandler} 完成。
 */
@Repository
public class MarketDataGapRepositoryImpl implements MarketDataGapRepository {

    private final MarketDataGapMapper mapper;

    public MarketDataGapRepositoryImpl(MarketDataGapMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MarketDataGap> findByDataset(long datasetId) {
        return mapper.findByDataset(datasetId);
    }

    @Override
    public int deleteByDataset(long datasetId) {
        return mapper.deleteByDataset(datasetId);
    }

    @Override
    public int insertBatch(List<MarketDataGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return 0;
        }
        return mapper.insertBatch(gaps);
    }
}
