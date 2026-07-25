package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.market.history.model.MarketDataGap;
import com.aiprovider.quant.market.model.KlineInterval;

import java.util.List;

/**
 * 任务级缺口计算器。
 *
 * REST 同步和归档导入共用此组件，保证缺口统计算法唯一。
 *
 * 缺口由三部分组成：
 * <ol>
 *   <li>前部缺口：normalizedStart 到第一根 K 线 openTime 之间</li>
 *   <li>内部缺口：数据集内已检测到的 gap 区段，与任务请求范围取交集</li>
 *   <li>后部缺口：最后一根 K 线 closeTime 到 normalizedEnd 之间</li>
 * </ol>
 */
public final class MarketTaskGapCalculator {

    /**
     * 计算任务请求范围内的缺口 K 线总根数。
     *
     * @param interval        K 线周期
     * @param normalizedStart 任务归一化起始时间（ms）
     * @param normalizedEnd   任务归一化结束时间（ms）
     * @param earliestMs      数据集最早 K 线 openTime（ms），null 表示无数据
     * @param latestMs        数据集最晚 K 线 openTime（ms），null 表示无数据
     * @param internalGaps    校验阶段检测到的内部缺口列表
     * @return 任务请求范围内的缺口 K 线总根数
     */
    public long calculateTaskGapCount(KlineInterval interval,
                                       long normalizedStart, long normalizedEnd,
                                       Long earliestMs, Long latestMs,
                                       List<MarketDataGap> internalGaps) {
        long durationMs = interval.durationMillis();

        if (earliestMs == null || latestMs == null) {
            return (normalizedEnd - normalizedStart) / durationMs;
        }

        long gaps = 0;

        // 前部缺口
        long frontEnd = Math.min(earliestMs, normalizedEnd);
        if (frontEnd > normalizedStart) {
            gaps += (frontEnd - normalizedStart) / durationMs;
        }

        // 内部缺口（与请求范围取交集）
        for (MarketDataGap gap : internalGaps) {
            long gapStart = gap.getStartOpenTime().toEpochMilli();
            long gapEndExclusive = gap.getEndOpenTimeExclusive().toEpochMilli();
            long intersectStart = Math.max(gapStart, normalizedStart);
            long intersectEnd = Math.min(gapEndExclusive, normalizedEnd);
            if (intersectEnd > intersectStart) {
                gaps += (intersectEnd - intersectStart) / durationMs;
            }
        }

        // 后部缺口
        long backStart = Math.max(latestMs + durationMs, normalizedStart);
        if (normalizedEnd > backStart) {
            gaps += (normalizedEnd - backStart) / durationMs;
        }

        return gaps;
    }
}
