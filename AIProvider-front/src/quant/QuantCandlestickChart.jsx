// QuantCandlestickChart — 基于 TradingView lightweight-charts v5 的合约 K 线图组件。
// 只通过 props 接收数据，不发起 API 请求，不创建 WebSocket。
// candles 为 REST 初始数据（openTime 为 ISO 字符串），update 为 WebSocket 单根增量更新。
import { useEffect, useRef } from "react";
import {
  createChart,
  CandlestickSeries,
  HistogramSeries,
  ColorType,
} from "lightweight-charts";
import "./QuantCandlestickChart.css";

// 读取语义主题变量的实际颜色值（canvas 无法直接消费 CSS 变量）。
function readThemeColor(name, fallback) {
  if (typeof window === "undefined") return fallback;
  try {
    const value = window.getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return value || fallback;
  } catch {
    return fallback;
  }
}

// 收集当前主题的涨跌与基础配色。
function readThemeColors() {
  return {
    up: readThemeColor("--accent-mint", "#72ddb1"),
    down: readThemeColor("--accent-red", "#ff718f"),
    text: readThemeColor("--text-muted", "#b39aa6"),
    border: readThemeColor("--border-normal", "#6f5269"),
    background: readThemeColor("--bg-surface", "#221923"),
  };
}

// 将多种形式的时间统一换算成毫秒时间戳，兼容 ISO 字符串、数字与数字字符串。
function toTimeMs(value) {
  if (value == null) return NaN;
  if (typeof value === "number") return value;
  const s = String(value).trim();
  if (s === "") return NaN;
  if (/^\d+$/.test(s)) {
    const n = Number(s);
    // 10 位及以下视为秒，13 位视为毫秒。
    return s.length <= 10 ? n * 1000 : n;
  }
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? NaN : d.getTime();
}

// 将 candle（openTime 可为 ISO 字符串或数字字符串）转换为 lightweight-charts 的 K 线 Bar。
function toCandleBar(c) {
  if (!c || c.openTime == null) return null;
  const ms = toTimeMs(c.openTime);
  if (!Number.isFinite(ms)) return null;
  return {
    time: Math.floor(ms / 1000),
    open: Number(c.open),
    high: Number(c.high),
    low: Number(c.low),
    close: Number(c.close),
  };
}

// 将 candle 转换为成交量 Bar，颜色按当根涨跌着色。
function toVolumeBar(c, upColor, downColor) {
  if (!c || c.openTime == null) return null;
  const ms = toTimeMs(c.openTime);
  if (!Number.isFinite(ms)) return null;
  const open = Number(c.open);
  const close = Number(c.close);
  return {
    time: Math.floor(ms / 1000),
    value: Number(c.volume) || 0,
    color: close >= open ? upColor : downColor,
  };
}

export default function QuantCandlestickChart({ candles, update }) {
  const containerRef = useRef(null);
  const chartRef = useRef(null);
  const candleSeriesRef = useRef(null);
  const volumeSeriesRef = useRef(null);

  // 创建图表与系列（仅一次），并绑定 ResizeObserver 与主题监听。
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;

    const colors = readThemeColors();
    const chart = createChart(container, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: colors.background },
        textColor: colors.text,
        fontSize: 11,
      },
      grid: {
        vertLines: { color: colors.border },
        horzLines: { color: colors.border },
      },
      crosshair: { mode: 1 },
      rightPriceScale: { borderColor: colors.border },
      timeScale: {
        borderColor: colors.border,
        timeVisible: true,
        secondsVisible: false,
      },
    });
    chartRef.current = chart;

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: colors.up,
      downColor: colors.down,
      borderUpColor: colors.up,
      borderDownColor: colors.down,
      wickUpColor: colors.up,
      wickDownColor: colors.down,
    });
    candleSeriesRef.current = candleSeries;

    // 成交量使用独立 overlay 价格刻度，挤压到图表底部 20% 区域。
    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: "volume" },
      priceScaleId: "",
    });
    chart.priceScale("").applyOptions({
      scaleMargins: { top: 0.8, bottom: 0 },
    });
    volumeSeriesRef.current = volumeSeries;

    // 主题切换时重新读取语义颜色并应用（主题以 inline style 写在 documentElement 上）。
    const applyThemeColors = () => {
      const c = readThemeColors();
      chart.applyOptions({
        layout: {
          background: { type: ColorType.Solid, color: c.background },
          textColor: c.text,
        },
        grid: {
          vertLines: { color: c.border },
          horzLines: { color: c.border },
        },
        rightPriceScale: { borderColor: c.border },
        timeScale: { borderColor: c.border },
      });
      candleSeries.applyOptions({
        upColor: c.up,
        downColor: c.down,
        borderUpColor: c.up,
        borderDownColor: c.down,
        wickUpColor: c.up,
        wickDownColor: c.down,
      });
    };
    const themeObserver = new MutationObserver(applyThemeColors);
    themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["style"],
    });

    // autoSize 的兜底：部分环境下 ResizeObserver 失败时手动 resize。
    const resizeObserver = new ResizeObserver(() => {
      if (container.clientWidth && container.clientHeight) {
        chart.resize(container.clientWidth, container.clientHeight);
      }
    });
    resizeObserver.observe(container);

    return () => {
      themeObserver.disconnect();
      resizeObserver.disconnect();
      chart.remove();
      chartRef.current = null;
      candleSeriesRef.current = null;
      volumeSeriesRef.current = null;
    };
  }, []);

  // candles 变化（REST 初始加载或切换 symbol/interval）时整体重置数据。
  useEffect(() => {
    const series = candleSeriesRef.current;
    const volSeries = volumeSeriesRef.current;
    const chart = chartRef.current;
    if (!series || !volSeries || !chart) return;
    const list = Array.isArray(candles) ? candles : [];
    const colors = readThemeColors();
    const candleData = [];
    const volumeData = [];
    let prevTime = -Infinity;
    for (const c of list) {
      const bar = toCandleBar(c);
      if (!bar) continue;
      // lightweight-charts 要求时间严格递增且唯一。
      if (bar.time <= prevTime) continue;
      prevTime = bar.time;
      candleData.push(bar);
      const vbar = toVolumeBar(c, colors.up, colors.down);
      if (vbar) volumeData.push(vbar);
    }
    series.setData(candleData);
    volSeries.setData(volumeData);
    chart.timeScale().fitContent();
  }, [candles]);

  // update 变化（WebSocket 单根增量）时调用 series.update。
  useEffect(() => {
    const series = candleSeriesRef.current;
    const volSeries = volumeSeriesRef.current;
    if (!series || !volSeries || !update) return;
    const bar = toCandleBar(update);
    if (!bar) return;
    const colors = readThemeColors();
    try {
      series.update(bar);
    } catch {
      // 时间乱序等异常时忽略本次单根更新，下一次 setData 会纠正。
    }
    const vbar = toVolumeBar(update, colors.up, colors.down);
    if (vbar) {
      try {
        volSeries.update(vbar);
      } catch {
        // 同上，忽略异常更新。
      }
    }
  }, [update]);

  return (
    <div
      className="quant-candlestick-chart"
      ref={containerRef}
      role="img"
      aria-label="合约 K 线实时图表"
      tabIndex={0}
    >
      <span className="quant-candlestick-legend" aria-hidden="true">
        <i className="leg leg-up" />涨
        <i className="leg leg-down" />跌
      </span>
    </div>
  );
}
