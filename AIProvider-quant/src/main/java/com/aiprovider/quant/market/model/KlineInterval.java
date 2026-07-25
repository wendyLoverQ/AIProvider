package com.aiprovider.quant.market.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * K 线周期枚举。
 *
 * 覆盖 Binance USDⓈ-M Futures 支持的公共周期。前端只暴露前 6 个常用周期
 * （1m、5m、15m、1h、4h、1d），其余周期用于后续扩展或直接对上游兼容。
 */
public enum KlineInterval {
    M1("1m"),
    M3("3m"),
    M5("5m"),
    M15("15m"),
    M30("30m"),
    H1("1h"),
    H2("2h"),
    H4("4h"),
    H6("6h"),
    H12("12h"),
    D1("1d"),
    W1("1w"),
    MO1("1M");

    private final String code;

    KlineInterval(String code) {
        this.code = code;
    }

    /** 返回对应 Binance API 的 interval 字符串。 */
    public String code() {
        return code;
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
