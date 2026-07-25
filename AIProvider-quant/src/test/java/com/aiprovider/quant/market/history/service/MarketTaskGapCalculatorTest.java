package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.market.history.model.MarketDataGap;
import com.aiprovider.quant.market.model.KlineInterval;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarketTaskGapCalculator} 单元测试。
 *
 * 验证任务请求范围内的缺口统计算法：
 * 前部缺口、内部缺口（与请求范围取交集）、后部缺口，以及无数据时的全范围缺口。
 * REST 同步和归档导入共用此组件，保证缺口统计算法唯一。
 */
class MarketTaskGapCalculatorTest {

    private static final long M1 = KlineInterval.M1.durationMillis(); // 60000ms

    private final MarketTaskGapCalculator calculator = new MarketTaskGapCalculator();

    @Test
    void noDataReturnsFullRangeAsGap() {
        long start = 0;
        long end = 10 * M1; // 10 根 K 线的范围
        long gaps = calculator.calculateTaskGapCount(KlineInterval.M1, start, end, null, null, Collections.emptyList());
        assertThat(gaps).isEqualTo(10);
    }

    @Test
    void contiguousDataCoveringFullRangeHasZeroGaps() {
        long start = 0;
        long end = 10 * M1;
        // 数据覆盖 [0, 9*M1]，共 10 根 K 线，无缺口
        long gaps = calculator.calculateTaskGapCount(KlineInterval.M1, start, end, 0L, 9 * M1, Collections.emptyList());
        assertThat(gaps).isZero();
    }

    @Test
    void frontGapOnly() {
        long start = 0;
        long end = 10 * M1;
        // 数据从 2*M1 开始，前部缺 2 根
        long gaps = calculator.calculateTaskGapCount(KlineInterval.M1, start, end, 2 * M1, 9 * M1, Collections.emptyList());
        assertThat(gaps).isEqualTo(2);
    }

    @Test
    void backGapOnly() {
        long start = 0;
        long end = 10 * M1;
        // 数据到 6*M1 结束，后部缺 3 根（7*M1, 8*M1, 9*M1）
        long gaps = calculator.calculateTaskGapCount(KlineInterval.M1, start, end, 0L, 6 * M1, Collections.emptyList());
        assertThat(gaps).isEqualTo(3);
    }

    @Test
    void internalGapOnly() {
        long start = 0;
        long end = 10 * M1;
        // 数据 [0, 9*M1]，中间缺 [2*M1, 3*M1) 共 1 根
        MarketDataGap gap = new MarketDataGap();
        gap.setStartOpenTime(Instant.ofEpochMilli(2 * M1));
        gap.setEndOpenTimeExclusive(Instant.ofEpochMilli(3 * M1));
        long gaps = calculator.calculateTaskGapCount(KlineInterval.M1, start, end, 0L, 9 * M1, List.of(gap));
        assertThat(gaps).isEqualTo(1);
    }

    @Test
    void frontInternalAndBackGapsCombined() {
        long start = 0;
        long end = 10 * M1;
        // 数据 [2*M1, 6*M1]
        // 前部缺 2 根（0, M1）
        // 内部缺 [4*M1, 5*M1) 共 1 根
        // 后部缺 3 根（7*M1, 8*M1, 9*M1）
        MarketDataGap gap = new MarketDataGap();
        gap.setStartOpenTime(Instant.ofEpochMilli(4 * M1));
        gap.setEndOpenTimeExclusive(Instant.ofEpochMilli(5 * M1));
        long gaps = calculator.calculateTaskGapCount(KlineInterval.M1, start, end, 2 * M1, 6 * M1, List.of(gap));
        assertThat(gaps).isEqualTo(6);
    }

    @Test
    void internalGapOutsideRequestRangeIsExcluded() {
        long start = 0;
        long end = 5 * M1;
        // 数据 [0, 4*M1]，但内部 gap 完全在请求范围外 [6*M1, 7*M1)
        MarketDataGap gap = new MarketDataGap();
        gap.setStartOpenTime(Instant.ofEpochMilli(6 * M1));
        gap.setEndOpenTimeExclusive(Instant.ofEpochMilli(7 * M1));
        long gaps = calculator.calculateTaskGapCount(KlineInterval.M1, start, end, 0L, 4 * M1, List.of(gap));
        assertThat(gaps).isZero();
    }
}
