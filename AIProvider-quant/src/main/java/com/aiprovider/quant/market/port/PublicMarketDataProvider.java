package com.aiprovider.quant.market.port;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketSnapshot;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.model.PerpetualContract;
import com.aiprovider.quant.market.model.PublicMarketHealth;

import java.util.List;

/**
 * 公共行情数据提供方端口。
 *
 * 各交易所公共行情适配器实现该接口。所有方法必须真实访问上游，禁止返回伪造数据或空对象冒充成功。
 * 上游失败时直接抛出异常，由上层统一处理。
 */
public interface PublicMarketDataProvider {

    /** 返回该提供方标识。 */
    MarketProviderId providerId();

    /** 返回该提供方市场类型。 */
    MarketType marketType();

    /** 探测上游健康状态。 */
    PublicMarketHealth health();

    /**
     * 查询指定计价币种的永续合约目录。
     *
     * @param quoteAsset 计价币种，例如 "USDT"
     * @return 合约目录列表
     */
    List<PerpetualContract> contracts(String quoteAsset);

    /**
     * 查询单个合约的行情快照。
     *
     * @param symbol 合约符号，例如 "BTCUSDT"
     * @return 行情快照
     */
    MarketSnapshot snapshot(String symbol);

    /**
     * 查询单个合约的 K 线。
     *
     * @param symbol   合约符号
     * @param interval K 线周期
     * @param limit    K 线数量
     * @return K 线列表
     */
    List<MarketCandle> klines(String symbol, KlineInterval interval, int limit);
}
