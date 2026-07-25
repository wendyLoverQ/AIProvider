package com.aiprovider.quant.market.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * K 线周期枚举。
 *
 * 覆盖 Binance USDⓈ-M Futures 支持的公共周期。前端只暴露前 6 个常用周期
 * （1m、5m、15m、1h、4h、1d），其余周期用于后续扩展或直接对上游兼容。
 *
 * 固定时长周期（isFixedDuration()=true）支持历史分页同步的缺口计算和时间对齐。
 * 变量长度周期（1M）不支持缺口计算，调用方会拒绝同步请求。
 */
public enum KlineInterval {
    M1("1m", true, 60_000L),
    M3("3m", true, 180_000L),
    M5("5m", true, 300_000L),
    M15("15m", true, 900_000L),
    M30("30m", true, 1_800_000L),
    H1("1h", true, 3_600_000L),
    H2("2h", true, 7_200_000L),
    H4("4h", true, 14_400_000L),
    H6("6h", true, 21_600_000L),
    H12("12h", true, 43_200_000L),
    D1("1d", true, 86_400_000L),
    W1("1w", true, 604_800_000L),
    MO1("1M", false, 0L);

    private final String code;
    private final boolean fixedDuration;
    private final long durationMillis;

    KlineInterval(String code, boolean fixedDuration, long durationMillis) {
        this.code = code;
        this.fixedDuration = fixedDuration;
        this.durationMillis = durationMillis;
    }

    /** 返回对应 Binance API 的 interval 字符串。 */
    public String code() {
        return code;
    }

    /**
     * 是否为固定毫秒长度周期。
     *
     * 1M（月线）不是固定长度，不能用于历史分页缺口计算。
     * 1w（周线）虽然跨月但固定为 7 天，视为固定时长。
     *
     * @return true 表示该周期有固定毫秒长度
     */
    public boolean isFixedDuration() {
        return fixedDuration;
    }

    /**
     * 返回固定时长周期的毫秒长度。
     *
     * @return 周期毫秒长度
     * @throws UnsupportedOperationException 当周期不是固定时长（如 1M）
     */
    public long durationMillis() {
        if (!fixedDuration) {
            throw new UnsupportedOperationException("非固定时长周期不支持 durationMillis: " + code);
        }
        return durationMillis;
    }

    /**
     * 将给定时间戳向下对齐到该周期的起点。
     *
     * 对齐基于 UTC epoch 毫秒。对于固定时长周期，对齐方式为：
     * {@code aligned = (epochMillis / durationMillis) * durationMillis}
     *
     * @param instant 待对齐的时间点
     * @return 对齐后的周期起点 Instant
     * @throws UnsupportedOperationException 当周期不是固定时长
     */
    public Instant alignOpenTime(Instant instant) {
        if (!fixedDuration) {
            throw new UnsupportedOperationException("非固定时长周期不支持 alignOpenTime: " + code);
        }
        long epochMillis = instant.toEpochMilli();
        long aligned = (epochMillis / durationMillis) * durationMillis;
        return Instant.ofEpochMilli(aligned);
    }

    /** 历史同步第一阶段支持的固定周期列表（1m、5m、15m、1h、4h、1d）。 */
    public static final Set<KlineInterval> SYNC_SUPPORTED = Collections.unmodifiableSet(EnumSet.of(
            M1, M5, M15, H1, H4, D1));

    /**
     * 判断该周期是否在历史同步第一阶段支持列表中。
     *
     * @return true 表示该周期可用于历史同步
     */
    public boolean isSyncSupported() {
        return SYNC_SUPPORTED.contains(this);
    }

    private static final Map<String, KlineInterval> BY_CODE;

    static {
        Map<String, KlineInterval> map = new LinkedHashMap<>();
        for (KlineInterval interval : values()) {
            map.put(interval.code, interval);
        }
        BY_CODE = Collections.unmodifiableMap(map);
    }

    /**
     * 根据 Binance interval 字符串解析枚举。
     *
     * @param code Binance interval 字符串，例如 "15m"
     * @return 对应枚举
     * @throws IllegalArgumentException 当 code 为 null 或不是合法周期
     */
    public static KlineInterval fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("K 线周期不能为空");
        }
        KlineInterval interval = BY_CODE.get(code);
        if (interval == null) {
            throw new IllegalArgumentException("不支持的 K 线周期: " + code);
        }
        return interval;
    }
}
