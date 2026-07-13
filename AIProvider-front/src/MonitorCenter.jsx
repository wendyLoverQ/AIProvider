import { useCallback, useEffect, useState } from "react";
import { ArrowsClockwise, CheckCircle, Clock, Cpu, Gauge, HardDrives, Pulse, Warning } from "@phosphor-icons/react";
import "./MonitorCenter.css";
import "./MonitorCenterEnhancements.css";

const API = "/api/monitor";
let monitorSummaryCache = null;
async function readSummary() {
  const paths = ["summary", "ai-overview", "ai-timeseries?range=24h"];
  const values = await Promise.all(paths.map(async (path) => {
    const response = await fetch(`${API}/${path}`); if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const json = await response.json(); if (json.code !== 200) throw new Error(json.message || "监控数据读取失败"); return json.data;
  }));
  return { summary: values[0], overview: values[1], timeseries: values[2] || [] };
}
const bytes = (value) => {
  if (value == null) return "—";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let number = Number(value), index = 0;
  while (number >= 1024 && index < units.length - 1) { number /= 1024; index += 1; }
  return `${number.toFixed(index < 2 ? 0 : 2)} ${units[index]}`;
};
const percent = (used, total) => total ? Math.min(100, Math.max(0, used / total * 100)) : 0;
const dateTime = (value) => value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—";

export default function MonitorCenter() {
  const [summary, setSummary] = useState(monitorSummaryCache);
  const [loading, setLoading] = useState(!monitorSummaryCache);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const load = useCallback(async (silent = false) => {
    if (!silent) setRefreshing(true);
    try { const next = await readSummary(); monitorSummaryCache = next; setSummary(next); setError(""); }
    catch (exception) { setError(exception.message); }
    finally { setLoading(false); setRefreshing(false); }
  }, []);
  useEffect(() => {
    load();
    const timer = setInterval(() => load(true), 60000);
    return () => clearInterval(timer);
  }, [load]);

  return <section className={`cloud-monitor ${loading ? "is-loading" : ""}`}>
    <div className="monitor-kawaii-crown" aria-hidden="true"><i>✦</i><b>♡ SYSTEM GUARDIAN ♡</b><i>✦</i></div>
    <header className="cloud-toolbar">
      <div><span className="eyebrow">SERVICE · SERVER · TENCENT CLOUD</span><p>服务请求、服务器资源与腾讯云流量</p></div>
      <button onClick={() => load()} disabled={refreshing || loading}><ArrowsClockwise className={refreshing || loading ? "spin" : ""} />{loading ? "正在读取" : refreshing ? "刷新中" : "手动刷新"}</button>
    </header>
    {error && <div className="cloud-error"><Warning />部分数据暂不可用：{error}</div>}
    <div className={`cloud-health ${summary?.summary?.health?.status === "UP" ? "healthy" : "unhealthy"}`}>
      <Pulse /><div><span>Provider Health</span><strong>{summary?.summary?.health?.status || "UNKNOWN"}</strong><small>检查于 {dateTime(summary?.summary?.health?.checkedAt)}</small></div>
    </div>
    <ServiceRequests overview={summary?.overview} timeseries={summary?.timeseries} />
    <div className="cloud-capacity-grid">
      <Capacity title="服务器内存" icon={Cpu} resource={summary?.summary?.memory} collectedAt={summary?.summary?.collectedAt} />
      <Capacity title="系统磁盘" icon={HardDrives} resource={summary?.summary?.disk} collectedAt={summary?.summary?.collectedAt} />
      <Traffic traffic={summary?.summary?.traffic} />
    </div>
  </section>;
}

function ServiceRequests({ overview = {}, timeseries = [] }) {
  const points = timeseries.slice(-24);
  const maxRequests = Math.max(1, ...points.map((item) => Number(item.totalRequests || 0)));
  const maxP95 = Math.max(1, ...points.map((item) => Number(item.p95DurationMs || 0)));
  return <section className="service-requests">
    <header><div><h2>服务请求</h2><span>今天汇总 · 最近 24 小时明细</span></div></header>
    <div className="service-kpis">
      <div><Pulse /><span>请求总数</span><strong>{Number(overview.totalRequests || 0).toLocaleString("zh-CN")}</strong></div>
      <div><CheckCircle /><span>成功率</span><strong>{Number(overview.successRate || 0).toFixed(1)}%</strong></div>
      <div><Warning /><span>失败请求</span><strong>{Number(overview.failureCount || 0).toLocaleString("zh-CN")}</strong></div>
      <div><Clock /><span>P95 响应</span><strong>{Number(overview.p95DurationMs || 0).toLocaleString("zh-CN")} ms</strong></div>
    </div>
    {points.length ? <div className="request-visual">
      <div className="request-visual-head"><strong>24 小时请求走势</strong><span><i className="volume" />请求量</span><span><i className="failure" />失败率</span><span><i className="latency" />P95 响应</span></div>
      <div className="request-chart" role="img" aria-label="最近 24 小时请求量、失败率和 P95 响应趋势">
        {points.map((item, index) => {
          const requests = Number(item.totalRequests || 0);
          const failure = Number(item.errorRate || 0);
          const p95 = Number(item.p95DurationMs || 0);
          const time = new Date(item.bucket);
          return <div className="request-point" key={item.bucket} title={`${dateTime(item.bucket)}\n请求 ${requests}\n失败率 ${failure.toFixed(1)}%\n平均 ${Math.round(Number(item.avgDurationMs || 0))} ms\nP95 ${p95} ms`}>
            <div className="request-bar"><i style={{ height: `${Math.max(requests ? 5 : 0, requests / maxRequests * 100)}%` }} /><b style={{ bottom: `${Math.min(94, p95 / maxP95 * 100)}%` }} /><em style={{ height: `${Math.min(100, failure)}%` }} /></div>
            {(index === 0 || index === points.length - 1 || index % 4 === 0) && <small>{time.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false })}</small>}
          </div>;
        })}
      </div>
    </div> : <div className="request-visual-empty">最近 24 小时暂无请求</div>}
  </section>;
}

function Capacity({ title, icon: Icon, resource, collectedAt }) {
  const usage = percent(resource?.usedBytes, resource?.totalBytes);
  const tone = usage >= 90 ? "danger" : usage >= 70 ? "warning" : "normal";
  return <article className="cloud-card">
    <header><Icon /><div><h2>{title}</h2><span>{resource?.available ? "正常采集" : "不可用"}</span></div><b className={tone}>{resource?.available ? `${usage.toFixed(1)}%` : "—"}</b></header>
    <div className="cloud-main"><strong>{bytes(resource?.usedBytes)}</strong><span>/ {bytes(resource?.totalBytes)}</span></div>
    <div className="cloud-track"><i className={tone} style={{ width: `${usage}%` }} /></div>
    <small>采集时间 {dateTime(collectedAt)}</small>
  </article>;
}

function Traffic({ traffic }) {
  const usage = percent(traffic?.usedBytes, traffic?.totalBytes);
  const tone = usage >= 90 ? "danger" : usage >= 70 ? "warning" : "normal";
  return <article className="cloud-card traffic-card">
    <header><Gauge /><div><h2>本期流量包</h2><span>{traffic?.stale ? "数据可能已过期" : traffic?.status || "不可用"}</span></div><b className={traffic?.stale ? "warning" : tone}>{traffic?.available ? `${usage.toFixed(1)}%` : "—"}</b></header>
    <div className="cloud-main"><strong>{bytes(traffic?.usedBytes)}</strong><span>/ {bytes(traffic?.totalBytes)}</span></div>
    <div className="cloud-track"><i className={tone} style={{ width: `${usage}%` }} /></div>
    <dl><div><dt>剩余</dt><dd>{bytes(traffic?.remainingBytes)}</dd></div><div><dt>超额</dt><dd>{bytes(traffic?.overflowBytes)}</dd></div><div><dt>流量周期</dt><dd>{dateTime(traffic?.periodStart)} — {dateTime(traffic?.periodEnd)}</dd></div></dl>
    <small>腾讯云采集时间 {dateTime(traffic?.collectedAt)}</small>
  </article>;
}
