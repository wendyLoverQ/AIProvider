// useQuantMarketSocket — 合约行情 WebSocket 生命周期管理 Hook。
// 只连接本机后端代理 /ws/quant/market，绝不直连外部交易所 WebSocket。
// 负责 SUBSCRIBE/UNSUBSCRIBE、消息解析、自动重连与按 symbol/interval 过滤事件。
import { useCallback, useEffect, useRef, useState } from "react";
import { shouldIgnoreEvent } from "./quantMarketReducer";

// WebSocket 连接状态码与中文标签。
export const SOCKET_STATUS = {
  CONNECTING: "CONNECTING",
  LIVE: "LIVE",
  RECONNECTING: "RECONNECTING",
  DISCONNECTED: "DISCONNECTED",
  FAILED: "FAILED",
};

export const SOCKET_STATUS_LABELS = {
  CONNECTING: "连接中",
  LIVE: "实时",
  RECONNECTING: "正在重连",
  DISCONNECTED: "已断开",
  FAILED: "连接失败",
};

const WS_PATH = "/ws/quant/market";
const MAX_RECONNECT_ATTEMPTS = 6;

function buildSocketUrl() {
  if (typeof window === "undefined") return null;
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${window.location.host}${WS_PATH}`;
}

/**
 * @param {{provider:string, symbol:string, interval:string, enabled?:boolean}} params
 * @returns {{status:string, klineEvent:object|null, tickerEvent:object|null,
 *           markPriceEvent:object|null, bookTickerEvent:object|null,
 *           error:string, lastEventTime:string|null, reconnect:()=>void}}
 */
export function useQuantMarketSocket({ provider, symbol, interval, enabled = true }) {
  const [status, setStatus] = useState(enabled ? SOCKET_STATUS.CONNECTING : SOCKET_STATUS.DISCONNECTED);
  const [klineEvent, setKlineEvent] = useState(null);
  const [tickerEvent, setTickerEvent] = useState(null);
  const [markPriceEvent, setMarkPriceEvent] = useState(null);
  const [bookTickerEvent, setBookTickerEvent] = useState(null);
  const [error, setError] = useState("");
  const [lastEventTime, setLastEventTime] = useState(null);

  const wsRef = useRef(null);
  const socketIdRef = useRef(0);
  const reconnectTimerRef = useRef(null);
  const attemptRef = useRef(0);
  // 用 ref 保存最新订阅参数，避免消息回调闭包过期。
  const paramsRef = useRef({ provider, symbol, interval, enabled });
  paramsRef.current = { provider, symbol, interval, enabled };

  const clearReconnectTimer = useCallback(() => {
    if (reconnectTimerRef.current != null) {
      window.clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
  }, []);

  const sendJson = useCallback((obj) => {
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      try {
        ws.send(JSON.stringify(obj));
      } catch {
        // 发送失败由 onclose/onerror 兜底重连，不在此处吞掉根因。
      }
    }
  }, []);

  const subscribe = useCallback(() => {
    const { provider: p, symbol: s, interval: i } = paramsRef.current;
    if (!p || !s || !i) return;
    sendJson({ action: "SUBSCRIBE", provider: p, symbol: s, interval: i });
  }, [sendJson]);

  const unsubscribe = useCallback(() => {
    sendJson({ action: "UNSUBSCRIBE" });
  }, [sendJson]);

  // 处理一条已解析的服务器消息。
  const handleMessage = useCallback((msg) => {
    if (!msg || typeof msg !== "object") return;
    const { type, eventTime, data } = msg;
    const { symbol: curSymbol, interval: curInterval } = paramsRef.current;

    // STATUS/ERROR 为全局消息，不按 symbol 过滤。
    if (type === "STATUS") {
      if (eventTime) setLastEventTime(eventTime);
      if (data && data.status === "LIVE") {
        setStatus(SOCKET_STATUS.LIVE);
        setError("");
      }
      return;
    }
    if (type === "ERROR") {
      setError((data && data.message) || "服务器返回错误");
      if (eventTime) setLastEventTime(eventTime);
      return;
    }

    // 按 symbol/interval 过滤，避免切换合约时残留旧事件。
    if (shouldIgnoreEvent(msg, curSymbol, curInterval)) return;
    if (eventTime) setLastEventTime(eventTime);

    switch (type) {
      case "KLINE":
        setKlineEvent(msg);
        break;
      case "TICKER":
        setTickerEvent(msg);
        break;
      case "MARK_PRICE":
        setMarkPriceEvent(msg);
        break;
      case "BOOK_TICKER":
        setBookTickerEvent(msg);
        break;
      default:
        break;
    }
  }, []);

  // 建立连接（含重连）。
  const connect = useCallback(() => {
    if (typeof window === "undefined") return;
    if (!paramsRef.current.enabled) return;
    const url = buildSocketUrl();
    if (!url) return;

    // 关闭旧连接但不触发其 onclose 重连逻辑。
    if (wsRef.current) {
      const old = wsRef.current;
      wsRef.current = null;
      old.onopen = null;
      old.onmessage = null;
      old.onerror = null;
      old.onclose = null;
      try { old.close(); } catch { /* 旧 socket 关闭异常忽略 */ }
    }

    const myId = ++socketIdRef.current;
    setStatus(attemptRef.current === 0 ? SOCKET_STATUS.CONNECTING : SOCKET_STATUS.RECONNECTING);

    let ws;
    try {
      ws = new WebSocket(url);
    } catch {
      setStatus(SOCKET_STATUS.FAILED);
      return;
    }
    wsRef.current = ws;

    ws.onopen = () => {
      if (wsRef.current !== ws) return;
      attemptRef.current = 0;
      setStatus(SOCKET_STATUS.LIVE);
      setError("");
      subscribe();
    };

    ws.onmessage = (evt) => {
      if (wsRef.current !== ws) return;
      let msg;
      try {
        msg = JSON.parse(typeof evt.data === "string" ? evt.data : "");
      } catch {
        return;
      }
      handleMessage(msg);
    };

    ws.onerror = () => {
      if (wsRef.current !== ws) return;
      setError("WebSocket 连接发生错误");
    };

    ws.onclose = () => {
      if (wsRef.current !== ws) return;
      wsRef.current = null;
      if (!paramsRef.current.enabled) {
        setStatus(SOCKET_STATUS.DISCONNECTED);
        return;
      }
      // 自动重连，指数退避。
      attemptRef.current += 1;
      if (attemptRef.current > MAX_RECONNECT_ATTEMPTS) {
        setStatus(SOCKET_STATUS.FAILED);
        return;
      }
      setStatus(SOCKET_STATUS.RECONNECTING);
      const delay = Math.min(1000 * 2 ** (attemptRef.current - 1), 15000);
      clearReconnectTimer();
      reconnectTimerRef.current = window.setTimeout(() => connect(), delay);
    };
  }, [subscribe, handleMessage, clearReconnectTimer]);

  // 手动重连：重置尝试次数后重新连接。
  const reconnect = useCallback(() => {
    attemptRef.current = 0;
    clearReconnectTimer();
    connect();
  }, [connect, clearReconnectTimer]);

  // 连接生命周期：enabled 变化或挂载/卸载。
  useEffect(() => {
    if (!enabled) {
      setStatus(SOCKET_STATUS.DISCONNECTED);
      return undefined;
    }
    connect();
    return () => {
      clearReconnectTimer();
      const ws = wsRef.current;
      if (ws) {
        wsRef.current = null;
        ws.onopen = null;
        ws.onmessage = null;
        ws.onerror = null;
        ws.onclose = null;
        if (ws.readyState === WebSocket.OPEN) {
          try { ws.send(JSON.stringify({ action: "UNSUBSCRIBE" })); } catch { /* 忽略 */ }
        }
        try { ws.close(); } catch { /* 忽略 */ }
      }
    };
  }, [enabled, connect, clearReconnectTimer]);

  // symbol/interval/provider 变化时重新订阅并清空旧事件。
  useEffect(() => {
    setKlineEvent(null);
    setTickerEvent(null);
    setMarkPriceEvent(null);
    setBookTickerEvent(null);
    setError("");
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      unsubscribe();
      subscribe();
    }
  }, [provider, symbol, interval, subscribe, unsubscribe]);

  return {
    status,
    klineEvent,
    tickerEvent,
    markPriceEvent,
    bookTickerEvent,
    error,
    lastEventTime,
    reconnect,
  };
}
