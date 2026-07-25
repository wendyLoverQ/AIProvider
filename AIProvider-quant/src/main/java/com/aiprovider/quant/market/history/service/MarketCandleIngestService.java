package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.history.port.MarketCandleRepository;
import com.aiprovider.quant.market.history.port.SyncUnitOfWork;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一 K 线写入管线。REST 历史同步和 Binance 官方 ZIP 导入共同复用此服务。
 * 不使用 @Service 注解，由 AIProvider-back 通过 @Bean 创建。
 *
 * <p>该服务只负责“单批 K 线”的校验与写入，不关心数据来源（REST 分页 / 归档 CSV）、
 * 不关心任务进度、不关心缺口校验。调用方负责分页、游标推进和任务状态机。</p>
 *
 * <p>每批处理流程：</p>
 * <ol>
 *   <li>校验 symbol / interval / 时间范围 / 升序对齐 / 闭合（可选）/ OHLC / volume</li>
 *   <li>在事务内查询已有 K 线，相同数据计为 existing，冲突数据抛出 CANDLE_DATA_CONFLICT</li>
 *   <li>批量插入新 K 线并校验影响行数</li>
 *   <li>返回 BatchResult 统计</li>
 * </ol>
 *
 * <p>闭合校验通过 {@code serverTimeMs} 控制：传入 0 表示跳过（用于归档数据，
 * 历史数据天然闭合）；传入正数表示按服务器时间校验 closeTime &lt; serverTimeMs。</p>
 */
public class MarketCandleIngestService {

    // ---- 错误码 ----

    public static final String ERR_DUPLICATE_OPEN_TIME = "DUPLICATE_OPEN_TIME";
    public static final String ERR_OUT_OF_RANGE = "OUT_OF_RANGE";
    public static final String ERR_NOT_CLOSED = "NOT_CLOSED_KLINE";
    public static final String ERR_OHLC_INVALID = "OHLC_INVALID";
    public static final String ERR_VOLUME_NEGATIVE = "VOLUME_NEGATIVE";
    public static final String ERR_NOT_ALIGNED = "OPEN_TIME_NOT_ALIGNED";
    public static final String ERR_SYMBOL_MISMATCH = "SYMBOL_MISMATCH";
    public static final String ERR_INSERT_COUNT = "INSERT_COUNT_MISMATCH";
    public static final String ERR_CANDLE_CONFLICT = "CANDLE_DATA_CONFLICT";

    private final MarketCandleRepository candleRepository;
    private final SyncUnitOfWork unitOfWork;

    public MarketCandleIngestService(MarketCandleRepository candleRepository, SyncUnitOfWork unitOfWork) {
        this.candleRepository = candleRepository;
        this.unitOfWork = unitOfWork;
    }

    /**
     * 写入单批 K 线。
     *
     * <p>先执行批次校验（事务外，纯内存），再在 {@link SyncUnitOfWork} 事务内完成
     * “查询已有 → 冲突判定 → 批量插入 → 行数校验”。任意一步失败抛出
     * {@link IngestException}，事务回滚，已提交的批次不受影响。</p>
     *
     * @param datasetId     目标数据集 ID
     * @param candles       本批 K 线（REST 拉取或 CSV 解析后的统一模型）
     * @param interval      K 线周期
     * @param symbol        期望 symbol，用于逐根校验
     * @param cursor        当前分页游标（本批 openTime 下界，用于范围校验）
     * @param normalizedEnd 归一化结束边界（openTime 上界，开区间）
     * @param serverTimeMs  服务器时间，用于闭合校验；0 表示跳过（归档数据）
     * @param lastOpenTime  上一批最后一根 openTime（ms）；-1 表示首批
     * @param source        数据来源标记，写入 HistoricalCandle.source
     * @return 本批写入统计
     * @throws IngestException 校验或写入失败时抛出，errorCode 标识具体原因
     */
    public BatchResult ingestBatch(long datasetId, List<MarketCandle> candles, KlineInterval interval,
                                   String symbol, long cursor, long normalizedEnd, long serverTimeMs,
                                   long lastOpenTime, String source) {
        // 1. 批次校验（事务外）
        validateBatch(candles, cursor, normalizedEnd, serverTimeMs, interval, symbol, lastOpenTime);

        // 2. 事务内：查询已有 → 冲突判定 → 批量插入 → 行数校验
        return unitOfWork.execute(() -> {
            List<HistoricalCandle> toInsert = new ArrayList<>();
            int batchExisting = 0;
            int batchConflict = 0;

            // 查询已存在的 K 线
            List<Long> openTimes = new ArrayList<>();
            for (MarketCandle c : candles) {
                openTimes.add(c.getOpenTime().toEpochMilli());
            }
            List<HistoricalCandle> existing = candleRepository.findByOpenTimes(datasetId, openTimes);
            Map<Long, HistoricalCandle> existingMap = new HashMap<>();
            for (HistoricalCandle hc : existing) {
                existingMap.put(hc.getOpenTime().toEpochMilli(), hc);
            }

            for (MarketCandle c : candles) {
                long openTimeMs = c.getOpenTime().toEpochMilli();
                HistoricalCandle existingCandle = existingMap.get(openTimeMs);
                if (existingCandle != null) {
                    if (candleMatches(c, existingCandle)) {
                        // 数据一致，计为已存在
                        batchExisting++;
                    } else {
                        // 数据冲突，记数后立即抛出，事务回滚
                        batchConflict++;
                        throw new IngestException(ERR_CANDLE_CONFLICT,
                                "K 线数据冲突: symbol=" + symbol + " interval=" + interval.code()
                                        + " openTime=" + Instant.ofEpochMilli(openTimeMs));
                    }
                } else {
                    toInsert.add(toHistoricalCandle(c, datasetId, source));
                }
            }

            // 批量插入新 K 线
            if (!toInsert.isEmpty()) {
                int inserted = candleRepository.insertBatch(toInsert);
                if (inserted != toInsert.size()) {
                    throw new IngestException(ERR_INSERT_COUNT,
                            "批量插入影响行数不匹配: expected=" + toInsert.size() + " actual=" + inserted);
                }
            }

            return new BatchResult(toInsert.size(), batchExisting, batchConflict);
        });
    }

    // ---- 批次校验 ----

    /**
     * 批次校验，逻辑与 MarketHistorySyncService.validateBatch 一致。
     *
     * <p>唯一差异：闭合校验为可选。{@code serverTimeMs == 0} 时跳过闭合校验，
     * 用于归档数据（历史 K 线天然闭合，且没有实时服务器时间可用）。</p>
     */
    private void validateBatch(List<MarketCandle> fetched, long cursor, long normalizedEnd,
                               long serverTimeMs, KlineInterval interval, String symbol, long lastOpenTime) {
        if (fetched.isEmpty()) return;

        long durationMs = interval.durationMillis();
        long prevOpenTime = lastOpenTime;

        for (int i = 0; i < fetched.size(); i++) {
            MarketCandle c = fetched.get(i);
            long openTimeMs = c.getOpenTime().toEpochMilli();
            long closeTimeMs = c.getCloseTime().toEpochMilli();

            // symbol 校验
            if (!symbol.equals(c.getSymbol())) {
                throw new IngestException(ERR_SYMBOL_MISMATCH,
                        "symbol 不匹配: expected=" + symbol + " actual=" + c.getSymbol());
            }

            // 范围校验
            if (openTimeMs < cursor) {
                throw new IngestException(ERR_OUT_OF_RANGE,
                        "K 线 openTime 早于游标: openTime=" + openTimeMs + " cursor=" + cursor);
            }
            if (openTimeMs >= normalizedEnd) {
                throw new IngestException(ERR_OUT_OF_RANGE,
                        "K 线 openTime 超出结束边界: openTime=" + openTimeMs + " end=" + normalizedEnd);
            }

            // 对齐校验
            if (openTimeMs % durationMs != 0) {
                throw new IngestException(ERR_NOT_ALIGNED,
                        "K 线 openTime 未对齐到周期: openTime=" + openTimeMs + " duration=" + durationMs);
            }

            // 升序校验
            if (i > 0 && openTimeMs <= prevOpenTime) {
                throw new IngestException(ERR_DUPLICATE_OPEN_TIME,
                        "同页 K 线 openTime 非升序或重复: prev=" + prevOpenTime + " current=" + openTimeMs);
            }
            if (prevOpenTime >= 0 && openTimeMs <= prevOpenTime) {
                throw new IngestException(ERR_DUPLICATE_OPEN_TIME,
                        "K 线 openTime 未超过前一根: prev=" + prevOpenTime + " current=" + openTimeMs);
            }

            // 闭合校验（serverTimeMs == 0 表示跳过，用于归档数据）
            if (serverTimeMs > 0 && closeTimeMs >= serverTimeMs) {
                throw new IngestException(ERR_NOT_CLOSED,
                        "K 线未闭合: closeTime=" + closeTimeMs + " serverTime=" + serverTimeMs);
            }

            // OHLC 合法性
            BigDecimal high = c.getHigh();
            BigDecimal low = c.getLow();
            BigDecimal open = c.getOpen();
            BigDecimal close = c.getClose();
            if (high.compareTo(open) < 0 || high.compareTo(close) < 0) {
                throw new IngestException(ERR_OHLC_INVALID,
                        "high 小于 open 或 close: open=" + open + " high=" + high + " close=" + close);
            }
            if (low.compareTo(open) > 0 || low.compareTo(close) > 0) {
                throw new IngestException(ERR_OHLC_INVALID,
                        "low 大于 open 或 close: open=" + open + " low=" + low + " close=" + close);
            }

            // volume 非负
            if (c.getVolume().signum() < 0) {
                throw new IngestException(ERR_VOLUME_NEGATIVE,
                        "volume 为负数: " + c.getVolume());
            }

            prevOpenTime = openTimeMs;
        }
    }

    // ---- 冲突比较 ----

    /**
     * 比较 REST/CSV 解析得到的 K 线与数据库已存 K 线是否一致。
     * 与 MarketHistorySyncService.candleMatches 逻辑一致。
     */
    private boolean candleMatches(MarketCandle fetched, HistoricalCandle existing) {
        return eq(fetched.getOpen(), existing.getOpenPrice())
                && eq(fetched.getHigh(), existing.getHighPrice())
                && eq(fetched.getLow(), existing.getLowPrice())
                && eq(fetched.getClose(), existing.getClosePrice())
                && eq(fetched.getVolume(), existing.getVolume())
                && eq(fetched.getQuoteVolume(), existing.getQuoteVolume())
                && fetched.getTradeCount() == existing.getTradeCount()
                && eq(fetched.getTakerBuyBaseVolume(), existing.getTakerBuyBaseVolume())
                && eq(fetched.getTakerBuyQuoteVolume(), existing.getTakerBuyQuoteVolume());
    }

    private static boolean eq(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    // ---- 转换 ----

    /**
     * 将统一 K 线模型转换为数据库实体。
     * 与 MarketHistorySyncService.toHistoricalCandle 逻辑一致，差异在于 source 由参数传入。
     */
    private HistoricalCandle toHistoricalCandle(MarketCandle candle, long datasetId, String source) {
        HistoricalCandle hc = new HistoricalCandle();
        hc.setDatasetId(datasetId);
        hc.setProvider(candle.getProvider());
        hc.setMarketType(candle.getMarketType());
        hc.setSymbol(candle.getSymbol());
        hc.setInterval(candle.getInterval());
        hc.setOpenTime(candle.getOpenTime());
        hc.setCloseTime(candle.getCloseTime());
        hc.setOpenPrice(candle.getOpen());
        hc.setHighPrice(candle.getHigh());
        hc.setLowPrice(candle.getLow());
        hc.setClosePrice(candle.getClose());
        hc.setVolume(candle.getVolume());
        hc.setQuoteVolume(candle.getQuoteVolume());
        hc.setTradeCount(candle.getTradeCount());
        hc.setTakerBuyBaseVolume(candle.getTakerBuyBaseVolume());
        hc.setTakerBuyQuoteVolume(candle.getTakerBuyQuoteVolume());
        hc.setSource(source);
        return hc;
    }

    // ---- 内部类型 ----

    /**
     * 单批写入统计。
     */
    public static class BatchResult {
        public final int inserted;
        public final int existing;
        public final int conflict;

        public BatchResult(int inserted, int existing, int conflict) {
            this.inserted = inserted;
            this.existing = existing;
            this.conflict = conflict;
        }
    }

    /**
     * 写入管线异常，携带错误码供调用方映射到任务失败原因。
     */
    public static class IngestException extends RuntimeException {
        public final String errorCode;

        public IngestException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
    }
}
