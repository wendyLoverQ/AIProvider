import { useCallback, useEffect, useState } from "react";
import {
  ArrowsClockwise,
  ChartLineUp,
  Database,
  Lightning,
  LockKey,
  Sparkle,
  Warning,
} from "@phosphor-icons/react";
import { readJsonResponse } from "../apiResponse";
import QuantPageScaffold from "./QuantPageScaffold";

const OVERVIEW_PATH = "/api/quant/overview";

async function fetchOverview() {
  const response = await fetch(OVERVIEW_PATH);
  const payload = await readJsonResponse(response, "量化总览服务响应异常");
  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || `请求失败 · HTTP ${response.status}`);
  }
  return payload.data;
}

const PHASE_LABELS = { FOUNDATION: "基础骨架" };
const EXCHANGE_LABELS = { NOT_CONFIGURED: "未配置" };
const STORAGE_LABELS = { NOT_CREATED: "未创建" };
const MODULE_STATUS_LABELS = { SKELETON: "骨架已建立" };
const GROUP_ORDER = ["research", "trading", "operations"];
const GROUP_LABELS = { research: "研究链路", trading: "交易链路", operations: "运行管理" };

function TopStatusCard({ icon: Icon, label, value, tone }) {
  return (
    <article className={`quant-status-card ${tone ? `tone-${tone}` : ""}`}>
      <div className="quant-status-icon"><Icon weight="duotone" /></div>
      <div className="quant-status-text">
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
    </article>
  );
}

function ModuleRow({ module }) {
  const statusLabel = MODULE_STATUS_LABELS[module.status] || module.status || "未接入";
  return (
    <article className="quant-module-row">
      <div className="quant-module-info">
        <strong>{module.name}</strong>
        <small>{module.description}</small>
      </div>
      <span className="quant-module-status">{statusLabel}</span>
    </article>
  );
}

function ModuleGroup({ group, modules }) {
  const label = GROUP_LABELS[group] || group;
  return (
    <section className="quant-module-group" aria-label={label}>
      <header><h3>{label}</h3></header>
      <div className="quant-module-list">
        {modules.map((module) => <ModuleRow key={module.key} module={module} />)}
      </div>
    </section>
  );
}

function OverviewBody({ overview, state, error, load, refreshing }) {
  if (state === "loading") {
    return (
      <div className="quant-loading" role="status" aria-live="polite">
        <div className="quant-loader-dot" /><div className="quant-loader-dot" /><div className="quant-loader-dot" />
        <span>正在读取骨架总览…</span>
      </div>
    );
  }
  if (state === "error") {
    return (
      <div className="quant-error" role="alert">
        <Warning weight="fill" />
        <div>
          <strong>骨架总览加载失败</strong>
          <span>{error}</span>
        </div>
        <button type="button" className="quant-error-retry" onClick={load}>
          <ArrowsClockwise />重新加载
        </button>
      </div>
    );
  }
  const groupedModules = (overview?.modules || []).reduce((groups, module) => {
    const group = module.group || "operations";
    if (!groups[group]) groups[group] = [];
    groups[group].push(module);
    return groups;
  }, {});
  const phaseLabel = overview ? (PHASE_LABELS[overview.phase] || overview.phase || "未接入") : "—";
  const liveTradingLabel = overview ? (overview.liveTradingEnabled ? "已启用" : "未启用") : "—";
  const exchangeLabel = overview ? (EXCHANGE_LABELS[overview.exchangeState] || overview.exchangeState || "未配置") : "—";
  const storageLabel = overview ? (STORAGE_LABELS[overview.storageState] || overview.storageState || "未创建") : "—";

  return (
    <>
      <div className="quant-workspace-head">
        <div>
          <span className="eyebrow">QUANT · FOUNDATION SKELETON</span>
          <h3>骨架总览</h3>
          <small>当前阶段只展示已建立的骨架结构，不提供具体量化业务</small>
        </div>
        <button type="button" className="quant-refresh" onClick={load} disabled={refreshing}>
          <ArrowsClockwise className={refreshing ? "spin" : ""} />
          {refreshing ? "加载中" : "重新加载"}
        </button>
      </div>

      <section className="quant-status-grid" aria-label="骨架总览状态">
        <TopStatusCard icon={Sparkle} label="当前阶段" value={phaseLabel} tone="primary" />
        <TopStatusCard icon={LockKey} label="实盘交易" value={liveTradingLabel} tone="warning" />
        <TopStatusCard icon={Lightning} label="交易所" value={exchangeLabel} tone="warning" />
        <TopStatusCard icon={Database} label="数据存储" value={storageLabel} tone="warning" />
      </section>

      <section className="quant-modules" aria-label="模块地图">
        <header className="quant-section-head">
          <h4><ChartLineUp weight="duotone" />模块地图</h4>
          <small>仅展示当前已建立的骨架结构，未接入具体业务</small>
        </header>
        <div className="quant-module-grid">
          {GROUP_ORDER.map((group) => (
            <ModuleGroup key={group} group={group} modules={groupedModules[group] || []} />
          ))}
        </div>
      </section>
    </>
  );
}

export default function QuantOverview() {
  const [overview, setOverview] = useState(null);
  const [state, setState] = useState("loading");
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setState("loading");
    setError("");
    setRefreshing(true);
    try {
      const data = await fetchOverview();
      setOverview(data);
      setState("ready");
    } catch (exception) {
      setError(exception.message || "量化总览加载失败");
      setState("error");
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  return (
    <QuantPageScaffold pageClass="quant-overview-page" title="量化总览">
      <OverviewBody
        overview={overview}
        state={state}
        error={error}
        load={load}
        refreshing={refreshing}
      />
    </QuantPageScaffold>
  );
}
