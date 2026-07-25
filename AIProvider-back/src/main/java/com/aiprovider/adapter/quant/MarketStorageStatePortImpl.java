package com.aiprovider.adapter.quant;

import com.aiprovider.mapper.MarketDatasetMapper;
import com.aiprovider.quant.market.history.port.MarketStorageStatePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 行情存储状态端口后端实现。
 *
 * 直接查询 q_market_dataset 表，根据 CandleCount 和 Status 计算存储状态：
 * <ul>
 *   <li>无 dataset 或所有 dataset 的 CandleCount=0 → MARKET_DATA_READY_EMPTY</li>
 *   <li>存在 CandleCount>0 的 dataset，且其中存在非 CONTIGUOUS 状态 → MARKET_DATA_GAPPED</li>
 *   <li>存在 CandleCount>0 的 dataset，且全部为 CONTIGUOUS 状态 → MARKET_DATA_AVAILABLE</li>
 * </ul>
 *
 * <p>不依赖 GapCount 单独判断，避免空数据集（CandleCount=0, GapCount=0, Status=EMPTY）
 * 被误判为 MARKET_DATA_AVAILABLE。</p>
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
        long withCandles = datasetMapper.countWithCandles();
        if (withCandles == 0) {
            log.debug("operation=get-storage-state withCandles=0 state={}", STATE_EMPTY);
            return STATE_EMPTY;
        }
        long notContiguous = datasetMapper.countWithCandlesNotContiguous();
        String state = notContiguous > 0 ? STATE_GAPPED : STATE_AVAILABLE;
        log.debug("operation=get-storage-state withCandles={} notContiguous={} state={}", withCandles, notContiguous, state);
        return state;
    }
}
