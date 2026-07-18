import { useCallback, useEffect, useState } from "react";
import { ArrowsClockwise, ArrowsDownUp, CheckCircle, Clock, Coins, Cpu, Gauge, Gift, HardDrives, Pulse, Warning } from "@phosphor-icons/react";
import "./MonitorCenter.css";
import "./MonitorCenterEnhancements.css";

const API = "/api/monitor";
let monitorSummaryCache = null;
async function readSummary() {
  const paths = ["cloud-servers", "ai-overview", "ai-timeseries?range=24h", "aws-billing"];
  const values = await Promise.all(paths.map(async (path) => {
    const response = await fetch(`${API}/${path}`); if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const json = await response.json(); if (json.code !== 200) throw new Error(json.message || "监控数据读取失败"); return json.data;
  }));
  return { cloudServers: values[0], overview: values[1], timeseries: values[2] || [], awsBilling: values[3] };
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
const money = (value, currency = "USD") => value == null ? "—" : new Intl.NumberFormat("zh-CN", { style: "currency", currency: currency || "USD", maximumFractionDigits: 2 }).format(Number(value));
const apiReason = (value) => value ? value.replace(/Exception$/, "").replace(/([a-z])([A-Z])/g, "$1 $2") : "AWS API 不可用";

export default function MonitorCenter() {
  const [summary, setSummary] = useState(monitorSummaryCache);
  const [loading, setLoading] = useState(!monitorSummaryCache);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [selectedCloud, setSelectedCloud] = useState("aws");
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

  const server = summary?.cloudServers?.[selectedCloud];
  return <section className={`cloud-monitor ${loading ? "is-loading" : ""}`}>
    <div className="monitor-kawaii-crown" aria-hidden="true"><i>✦</i><b>♡ SYSTEM GUARDIAN ♡</b><i>✦</i></div>
    <header className="cloud-toolbar">
      <div><span className="eyebrow">SERVICE · SERVER · {selectedCloud === "aws" ? "AWS" : "TENCENT CLOUD"}</span><p>服务请求、服务器资源、实时网速与本月流量</p></div>
      <div className="cloud-toolbar-actions">
        <div className="cloud-provider-switch" aria-label="云服务器切换">
          <button type="button" aria-pressed={selectedCloud === "tencent"} onClick={() => setSelectedCloud("tencent")}>腾讯云</button>
          <button type="button" aria-pressed={selectedCloud === "aws"} onClick={() => setSelectedCloud("aws")}>AWS</button>
        </div>
        <button type="button" onClick={() => load()} disabled={refreshing || loading}><ArrowsClockwise className={refreshing || loading ? "spin" : ""} />{loading ? "正在读取" : refreshing ? "刷新中" : "手动刷新"}</button>
      </div>
    </header>
    {error && <div className="cloud-error"><Warning />部分数据暂不可用：{error}</div>}
    <div className={`cloud-health ${server?.status === "UP" ? "healthy" : "unhealthy"}`}>
      <Pulse /><div><span>{server?.displayName || "云服务器"}健康状态</span><strong>{server?.status || "UNKNOWN"}</strong><small>检查于 {dateTime(server?.collectedAt)}</small></div>
    </div>
    <ServiceRequests overview={summary?.overview} timeseries={summary?.timeseries} />
    <div className="cloud-capacity-grid">
      <Capacity title="服务器内存" icon={Cpu} resource={server?.memory} collectedAt={server?.collectedAt} />
      <Capacity title="系统磁盘" icon={HardDrives} resource={server?.disk} collectedAt={server?.collectedAt} />
      <Network network={server?.network} instance={server?.instance} />
      {selectedCloud === "aws" ? <AwsBilling billing={summary?.awsBilling} /> : <Traffic traffic={server?.traffic} provider={selectedCloud} />}
    </div>
  </section>;
}

function AwsBilling({ billing }) {
  const plan = billing?.plan || {}, cost = billing?.cost || {}, credits = billing?.credits || {}, freeTier = billing?.freeTier || {};
  const hasBilling = Boolean(billing);
  const risks = freeTier.items || [];
  const sourceErrors = [plan, cost, credits, freeTier].filter((item) => item.available === false).map((item) => apiReason(item.unavailableReason));
  return <article className="cloud-card aws-billing-card">
    <header><Coins /><div><h2>AWS 余额与本月费用</h2><span>{!hasBilling ? "正在读取真实账单接口" : sourceErrors.length ? `${4 - sourceErrors.length}/4 路真实接口可用` : "4 路 AWS 账单接口已连接"}</span></div><b className={sourceErrors.length ? "warning" : "normal"}>{!hasBilling ? "读取中" : plan.available ? `${plan.type || "—"} · ${plan.status || "—"}` : "不可用"}</b></header>
    <div className="aws-billing-stats">
      <div><span>本月净费用{cost.estimated ? "（预估）" : ""}</span><strong>{cost.available ? money(cost.netUnblendedCost, cost.currency) : "—"}</strong></div>
      <div><span>Credits 剩余</span><strong>{credits.available ? money(credits.remainingAmount, credits.currency) : plan.available ? money(plan.remainingCredits, plan.currency) : "—"}</strong></div>
      <div><span>免费额度项目</span><strong>{freeTier.available ? `${risks.length} 项` : "—"}</strong></div>
    </div>
    {risks.length > 0 && <details className="aws-free-tier"><summary><Gift />免费额度用量明细</summary><div>{risks.map((item, index) => <div key={`${item.service}-${item.usageType}-${index}`}><span>{item.description || item.service || item.usageType}</span><strong>{Number(item.actual || 0).toLocaleString("zh-CN")} / {Number(item.limit || 0).toLocaleString("zh-CN")} {item.unit || ""}</strong><i><b style={{ width: `${Math.min(100, Number(item.usagePercent || 0))}%` }} /></i></div>)}</div></details>}
    {sourceErrors.length > 0 && <small className="aws-source-errors">不可用：{sourceErrors.join("；")}</small>}
    <small>Cost Explorer、Free Tier 与 Billing API · 缓存 6 小时 · 采集于 {dateTime(billing?.collectedAt)}</small>
  </article>;
}

function ServiceRequests({ overview = {}, timeseries = [] }) {
  const [hoveredIndex, setHoveredIndex] = useState(null);
  const points = timeseries.slice(-24);
  const maxRequests = Math.max(1, ...points.map((item) => Number(item.totalRequests || 0)));
  const maxP95 = Math.max(1, ...points.map((item) => Number(item.p95DurationMs || 0)));
  const activePoint = hoveredIndex == null ? null : points[hoveredIndex];
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
      <div className="request-chart" role="img" aria-label="最近 24 小时请求量、失败率和 P95 响应趋势" onMouseLeave={() => setHoveredIndex(null)}>
        {activePoint && <div className="request-tooltip" style={{ left: `clamp(120px, ${((hoveredIndex + 0.5) / points.length) * 100}%, calc(100% - 120px))` }}>
          <header><strong>{new Date(activePoint.bucket).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false })}</strong><span>小时统计</span></header>
          <dl>
            <div><dt>请求量</dt><dd>{Number(activePoint.totalRequests || 0).toLocaleString("zh-CN")}</dd></div>
            <div><dt>失败率</dt><dd>{Number(activePoint.errorRate || 0).toFixed(1)}%</dd></div>
            <div><dt>平均响应</dt><dd>{Math.round(Number(activePoint.avgDurationMs || 0)).toLocaleString("zh-CN")} ms</dd></div>
            <div><dt>P95 响应</dt><dd>{Number(activePoint.p95DurationMs || 0).toLocaleString("zh-CN")} ms</dd></div>
          </dl>
        </div>}
        {points.map((item, index) => {
          const requests = Number(item.totalRequests || 0);
          const failure = Number(item.errorRate || 0);
          const p95 = Number(item.p95DurationMs || 0);
          const time = new Date(item.bucket);
          return <div className={`request-point ${hoveredIndex === index ? "is-active" : ""}`} key={item.bucket} tabIndex="0" aria-label={`${time.toLocaleString("zh-CN", { hour12: false })}，请求 ${requests}，失败率 ${failure.toFixed(1)}%，P95 ${p95} 毫秒`} onMouseEnter={() => setHoveredIndex(index)} onFocus={() => setHoveredIndex(index)} onBlur={() => setHoveredIndex(null)}>
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

function Network({ network, instance }) {
  return <article className="cloud-card network-card">
    <header><ArrowsDownUp /><div><h2>CloudWatch 网络</h2><span>{network?.available ? "AWS API 近实时采集" : "暂不可用"}</span></div></header>
    <dl><div><dt>下载</dt><dd>{bytes(network?.inboundBytesPerSecond)}/s</dd></div><div><dt>上传</dt><dd>{bytes(network?.outboundBytesPerSecond)}/s</dd></div><div><dt>本月入站</dt><dd>{bytes(network?.monthInboundBytes)}</dd></div><div><dt>本月出站</dt><dd>{bytes(network?.monthOutboundBytes)}</dd></div></dl>
    <small>{instance?.instanceType || "实例类型未知"} · {instance?.availabilityZone || instance?.region || "区域未知"} · {instance?.publicIpv4 || "公网 IP 未知"}</small>
    <small>AWS API：{instance?.awsApiStatus || "不适用"} · 实例 {instance?.instanceId || "—"}</small>
  </article>;
}

function Traffic({ traffic, provider }) {
  const usage = percent(traffic?.usedBytes, traffic?.totalBytes);
  const tone = usage >= 90 ? "danger" : usage >= 70 ? "warning" : "normal";
  return <article className="cloud-card traffic-card">
    <header><Gauge /><div><h2>{provider === "aws" ? "AWS 本月 100 GB 流量" : "腾讯云本期流量包"}</h2><span>{traffic?.stale ? "数据可能已过期" : traffic?.status || "不可用"}</span></div><b className={traffic?.stale ? "warning" : tone}>{traffic?.available ? `${usage.toFixed(1)}%` : "—"}</b></header>
    <div className="cloud-main"><strong>{bytes(traffic?.usedBytes)}</strong><span>/ {bytes(traffic?.totalBytes)}</span></div>
    <div className="cloud-track"><i className={tone} style={{ width: `${usage}%` }} /></div>
    <dl><div><dt>剩余</dt><dd>{bytes(traffic?.remainingBytes)}</dd></div><div><dt>超额</dt><dd>{bytes(traffic?.overflowBytes)}</dd></div><div><dt>流量周期</dt><dd>{dateTime(traffic?.periodStart)} — {dateTime(traffic?.periodEnd)}</dd></div></dl>
    <small>{provider === "aws" ? "AWS CloudWatch API · 100GB 为全账户免费公网出站额度" : "腾讯云 API 采集"} {dateTime(traffic?.collectedAt)}</small>
  </article>;
}
