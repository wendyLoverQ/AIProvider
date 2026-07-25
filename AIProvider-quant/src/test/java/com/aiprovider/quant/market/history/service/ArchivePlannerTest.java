package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.market.history.model.ArchiveImportMode;
import com.aiprovider.quant.market.history.model.ArchiveImportPlan;
import com.aiprovider.quant.market.history.model.ArchiveKlineFile;
import com.aiprovider.quant.market.model.KlineInterval;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ArchivePlanner} 单元测试。
 *
 * 验证 Binance 官方归档包规划规则：
 * <ul>
 *   <li>月包在次月首个星期一发布（已发布 → 月包，未发布 → 日包）</li>
 *   <li>日包 T+1 发布（昨天数据今天可下载）</li>
 *   <li>今天及以后的数据尚无归档 → REST 尾部</li>
 * </ul>
 *
 * 使用固定 UTC 日期，避免依赖系统时钟。
 */
class ArchivePlannerTest {

    private static final String SYMBOL = "BTCUSDT";
    private static final KlineInterval INTERVAL = KlineInterval.M1;

    // 固定 UTC 00:00 时间戳（毫秒）
    private static final long JAN_01 = 1735689600000L;
    private static final long FEB_01 = 1738368000000L;
    private static final long FEB_02 = 1738454400000L;
    private static final long FEB_03 = 1738540800000L; // 2025-02-03 = 次月首个星期一（1月的月包发布日）
    private static final long FEB_04 = 1738627200000L;
    private static final long FEB_05 = 1738713600000L;
    private static final long MAR_01 = 1740787200000L;

    private final ArchivePlanner planner = new ArchivePlanner();

    @Test
    void monthlyPublishedUsesMonthlyFile() {
        // 1 月完整范围，serverTime=Feb 4（>Feb 3 发布日）→ 月包已发布
        ArchiveImportPlan plan = planner.planArchiveImport(SYMBOL, INTERVAL, JAN_01, FEB_01, FEB_04);

        assertThat(plan.getMonthlyFileCount()).isEqualTo(1);
        assertThat(plan.getDailyFileCount()).isZero();
        assertThat(plan.isHasRestTail()).isFalse();
        assertThat(plan.totalFileCount()).isEqualTo(1);

        ArchiveKlineFile file = plan.getFiles().get(0);
        assertThat(file.getZipFileName()).isEqualTo("BTCUSDT-1m-2025-01.zip");
        assertThat(file.getRelativePath()).startsWith("data/futures/um/monthly/klines/");
        assertThat(file.getRangeStart()).isEqualTo(JAN_01);
        assertThat(file.getRangeEndExclusive()).isEqualTo(FEB_01);
    }

    @Test
    void monthlyNotPublishedFallsBackToDailyFiles() {
        // 1 月完整范围，serverTime=Feb 2（<Feb 3 发布日）→ 月包未发布，使用日包
        ArchiveImportPlan plan = planner.planArchiveImport(SYMBOL, INTERVAL, JAN_01, FEB_01, FEB_02);

        assertThat(plan.getMonthlyFileCount()).isZero();
        assertThat(plan.getDailyFileCount()).isEqualTo(31); // 1 月有 31 天
        assertThat(plan.isHasRestTail()).isFalse();
        assertThat(plan.totalFileCount()).isEqualTo(31);

        ArchiveKlineFile first = plan.getFiles().get(0);
        assertThat(first.getZipFileName()).isEqualTo("BTCUSDT-1m-2025-01-01.zip");
        assertThat(first.getRelativePath()).startsWith("data/futures/um/daily/klines/");
    }

    @Test
    void rangeExtendingToTodayHasRestTail() {
        // 范围跨到今天，serverTime=Feb 4 → 归档截止 Feb 4，REST 尾部 [Feb 4, Mar 1)
        ArchiveImportPlan plan = planner.planArchiveImport(SYMBOL, INTERVAL, JAN_01, MAR_01, FEB_04);

        assertThat(plan.getMonthlyFileCount()).isEqualTo(1); // 1 月月包（Feb 4 >= Feb 3）
        assertThat(plan.getDailyFileCount()).isEqualTo(3); // Feb 1-3 日包
        assertThat(plan.isHasRestTail()).isTrue();
        assertThat(plan.getRestTailStartInclusive()).isEqualTo(FEB_04);
        assertThat(plan.getRestTailEndExclusive()).isEqualTo(MAR_01);
    }

    @Test
    void rangeFullyInPastHasNoRestTail() {
        // 范围完全在过去，serverTime=Mar 1 → 无 REST 尾部
        ArchiveImportPlan plan = planner.planArchiveImport(SYMBOL, INTERVAL, JAN_01, FEB_01, MAR_01);

        assertThat(plan.isHasRestTail()).isFalse();
        assertThat(plan.getRestTailStartInclusive()).isNull();
        assertThat(plan.getRestTailEndExclusive()).isNull();
    }

    @Test
    void entireRangeTodayProducesOnlyRestTail() {
        // 整个范围在今天，归档无法覆盖 → 无归档文件，全部 REST 尾部
        long noon = FEB_04 + 43_200_000L; // Feb 4 12:00 UTC
        ArchiveImportPlan plan = planner.planArchiveImport(SYMBOL, INTERVAL, FEB_04, FEB_05, noon);

        assertThat(plan.totalFileCount()).isZero();
        assertThat(plan.isHasRestTail()).isTrue();
        assertThat(plan.getRestTailStartInclusive()).isEqualTo(FEB_04);
        assertThat(plan.getRestTailEndExclusive()).isEqualTo(FEB_05);
    }

    @Test
    void planModeIsAlwaysAuto() {
        ArchiveImportPlan plan = planner.planArchiveImport(SYMBOL, INTERVAL, JAN_01, FEB_01, FEB_04);
        assertThat(plan.getMode()).isEqualTo(ArchiveImportMode.AUTO);
    }

    @Test
    void monthlyModeOnlyProducesMonthlyFiles() {
        ArchiveImportPlan plan = planner.plan(SYMBOL, INTERVAL, JAN_01, FEB_01, FEB_04,
                ArchiveImportMode.ARCHIVE_MONTHLY);
        assertThat(plan.getMode()).isEqualTo(ArchiveImportMode.ARCHIVE_MONTHLY);
        assertThat(plan.getFiles()).allMatch(file -> file.getSourceMode() == ArchiveImportMode.ARCHIVE_MONTHLY);
        assertThat(plan.getDailyFileCount()).isZero();
        assertThat(plan.isHasRestTail()).isFalse();
    }

    @Test
    void monthlyModeRejectsPartialMonth() {
        assertThatThrownBy(() -> planner.plan(SYMBOL, INTERVAL, JAN_01 + 60_000, FEB_01, FEB_04,
                ArchiveImportMode.ARCHIVE_MONTHLY))
                .isInstanceOf(ArchiveDataException.class)
                .hasMessageContaining("完整 UTC 月");
    }

    @Test
    void dailyModeOnlyProducesDailyFiles() {
        ArchiveImportPlan plan = planner.plan(SYMBOL, INTERVAL, FEB_01, FEB_04, FEB_05,
                ArchiveImportMode.ARCHIVE_DAILY);
        assertThat(plan.getMode()).isEqualTo(ArchiveImportMode.ARCHIVE_DAILY);
        assertThat(plan.getFiles()).allMatch(file -> file.getSourceMode() == ArchiveImportMode.ARCHIVE_DAILY);
        assertThat(plan.getMonthlyFileCount()).isZero();
        assertThat(plan.isHasRestTail()).isFalse();
    }

    @Test
    void dailyModeRejectsCurrentDay() {
        assertThatThrownBy(() -> planner.plan(SYMBOL, INTERVAL, FEB_04, FEB_05, FEB_04 + 12 * 60 * 60 * 1000L,
                ArchiveImportMode.ARCHIVE_DAILY))
                .isInstanceOf(ArchiveDataException.class)
                .hasMessageContaining("当前 UTC 日期");
    }

    @Test
    void strictValidationUsesOriginalRequestBoundary() {
        assertThatThrownBy(() -> planner.validateStrictRequest(
                Instant.parse("2025-01-01T00:01:00Z"),
                Instant.parse("2025-02-01T00:00:00Z"),
                FEB_04, ArchiveImportMode.ARCHIVE_MONTHLY))
                .isInstanceOf(ArchiveDataException.class)
                .hasMessageContaining("原始请求");
    }
}
