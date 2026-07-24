import { useCallback, useEffect, useState } from "react";
import {
  ArrowsClockwise,
  ChartBar,
  ChartLineUp,
  Database,
  GearSix,
  Lightning,
  ListChecks,
  LockKey,
  Scroll,
  ShieldCheck,
  Sparkle,
  Stack,
  Wallet,
  Warning,
} from "@phosphor-icons/react";
import { readJsonResponse } from "./apiResponse";
import "./QuantWorkbench.css";

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

const WORKSPACES = [
  { key: "overview", label: "总览", icon: ChartBar },
  { key: "strategies", label: "策略管理", icon: Stack },
  { key: "backtests", label: "回测与参数实验", icon: ChartLineUp },
  { key: "risk", label: "风控中心", icon: ShieldCheck },
  { key: "portfolio", label: "账户与仓位", icon: Wallet },
  { key: "orders", label: "订单与成交", icon: ListChecks },
  { key: "logs", label: "运行记录", icon: Scroll },
];

// 非总览工作区只展示真实未接入状态，不提供任何功能入口、假开关或假数据。
const SKELETON_WORKSPACES = {
  strategies: {
    title: "策略管理",
    intro: "策略定义、参数版本、启停状态、信号记录",
    items: ["策略定义", "参数版本", "启停状态", "信号记录"],
    note: "尚未接入具体业务",
  },
  backtests: {
    title: "回测与参数实验",
    intro: "历史数据选择、回测任务、手续费与滑点、参数实验、结果报告",
    items: ["历史数据选择", "回测任务", "手续费与滑点", "参数实验", "结果报告"],
    note: "尚未接入具体业务 · 不展示收益、曲线或胜率",
  },
  risk: {
    title: "风控中心",
    intro: "仓位限制、杠杆限制、单笔风险、日亏熔断、连续亏损熔断、紧急停止",
    items: ["仓位限制", "杠杆限制", "单笔风险", "日亏熔断", "连续亏损熔断", "紧急停止"],
    note: "尚未接入具体业务 · 不提供开关或误操作按钮",
  },
  portfolio: {
    title: "账户与仓位",
    intro: "账户余额、可用保证金、当前仓位、已实现盈亏、未实现盈亏、资金费率",
    items: ["账户余额", "可用保证金", "当前仓位", "已实现盈亏", "未实现盈亏", "资金费率"],
    note: "尚未接入 · 不展示余额或盈亏数字",
  },
  orders: {
    title: "订单与成交",
    intro: "活跃订单、历史订单、成交记录、保护单、执行异常",
    items: ["活跃订单", "历史订单", "成交记录", "保护单", "执行异常"],
    note: "尚未接入具体业务 · 不展示订单",
  },
  logs: {
    title: "运行记录",
    intro: "策略运行日志、风控决策、对账记录、系统异常、发布版本",
    items: ["策略运行日志", "风控决策", "对账记录", "系统异常", "发布版本"],
    note: "尚未接入具体业务 · 不生成日志",
  },
};

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

function OverviewWorkspace({ overview, state, error, load, refreshing }) {
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
    <div className="quant-overview">
      <div className="quant-workspace-head">
        <div>
          <span className="eyebrow">QUANT · FOUNDATION SKELETON</span>
          <h3>总览</h3>
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
    </div>
  );
}

function SkeletonWorkspace({ config }) {
  return (
    <div className="quant-skeleton" aria-label={config.title}>
      <div className="quant-workspace-head">
        <div>
          <span className="eyebrow">QUANT · 尚未接入</span>
          <h3>{config.title}</h3>
          <small>{config.intro}</small>
        </div>
      </div>
      <section className="quant-cap-grid" aria-label={`${config.title}能力结构`}>
        {config.items.map((item) => (
          <article key={item} className="quant-cap-row">
            <span className="quant-cap-name">{item}</span>
            <span className="quant-cap-status">未接入</span>
          </article>
        ))}
      </section>
      <p className="quant-skeleton-note">{config.note}</p>
    </div>
  );
}

export default function QuantWorkbench() {
  const [active, setActive] = useState("overview");
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
    <section className="quant-workbench" aria-label="量化交易工作区">
      <header className="quant-hero">
        <div className="quant-hero-icon"><Sparkle weight="duotone" /></div>
        <div className="quant-hero-text">
          <span>QUANT · FOUNDATION SKELETON</span>
          <h2>量化交易工作区</h2>
          <p>策略研究、回测、风控与实盘运行总览。当前阶段只展示已建立的骨架结构，不提供具体量化业务。</p>
        </div>
      </header>

      <nav className="quant-tabs" aria-label="量化交易内部工作区">
        {WORKSPACES.map((ws) => {
          const Icon = ws.icon;
          const selected = active === ws.key;
          return (
            <button
              type="button"
              key={ws.key}
              className={selected ? "quant-tab active" : "quant-tab"}
              aria-current={selected ? "page" : undefined}
              onClick={() => setActive(ws.key)}
            >
              <Icon weight={selected ? "duotone" : "regular"} />
              {ws.label}
            </button>
          );
        })}
      </nav>

      <div className="quant-panel-area">
        {active === "overview" && (
          <OverviewWorkspace
            overview={overview}
            state={state}
            error={error}
            load={load}
            refreshing={refreshing}
          />
        )}
        {active !== "overview" && (
          <SkeletonWorkspace config={SKELETON_WORKSPACES[active]} />
        )}
      </div>
    </section>
  );
}
