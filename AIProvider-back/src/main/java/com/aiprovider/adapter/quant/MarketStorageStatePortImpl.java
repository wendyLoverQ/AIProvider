package com.aiprovider.adapter.quant;

import com.aiprovider.mapper.MarketDatasetMapper;
import com.aiprovider.quant.market.history.port.MarketStorageStatePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 行情存储状态端口后端实现。
 *
 * 直接查询 q_market_dataset 表，根据 dataset 数量和缺口数量计算存储状态：
 * <ul>
 *   <li>无 dataset → MARKET_DATA_READY_EMPTY（表就绪但无数据）</li>
 *   <li>有 dataset 且全部 GapCount=0 → MARKET_DATA_AVAILABLE</li>
 *   <li>任一 dataset GapCount>0 → MARKET_DATA_GAPPED</li>
 * </ul>
 */
public class MarketStorageStatePortImpl implements MarketStorageStatePort {

    private static final Logger log = LoggerFactory.getLogger(MarketStorageStatePortImpl.class);

    public static final String STATE_EMPTY = "MARKET_DATA_READY_EMPTY";
    public static final String STATE_AVAILABLE = "MARKET_DATA_AVAILABLE";
    public static final String STATE_GAPPED = "MARKET_DATA_GAPPED";

    private final MarketDatasetMapper datasetMapper;

    public MarketStorageStatePortImpl(MarketDatasetMapper datasetMapper) {
        this.datasetMapper = datasetMapper;
    }

    @Override
    public String getStorageState() {
        long total = datasetMapper.countAll();
        if (total == 0) {
            log.debug("operation=get-storage-state total=0 state={}", STATE_EMPTY);
            return STATE_EMPTY;
        }
        long withGaps = datasetMapper.countWithGaps();
        String state = withGaps > 0 ? STATE_GAPPED : STATE_AVAILABLE;
        log.debug("operation=get-storage-state total={} withGaps={} state={}", total, withGaps, state);
        return state;
    }
}
