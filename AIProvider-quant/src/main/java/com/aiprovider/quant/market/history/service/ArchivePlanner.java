package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.market.history.model.ArchiveImportMode;
import com.aiprovider.quant.market.history.model.ArchiveImportPlan;
import com.aiprovider.quant.market.history.model.ArchiveKlineFile;
import com.aiprovider.quant.market.model.KlineInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Binance 官方历史数据包导入计划规划器。
 *
 * <p>纯 Java 逻辑类，不使用 {@code @Service} 注解，不依赖任何 Spring API，
 * 由 {@code AIProvider-back} 通过 {@code @Bean} 创建。</p>
 *
 * <p>给定一个已对齐到 K 线周期的时间范围 {@code [normalizedStartMs, normalizedEndMs)}，
 * 规划器根据 Binance 官方归档数据包的发布规则计算需要下载哪些 ZIP 包：</p>
 * <ul>
 *   <li>整月覆盖的范围使用月包；</li>
 *   <li>月首或月末部分覆盖的范围使用日包；</li>
 *   <li>今天及以后的数据尚无归档，由调用方通过 REST 接口修补尾部。</li>
 * </ul>
 *
 * <p>路径规则来源：binance/binance-public-data (MIT License)</p>
 * <ul>
 *   <li>月包：{@code data/futures/um/monthly/klines/{SYMBOL}/{INTERVAL}/{SYMBOL}-{INTERVAL}-{YYYY-MM}.zip}</li>
 *   <li>日包：{@code data/futures/um/daily/klines/{SYMBOL}/{INTERVAL}/{SYMBOL}-{INTERVAL}-{YYYY-MM-DD}.zip}</li>
 *   <li>校验文件：在 zip 文件名后加 {@code .CHECKSUM}</li>
 * </ul>
 *
 * <p>本规划器只生成相对路径，基础下载 URL 由 {@code AIProvider-back} 的 adapter 负责。</p>
 *
 * <p>Binance 归档发布规则：</p>
 * <ul>
 *   <li>月包在次月首个星期一发布（月包发布检查阈值 = 该月结束后下一月第一个星期一 00:00 UTC）；</li>
 *   <li>日包 T+1 发布（昨天数据今天可下载）；</li>
 *   <li>今天的数据尚无归档，需要 REST 尾部修补。</li>
 * </ul>
 */
public class ArchivePlanner {

    private static final Logger log = LoggerFactory.getLogger(ArchivePlanner.class);

    public ArchivePlanner() {
    }

    /**
     * 规划一次归档导入任务。
     *
     * <p>输入的时间范围已被调用方对齐到 K 线周期边界（normalized），本方法只负责
     * 拆分月包、日包和判定 REST 尾部，不做周期对齐。</p>
     *
     * @param symbol            交易对，例如 "BTCUSDT"
     * @param interval          K 线周期，仅用到 {@link KlineInterval#code()} 拼接路径
     * @param normalizedStartMs 已对齐的起始 openTime，epoch 毫秒
     * @param normalizedEndMs   已对齐的独占结束 openTime，epoch 毫秒
     * @param serverTimeMs      服务器当前时间，epoch 毫秒，用于推算归档截止
     * @return 归档导入计划，模式固定为 {@link ArchiveImportMode#AUTO}
     */
    public ArchiveImportPlan planArchiveImport(String symbol,
                                               KlineInterval interval,
                                               long normalizedStartMs,
                                               long normalizedEndMs,
                                               long serverTimeMs) {
        return plan(symbol, interval, normalizedStartMs, normalizedEndMs, serverTimeMs,
                ArchiveImportMode.AUTO);
    }

    /** Plans strictly according to the requested source mode. */
    public ArchiveImportPlan plan(String symbol, KlineInterval interval,
                                  long normalizedStartMs, long normalizedEndMs,
                                  long serverTimeMs, ArchiveImportMode mode) {
        if (mode == null || mode == ArchiveImportMode.REST_GAP_REPAIR) {
            throw new ArchiveDataException("INVALID_ARCHIVE_MODE", "归档规划不支持 REST_GAP_REPAIR");
        }
        if (normalizedEndMs <= normalizedStartMs) {
            throw new ArchiveDataException("INVALID_TIME_RANGE", "归档范围必须为正数");
        }
        if (mode == ArchiveImportMode.AUTO) {
            return planAuto(symbol, interval, normalizedStartMs, normalizedEndMs, serverTimeMs);
        }
        if (mode == ArchiveImportMode.ARCHIVE_MONTHLY) {
            return planMonthlyOnly(symbol, interval, normalizedStartMs, normalizedEndMs, serverTimeMs);
        }
        return planDailyOnly(symbol, interval, normalizedStartMs, normalizedEndMs, serverTimeMs);
    }

    private ArchiveImportPlan planAuto(String symbol,
                                       KlineInterval interval,
                                       long normalizedStartMs,
                                       long normalizedEndMs,
                                       long serverTimeMs) {
        // 1. 归档截止时间 archiveCutoffMs = 今天 00:00 UTC（= 昨天 00:00 UTC + 1 天）。
        //    日包 T+1 发布：昨天数据今天可下载；今天数据尚无归档。归档文件只覆盖到昨天结束。
        LocalDate todayUtc = Instant.ofEpochMilli(serverTimeMs)
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        LocalDate yesterdayUtc = todayUtc.minusDays(1);
        long archiveCutoffMs = todayUtc
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();

        // 2. 有效结束 = min(请求结束, 归档截止)
        long effectiveEndMs = Math.min(normalizedEndMs, archiveCutoffMs);

        List<ArchiveKlineFile> files = new ArrayList<>();
        int monthlyFileCount = 0;
        int dailyFileCount = 0;
        boolean hasRestTail;
        long restTailStartInclusive = 0;
        long restTailEndExclusive = 0;

        if (effectiveEndMs <= normalizedStartMs) {
            // 请求范围全在今天或之后，无归档可用，整段交由 REST 尾部修补。
            hasRestTail = true;
            restTailStartInclusive = normalizedStartMs;
            restTailEndExclusive = normalizedEndMs;
        } else {
            // 3. 遍历从 normalizedStartMs 所在月到 effectiveEndMs 所在月的每个月。
            long cursorMonthStart = monthStartMs(normalizedStartMs);
            while (cursorMonthStart < effectiveEndMs) {
                long nextMonthStart = nextMonthStartMs(cursorMonthStart);

                if (cursorMonthStart >= normalizedStartMs && nextMonthStart <= effectiveEndMs) {
                    // 整月都在有效范围内 → 检查月包是否已发布
                    // 月包在次月首个星期一发布
                    long monthlyAvailableMs = firstMondayOfNextMonthMs(cursorMonthStart);
                    if (serverTimeMs >= monthlyAvailableMs) {
                        // 月包已发布 → 使用月包
                        String ym = formatYearMonth(cursorMonthStart);
                        String zipFileName = symbol + "-" + interval.code() + "-" + ym + ".zip";
                        String checksumFileName = zipFileName + ".CHECKSUM";
                        String relativePath = "data/futures/um/monthly/klines/"
                                + symbol + "/" + interval.code() + "/" + zipFileName;
                        files.add(new ArchiveKlineFile(
                                ArchiveImportMode.ARCHIVE_MONTHLY,
                                relativePath,
                                zipFileName,
                                checksumFileName,
                                cursorMonthStart,
                                nextMonthStart));
                        monthlyFileCount++;
                    } else {
                        // 月包尚未发布 → 该月使用日包
                        dailyFileCount += addDailyFiles(files, symbol, interval,
                                cursorMonthStart, nextMonthStart,
                                Math.max(cursorMonthStart, normalizedStartMs),
                                Math.min(nextMonthStart, effectiveEndMs));
                    }
                } else {
                    // 该月部分覆盖 → 遍历该月内每一天，与 [normalizedStartMs, effectiveEndMs) 取交集
                    dailyFileCount += addDailyFiles(files, symbol, interval,
                            cursorMonthStart, nextMonthStart,
                            Math.max(cursorMonthStart, normalizedStartMs),
                            Math.min(nextMonthStart, effectiveEndMs));
                }

                cursorMonthStart = nextMonthStart;
            }

            // 4. 是否需要 REST 尾部修补：请求结束超过归档截止即需要
            hasRestTail = normalizedEndMs > effectiveEndMs;
            if (hasRestTail) {
                restTailStartInclusive = effectiveEndMs;
                restTailEndExclusive = normalizedEndMs;
            }
        }

        // 5/6. 汇总返回
        Long restTailStart = hasRestTail ? restTailStartInclusive : null;
        Long restTailEnd = hasRestTail ? restTailEndExclusive : null;

        ArchiveImportPlan plan = new ArchiveImportPlan(
                files,
                ArchiveImportMode.AUTO,
                normalizedStartMs,
                normalizedEndMs,
                monthlyFileCount,
                dailyFileCount,
                hasRestTail,
                restTailStart,
                restTailEnd);

        log.info("operation=archive-plan symbol={} interval={} start={} end={} serverTime={} archiveCutoff={} fileCount={} monthly={} daily={} hasRestTail={} restTailStart={} restTailEnd={}",
                symbol, interval.code(), normalizedStartMs, normalizedEndMs, serverTimeMs,
                archiveCutoffMs, files.size(), monthlyFileCount, dailyFileCount, hasRestTail,
                restTailStart, restTailEnd);

        return plan;
    }

    private ArchiveImportPlan planMonthlyOnly(String symbol, KlineInterval interval,
                                               long startMs, long endMs, long serverTimeMs) {
        if (startMs != monthStartMs(startMs) || endMs != monthStartMs(endMs)) {
            throw new ArchiveDataException("ARCHIVE_MONTHLY_REQUIRES_FULL_MONTH", "ARCHIVE_MONTHLY 要求完整 UTC 月范围");
        }
        List<ArchiveKlineFile> files = new ArrayList<>();
        for (long cursor = startMs; cursor < endMs; cursor = nextMonthStartMs(cursor)) {
            if (serverTimeMs < firstMondayOfNextMonthMs(cursor)) {
                throw new ArchiveDataException("ARCHIVE_MONTHLY_NOT_AVAILABLE", "月包尚未发布: " + formatYearMonth(cursor));
            }
            String name = symbol + "-" + interval.code() + "-" + formatYearMonth(cursor) + ".zip";
            files.add(new ArchiveKlineFile(ArchiveImportMode.ARCHIVE_MONTHLY,
                    "data/futures/um/monthly/klines/" + symbol + "/" + interval.code() + "/" + name,
                    name, name + ".CHECKSUM", cursor, nextMonthStartMs(cursor)));
        }
        return new ArchiveImportPlan(files, ArchiveImportMode.ARCHIVE_MONTHLY, startMs, endMs,
                files.size(), 0, false, null, null);
    }

    private ArchiveImportPlan planDailyOnly(String symbol, KlineInterval interval,
                                             long startMs, long endMs, long serverTimeMs) {
        if (startMs != dayStartMs(startMs) || endMs != dayStartMs(endMs)) {
            throw new ArchiveDataException("ARCHIVE_DAILY_REQUIRES_FULL_DAY", "ARCHIVE_DAILY 要求完整 UTC 自然日范围");
        }
        LocalDate today = Instant.ofEpochMilli(serverTimeMs).atZone(ZoneOffset.UTC).toLocalDate();
        long todayMs = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        if (endMs > todayMs) {
            throw new ArchiveDataException("ARCHIVE_DAILY_NOT_AVAILABLE", "当前 UTC 日期及未来日期没有日包");
        }
        List<ArchiveKlineFile> files = new ArrayList<>();
        for (long cursor = startMs; cursor < endMs; cursor = nextDayStartMs(cursor)) {
            String name = symbol + "-" + interval.code() + "-" + formatYearMonthDay(cursor) + ".zip";
            files.add(new ArchiveKlineFile(ArchiveImportMode.ARCHIVE_DAILY,
                    "data/futures/um/daily/klines/" + symbol + "/" + interval.code() + "/" + name,
                    name, name + ".CHECKSUM", cursor, nextDayStartMs(cursor)));
        }
        return new ArchiveImportPlan(files, ArchiveImportMode.ARCHIVE_DAILY, startMs, endMs,
                0, files.size(), false, null, null);
    }

    /**
     * 为指定月内的每一天创建日包（与请求范围取交集）。
     *
     * @param files        目标文件列表
     * @param symbol       合约符号
     * @param interval     K 线周期
     * @param monthStart   该月起始 epoch 毫秒
     * @param monthEnd     该月结束 epoch 毫秒（独占）
     * @param rangeStart   请求范围起始（包含）
     * @param rangeEnd     请求范围结束（独占）
     * @return 新增的日包数量
     */
    private int addDailyFiles(List<ArchiveKlineFile> files, String symbol, KlineInterval interval,
                               long monthStart, long monthEnd,
                               long rangeStart, long rangeEnd) {
        int count = 0;
        long dayCursor = monthStart;
        while (dayCursor < monthEnd && dayCursor < rangeEnd) {
            long dayEnd = nextDayStartMs(dayCursor);
            long intersectStart = Math.max(dayCursor, rangeStart);
            long intersectEnd = Math.min(dayEnd, rangeEnd);
            if (intersectStart < intersectEnd) {
                String ymd = formatYearMonthDay(dayCursor);
                String zipFileName = symbol + "-" + interval.code() + "-" + ymd + ".zip";
                String checksumFileName = zipFileName + ".CHECKSUM";
                String relativePath = "data/futures/um/daily/klines/"
                        + symbol + "/" + interval.code() + "/" + zipFileName;
                files.add(new ArchiveKlineFile(
                        ArchiveImportMode.ARCHIVE_DAILY,
                        relativePath,
                        zipFileName,
                        checksumFileName,
                        intersectStart,
                        intersectEnd));
                count++;
            }
            dayCursor = dayEnd;
        }
        return count;
    }

    /**
     * 计算月包发布时间：该月结束后下一月的第一个星期一 00:00 UTC。
     *
     * Binance 官方月包在次月首个星期一发布。只有 serverTime >= 此时间才允许使用月包。
     *
     * @param monthStartMs 该月 1 号 00:00 UTC 的 epoch 毫秒
     * @return 月包发布时间的 epoch 毫秒
     */
    private long firstMondayOfNextMonthMs(long monthStartMs) {
        LocalDate monthStart = Instant.ofEpochMilli(monthStartMs)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);
        // 找到下月第一个星期一
        LocalDate firstMonday = nextMonthStart;
        while (firstMonday.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            firstMonday = firstMonday.plusDays(1);
        }
        return firstMonday
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    // ---- 辅助方法 ----

    /**
     * 将 epoch 毫秒格式化为 {@code YYYY-MM}。
     *
     * @param epochMs epoch 毫秒
     * @return 形如 "2025-01" 的字符串
     */
    private String formatYearMonth(long epochMs) {
        return YearMonth.from(Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC)).toString();
    }

    /**
     * 将 epoch 毫秒格式化为 {@code YYYY-MM-DD}。
     *
     * @param epochMs epoch 毫秒
     * @return 形如 "2025-01-15" 的字符串
     */
    private String formatYearMonthDay(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    /**
     * 返回 epoch 毫秒所在月份的 1 号 00:00 UTC 的 epoch 毫秒。
     *
     * @param epochMs epoch 毫秒
     * @return 当月 1 号 00:00 UTC 的 epoch 毫秒
     */
    private long monthStartMs(long epochMs) {
        return Instant.ofEpochMilli(epochMs)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    /**
     * 返回 epoch 毫秒所在月份的下一月 1 号 00:00 UTC 的 epoch 毫秒。
     *
     * @param epochMs epoch 毫秒
     * @return 下月 1 号 00:00 UTC 的 epoch 毫秒
     */
    private long nextMonthStartMs(long epochMs) {
        return Instant.ofEpochMilli(epochMs)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .withDayOfMonth(1)
                .plusMonths(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    /**
     * 返回 epoch 毫秒所在天的 00:00 UTC 的 epoch 毫秒。
     *
     * @param epochMs epoch 毫秒
     * @return 当天 00:00 UTC 的 epoch 毫秒
     */
    private long dayStartMs(long epochMs) {
        return Instant.ofEpochMilli(epochMs)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    /**
     * 返回 epoch 毫秒所在天的下一日 00:00 UTC 的 epoch 毫秒。
     *
     * @param epochMs epoch 毫秒
     * @return 明天 00:00 UTC 的 epoch 毫秒
     */
    private long nextDayStartMs(long epochMs) {
        return Instant.ofEpochMilli(epochMs)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }
}
