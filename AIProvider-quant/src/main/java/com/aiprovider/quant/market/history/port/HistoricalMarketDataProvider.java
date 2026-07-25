package com.aiprovider.quant.market.history.port;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.time.Instant;
import java.util.List;

/**
 * 历史行情数据提供方端口。
 *
 * 各交易所历史行情适配器实现该接口。同步服务通过该端口获取历史闭合 K 线，
 * 不直接依赖具体交易所类。
 *
 * 所有方法必须真实访问上游，禁止返回伪造数据。上游失败时直接抛出异常。
 */
public interface HistoricalMarketDataProvider {

    /** 返回该提供方标识。 */
    MarketProviderId providerId();

    /** 返回该提供方市场类型。 */
    MarketType marketType();

    /**
     * 获取指定时间范围内的已闭合 K 线。
     *
     * 语义为 [startInclusive, endExclusive)。返回的 K 线按 openTime 升序排列。
     * 只返回 closeTime < 上游服务器时间的已闭合 K 线。
     *
     * @param symbol          合约符号，例如 "BTCUSDT"
     * @param interval        K 线周期
     * @param startInclusive   开始时间（包含），epoch 毫秒
     * @param endExclusive    结束时间（不包含），epoch 毫秒
     * @param limit           单次请求最大数量
     * @return 已闭合 K 线列表，按 openTime 升序
     */
    List<MarketCandle> fetchClosedKlines(String symbol, KlineInterval interval,
                                          long startInclusive, long endExclusive, int limit);

    /**
     * 获取上游服务器当前时间，用于判断 K 线是否已闭合。
     *
     * @return 服务器时间 Instant
     */
    Instant serverTime();
}
