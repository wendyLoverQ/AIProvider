package com.aiprovider.quant.market.history.port;

import com.aiprovider.quant.market.history.model.ArchiveKlineFile;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import java.util.List;
import java.util.function.Consumer;

/**
 * Binance 官方历史数据包下载与解析端口。
 *
 * 实现由 AIProvider-back 提供，使用 JDK HttpClient 下载官方 ZIP 压缩包，
 * 校验 SHA-256 CHECKSUM，流式解析 CSV 并转换为 MarketCandle。
 *
 * 路径规则来源：binance/binance-public-data (MIT)
 * data/futures/um/{daily|monthly}/klines/{SYMBOL}/{INTERVAL}/{filename}
 */
public interface HistoricalArchiveProvider {

    /**
     * 下载并解析一个官方 ZIP 数据包，流式回调解析出的 K 线。
     *
     * 实现负责：
     * 1. 下载 .zip.CHECKSUM 和 .zip 到临时文件
     * 2. SHA-256 校验
     * 3. ZIP 安全检查（Zip Slip 防护）
     * 4. 流式 CSV 解析（1000 行一批）
     * 5. 转换为 MarketCandle 并通过 consumer 回调
     * 6. 清理临时文件
     *
     * @param file      归档文件描述
     * @param symbol    合约符号（大写）
     * @param interval  K 线周期
     * @param consumer  K 线回调，每批最多 1000 条
     */
    void downloadAndParse(ArchiveKlineFile file, String symbol, KlineInterval interval,
                          Consumer<List<MarketCandle>> consumer);
}
