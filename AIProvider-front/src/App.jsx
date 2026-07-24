import { useEffect, useMemo, useRef, useState } from "react";
import {
  Waveform,
  Broadcast,
  Camera,
  ChartLineUp,
  ChatCircle,
  CheckCircle,
  Clock,
  Database,
  Desktop,
  Heart,
  House,
  Link,
  Robot,
  Sparkle,
  Warning,
  Wrench,
  X,
  ArrowsClockwise,
  VideoCamera,
  Stop,
  Brain,
  CaretRight,
  Bell,
  Pulse,
  CameraRotate,
  User,
  CirclesFour,
  ChatsTeardrop,
  Monitor,
  Notebook,
  MicrophoneStage,
  FilmSlate,
  Stack,
  Table,
  Rows,
  CaretLeft,
  ImageSquare,
  Power,
  Play,
  GearSix,
  SlidersHorizontal,
  Palette,
  XLogo,
  Cat,
  Star,
  PawPrint,
  LockKey,
  PaintBrush,
  Cube,
  ChatsCircle,
  FolderSimple,
  IdentificationCard,
  ChartBar,
  Flask,
  ListChecks,
  Scroll,
  ShieldCheck,
  Wallet,
} from "@phosphor-icons/react";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { motion } from "motion/react";
import ComfyConsole from "./ComfyLocalWorkbench";
import MonitorCenter from "./MonitorCenter";
import PromptManager from "./PromptManager";
import PromptOptionManager from "./PromptOptionManager";
import UiControl from "./UiControl";
import TwitterPublisher from "./TwitterPublisher";
import ContentOperationsCenter from "./ContentOperationsCenter";
import CuteHomeBackground from "./CuteHomeBackground";
import ManualImageEditor from "./ManualImageEditor";
import VideoEditor from "./VideoEditor";
import FoundryWorkbench from "./FoundryWorkbench";
import CryptoMarket from "./CryptoMarket";
import QuantOverview from "./quant/QuantOverview";
import QuantStrategies from "./quant/QuantStrategies";
import QuantBacktests from "./quant/QuantBacktests";
import QuantRisk from "./quant/QuantRisk";
import QuantPortfolio from "./quant/QuantPortfolio";
import QuantOrders from "./quant/QuantOrders";
import QuantLogs from "./quant/QuantLogs";
import RemoteCodex from "./RemoteCodex";
import FileTransfer from "./FileTransfer";
import FavoriteMediaLibrary from "./FavoriteMediaLibrary";
import PlatformAccountCenter from "./PlatformAccountCenter";
import AsrRecords from "./AsrRecords";
import { RELEASE_VERSION } from "./releaseVersion";
import { readJsonResponse } from "./apiResponse";
import "./App.css";
import "./CodexTheme.css";
import "./SemanticTheme.css";
import "./KawaiiUi.css";
import "./DesktopShell.css";

const API = "/api";
const NAV = [
  { key: "favorites", label: "我的最爱", icon: Star, group: "create", color: "#ff8fbe" },
  { key: "workshop", label: "图像工坊", icon: ImageSquare, group: "create", color: "#c69cff" },
  { key: "prompts", label: "Prompt 管理", icon: SlidersHorizontal, group: "create", color: "#f0a860" },
  { key: "manualEditor", label: "图片编辑", icon: PaintBrush, group: "create", color: "#6fe2df" },
  { key: "videoEditor", label: "视频编辑", icon: FilmSlate, group: "create", color: "#82b7ff" },
  { key: "maid", label: "我的女仆", icon: Heart, group: "create", color: "#ff718f" },
  { key: "monitor", label: "监控中心", icon: Pulse, group: "operate", color: "#ff6b6b" },
  { key: "asrRecords", label: "语音识别", icon: MicrophoneStage, group: "operate", color: "#82b7ff" },
  { key: "remoteCodex", label: "远程 Codex", icon: ChatsCircle, group: "operate", color: "#a78bfa" },
  { key: "foundry", label: "链上工具", icon: Cube, group: "operate", color: "#f59e0b" },
  { key: "fileTransfer", label: "文件中转", icon: FolderSimple, group: "operate", color: "#60a5fa" },
  { key: "camera", label: "手机监控", icon: VideoCamera, closed: true, hidden: true, group: "operate" },
  { key: "quantOverview", label: "量化总览", icon: ChartBar, group: "quant", color: "#a78bfa" },
  { key: "market", label: "市场行情", icon: ChartLineUp, group: "quant", color: "#72ddb1" },
  { key: "quantStrategies", label: "策略管理", icon: Stack, group: "quant", color: "#c69cff" },
  { key: "quantBacktests", label: "回测实验", icon: Flask, group: "quant", color: "#f0a860" },
  { key: "quantRisk", label: "风控中心", icon: ShieldCheck, group: "quant", color: "#ff6b6b" },
  { key: "quantPortfolio", label: "账户仓位", icon: Wallet, group: "quant", color: "#34d399" },
  { key: "quantOrders", label: "订单成交", icon: ListChecks, group: "quant", color: "#38bdf8" },
  { key: "quantLogs", label: "运行记录", icon: Scroll, group: "quant", color: "#94a3b8" },
  { key: "twitter", label: "Twitter 发布", icon: XLogo, group: "publish", color: "#38bdf8" },
  { key: "contentOperations", label: "内容运营", icon: Broadcast, group: "publish", color: "#fb923c" },
  { key: "accounts", label: "账号中心", icon: IdentificationCard, group: "publish", color: "#34d399" },
  { key: "appearance", label: "UI 控制", icon: Palette, group: "system", color: "#e879f9" },
  { key: "settings", label: "系统设置", icon: GearSix, group: "system", color: "#94a3b8" },
];
const NAV_GROUPS = [
  { key: "create", label: "创作" },
  { key: "operate", label: "运营与工具" },
  { key: "quant", label: "量化" },
  { key: "publish", label: "发布" },
  { key: "system", label: "系统" },
];
const PAGE_DESCRIPTIONS = {
  favorites: "收藏并管理保存在服务器上的媒体原件",
  manualEditor: "本机画布、抠图与 AI 修补",
  videoEditor: "素材、画布与时间线编辑",
  market: "实时行情、K 线与订单簿",
  quantOverview: "量化系统阶段、连接状态与模块运行总览",
  quantStrategies: "策略定义、参数版本、启停状态与信号记录",
  quantBacktests: "历史回放、撮合假设、参数实验与结果报告",
  quantRisk: "仓位、杠杆、亏损熔断与交易安全控制",
  quantPortfolio: "账户余额、保证金、仓位与盈亏状态",
  quantOrders: "活跃订单、历史订单、成交与执行异常",
  quantLogs: "策略、风控、对账与系统运行记录",
  prompts: "管理可复用的标签式与长文式提示词方案",
  promptOptions: "维护 Prompt 词条与分类规则",
  maid: "查看角色状态与模型活动",
  monitor: "服务健康、资源、网络与费用",
  asrRecords: "播放录音、核对识别结果并保存人工修正",
  remoteCodex: "连接远程 Codex 并管理对话",
  foundry: "Foundry 工具与链上只读查询",
  fileTransfer: "个人设备之间上传、下载和删除文件",
  twitter: "账号连接、内容编辑与发布任务",
  contentOperations: "采集、判断、发布与自动化运营",
  accounts: "集中保存平台登录信息与 API 凭据",
  appearance: "统一管理全站主题与组件外观",
  settings: "本机工作流、输出与迁移目录",
};
const VISIBLE_NAV = NAV.filter((item) => !item.hidden);
const MOBILE_NAV = [{ key: "home", label: "首页", icon: House }, ...VISIBLE_NAV];

const fmt = (value) => Number(value || 0).toLocaleString("zh-CN");
const compact = (value) =>
  new Intl.NumberFormat("zh-CN", {
    notation: "compact",
    maximumFractionDigits: 2,
  }).format(Number(value || 0));
const formatTime = (seconds) => {
  const s = Number(seconds || 0),
    h = Math.floor(s / 3600),
    m = Math.floor((s % 3600) / 60);
  return h ? `${h}h ${m}m` : m ? `${m}m` : `${Math.floor(s)}s`;
};
const timeAgo = (date) => {
  if (!date) return "刚刚";
  const delta = Math.max(0, Date.now() - new Date(date).getTime()) / 60000;
  if (delta < 1) return "刚刚";
  if (delta < 60) return `${Math.floor(delta)} 分钟前`;
  if (delta < 1440) return `${Math.floor(delta / 60)} 小时前`;
  return new Date(date).toLocaleDateString("zh-CN", {
    month: "short",
    day: "numeric",
  });
};
const chartTip = {
  background: "#252938",
  border: "1px solid #41495F",
  borderRadius: 12,
  color: "#F4F2FF",
  fontSize: 12,
};

async function get(path) {
  const response = await fetch(`${API}${path}`);
  if (!response.ok) throw new Error(`请求失败 · ${response.status}`);
  const result = await readJsonResponse(response, "工作区服务响应异常");
  if (result.code !== 200) throw new Error(result.message || "请求失败");
  return result.data;
}

function useDashboardData() {
  const [data, setData] = useState({
    overview: {},
    llm: [],
    models: [],
    chats: [],
    calls: [],
    time: [],
    tools: [],
    apps: [],
    broadcasts: [],
    chatStats: {},
    aiOverview: {},
    providerState: {},
    sync: { recentRuns: [] },
    insights: {
      counts: {},
      reminders: [],
      notes: [],
      voice: [],
      videos: [],
      remoteVideos: [],
      runtime: {},
    },
  });
  const [state, setState] = useState("loading");
  const [error, setError] = useState("");
  const load = async () => {
    setState("loading");
    setError("");
    try {
      const [
        overview,
        llm,
        models,
        chats,
        calls,
        time,
        tools,
        apps,
        broadcasts,
        chatStats,
        aiOverview,
        providerState,
        sync,
        insights,
      ] = await Promise.all([
        get("/dashboard/overview"),
        get("/dashboard/llm-usage-daily?days=30"),
        get("/dashboard/llm-model-stats"),
        get("/dashboard/recent-chats?limit=40"),
        get("/dashboard/recent-llm-calls?limit=30"),
        get("/dashboard/time-tracking-daily?days=30"),
        get("/dashboard/agent-tool-usage"),
        get("/dashboard/desktop-app-usage"),
        get("/dashboard/broadcast-stats"),
        get("/dashboard/chat-stats"),
        get("/monitor/ai-overview").catch(() => ({})),
        get("/monitor/providers").catch(() => ({})),
        get("/sync/status").catch(() => ({ recentRuns: [] })),
        get("/insights/command"),
      ]);
      setData({
        overview,
        llm,
        models,
        chats,
        calls,
        time,
        tools,
        apps,
        broadcasts,
        chatStats,
        aiOverview,
        providerState,
        sync,
        insights,
      });
      setState("ready");
    } catch (e) {
      setError(e.message);
      setState("error");
    }
  };
  useEffect(() => {
    load();
  }, []);
  return { ...data, state, error, reload: load };
}

function App() {
  const viewFromPath = () => ({ "/favorites": "favorites", "/workshop": "workshop", "/manual-editor": "manualEditor", "/video-editor": "videoEditor", "/market": "market", "/quant": "quantOverview", "/quant/strategies": "quantStrategies", "/quant/backtests": "quantBacktests", "/quant/risk": "quantRisk", "/quant/portfolio": "quantPortfolio", "/quant/orders": "quantOrders", "/quant/logs": "quantLogs", "/prompts": "prompts", "/prompt-options": "promptOptions", "/maid": "maid", "/admin/monitor": "monitor", "/admin/asr": "asrRecords", "/remote-codex": "remoteCodex", "/foundry": "foundry", "/file-transfer": "fileTransfer", "/camera": "camera", "/twitter": "twitter", "/content-operations": "contentOperations", "/accounts": "accounts", "/appearance": "appearance", "/settings": "settings" })[window.location.pathname] || "home";
  const [view, setView] = useState(viewFromPath);
  const [workshopMounted, setWorkshopMounted] = useState(() => viewFromPath() === "workshop");
  const [promptOptionCategory, setPromptOptionCategory] = useState("");
  const dashboard = useDashboardData();
  const current = NAV.find((item) => item.key === (view === "promptOptions" ? "prompts" : view));
  useEffect(() => {
    const path = ({ favorites: "/favorites", workshop: "/workshop", manualEditor: "/manual-editor", videoEditor: "/video-editor", market: "/market", quantOverview: "/quant", quantStrategies: "/quant/strategies", quantBacktests: "/quant/backtests", quantRisk: "/quant/risk", quantPortfolio: "/quant/portfolio", quantOrders: "/quant/orders", quantLogs: "/quant/logs", prompts: "/prompts", promptOptions: "/prompt-options", maid: "/maid", monitor: "/admin/monitor", asrRecords: "/admin/asr", remoteCodex: "/remote-codex", foundry: "/foundry", fileTransfer: "/file-transfer", camera: "/camera", twitter: "/twitter", contentOperations: "/content-operations", accounts: "/accounts", appearance: "/appearance", settings: "/settings" })[view] || "/";
    if (window.location.pathname !== path) window.history.replaceState({}, "", path);
  }, [view]);
  useEffect(() => {
    if (view === "workshop") setWorkshopMounted(true);
  }, [view]);
  useEffect(() => {
    const onPop = () => setView(viewFromPath());
    window.addEventListener("popstate", onPop); return () => window.removeEventListener("popstate", onPop);
  }, []);
  return (
    <div className="neural-shell shell-expanded">
      <aside className="rail rail-expanded">
        <button
          type="button"
          className={view === "home" ? "rail-brand active" : "rail-brand"}
          onClick={() => setView("home")}
          aria-label="首页"
          aria-current={view === "home" ? "page" : undefined}
          title="首页"
        >
          <span className="rail-mascot"><i className="mascot-ear ear-left" /><i className="mascot-ear ear-right" /><Cat weight="fill" /><b>•ᴗ•</b><em>✦</em></span>
          <span className="rail-brand-label">MAID</span>
        </button>
        <nav aria-label="一级工作区">
          {NAV_GROUPS.map((group) => (
            <section className="rail-group" key={group.key} aria-label={group.label}>
              <h2>{group.label}</h2>
              {VISIBLE_NAV.filter((item) => item.group === group.key).map((item) => (
                <NavButton
                  key={item.key}
                  item={item}
                  active={view === item.key || (view === "promptOptions" && item.key === "prompts")}
                  onClick={() => setView(item.key)}
                />
              ))}
            </section>
          ))}
        </nav>
        <div className="rail-release" aria-label={`前端版本 ${RELEASE_VERSION.frontend}，后端版本 ${RELEASE_VERSION.backend}`}>
          <span><b>前端</b>{RELEASE_VERSION.frontend}</span>
          <span><b>后端</b>{RELEASE_VERSION.backend}</span>
        </div>
      </aside>
      <header className="mobile-head">
        <div className="mobile-logo">
          <Cat weight="fill" /> Kawaii Maid
        </div>
        <span className="live-copy">
          <i /> LIVE
        </span>
      </header>
      <main className={`workspace workspace-${view} workspace-expanded-shell`}>
        {view === "workshop" && <KawaiiPageAtmosphere soft />}
        {view !== "home" && current && (
          <div className="section-head">
            <div>
              <span className="eyebrow">AI MAID · WORKSPACE</span>
              <h1>{current.label}</h1>
              {PAGE_DESCRIPTIONS[view] && <p>{PAGE_DESCRIPTIONS[view]}</p>}
            </div>
            <SystemClock />
          </div>
        )}
        {view === "home" && <HomeView data={dashboard} onOpenWorkshop={() => setView("workshop")} />}
        {view === "favorites" && <FavoriteMediaLibrary />}
        {workshopMounted && <div className={`tool-home compact-home persistent-workshop ${view === "workshop" ? "" : "persistent-view-hidden"}`} aria-hidden={view !== "workshop"}>
          <ComfyConsole embedded active={view === "workshop"} />
        </div>}
        {view === "manualEditor" && <ManualImageEditor />}
        {view === "videoEditor" && <VideoEditor />}
        {view === "market" && <CryptoMarket />}
        {view === "quantOverview" && <QuantOverview />}
        {view === "quantStrategies" && <QuantStrategies />}
        {view === "quantBacktests" && <QuantBacktests />}
        {view === "quantRisk" && <QuantRisk />}
        {view === "quantPortfolio" && <QuantPortfolio />}
        {view === "quantOrders" && <QuantOrders />}
        {view === "quantLogs" && <QuantLogs />}
        {view === "prompts" && <PromptManager onEditOptions={(category = "") => { setPromptOptionCategory(category); setView("promptOptions"); }} />}
        {view === "promptOptions" && <PromptOptionManager initialCategory={promptOptionCategory} onBack={() => setView("prompts")} />}
        {view === "maid" &&
          (dashboard.state === "loading" ? (
            <LoadingState />
          ) : dashboard.state === "error" ? (
            <ErrorState message={dashboard.error} retry={dashboard.reload} />
          ) : (
            <MaidView data={dashboard} />
          ))}
        {view === "camera" && <SealedFeature title="手机监控" message="这片频道正在休眠，暂时不对外开放。" />}
        {view === "monitor" && <MonitorCenter />}
        {view === "asrRecords" && <AsrRecords />}
        {view === "remoteCodex" && <RemoteCodex />}
        {view === "foundry" && <FoundryWorkbench />}
        {view === "fileTransfer" && <FileTransfer />}
        {view === "twitter" && <TwitterPublisher />}
        {view === "contentOperations" && <ContentOperationsCenter />}
        {view === "accounts" && <PlatformAccountCenter />}
        {view === "appearance" && <UiControl />}
        {view === "settings" && <div className="tool-home system-settings-view"><ComfyConsole mode="settings" /></div>}
      </main>
      <nav className="bottom-nav">
        {MOBILE_NAV.map((item) => (
          <NavButton
            key={item.key}
            item={item}
             active={view === item.key}
             onClick={() => setView(item.key)}
             mobile
          />
        ))}
      </nav>
    </div>
  );
}

function NavButton({ item, active, onClick, mobile = false }) {
  const Icon = item.icon;
  const buttonRef = useRef(null);
  useEffect(() => {
    if (mobile && active) buttonRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "center" });
  }, [active, mobile]);
  return (
    <button
      ref={buttonRef}
      className={active ? "nav-button active" : "nav-button"}
      disabled={item.closed}
      data-nav-key={item.key}
      onClick={item.closed ? undefined : onClick}
      title={item.closed ? `${item.label} · 暂未开放` : item.label}
      aria-current={active ? "page" : undefined}
      style={item.color ? { "--nav-accent": item.color } : undefined}
    >
      <Icon size={22} weight={active ? "duotone" : "regular"} color={item.color} />
      <span>{item.label}</span>
      {item.closed && <i className="nav-closed-dot">休</i>}
    </button>
  );
}

function SystemClock() {
  const [now, setNow] = useState(new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  return (
    <div className="system-clock">
      <span>
        {now.toLocaleDateString("zh-CN", {
          month: "long",
          day: "numeric",
          weekday: "short",
        })}
      </span>
      <strong>{now.toLocaleTimeString("zh-CN", { hour12: false })}</strong>
    </div>
  );
}

/* ========== 首页：纯大图 ========== */
function HomeView({ data, onOpenWorkshop }) {
  return (
    <motion.div className="home-launcher" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: .55 }}>
      <CuteHomeBackground onOpenWorkshop={onOpenWorkshop} />
    </motion.div>
  );
}

/* ========== 我的女仆：聚合视图 ========== */
function MaidView({ data }) {
  const [insights, setInsights] = useState(data.insights || {});
  const [insightsError, setInsightsError] = useState("");
  const runtime = insights.runtime || {};
  const roles = insights.voiceRoles || [];
  const currentMaid = insights.currentMaid || {};
  const explicitCurrentRoleId = insights.currentRoleId ?? currentMaid.MaidId ?? currentMaid.maidId ?? runtime.LastRole ?? runtime.lastRole ?? "";
  const currentRoleId = explicitCurrentRoleId || roles[0]?.RoleId || roles[0]?.roleId || "";
  const [selectedRoleId, setSelectedRoleId] = useState(currentRoleId);
  const [roleData, setRoleData] = useState({ state: {}, card: {}, summary: {}, daily: [], recentCalls: [], businesses: [] });
  const [roleLoading, setRoleLoading] = useState(false);
  const [roleError, setRoleError] = useState("");
  const [avatarFailed, setAvatarFailed] = useState(false);
  const previousCurrentRoleId = useRef(currentRoleId);

  useEffect(() => {
    setInsights(data.insights || {});
  }, [data.insights]);
  useEffect(() => {
    let cancelled = false;
    const refreshInsights = () => {
      get("/insights/command")
        .then((result) => {
          if (!cancelled) {
            setInsights(result);
            setInsightsError("");
          }
        })
        .catch((exception) => {
          if (!cancelled) setInsightsError(exception.message || "女仆总览数据刷新失败");
        });
    };
    refreshInsights();
    const timer = window.setInterval(refreshInsights, 30_000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);
  useEffect(() => {
    const previous = previousCurrentRoleId.current;
    setSelectedRoleId((selected) => {
      if (!selected || String(selected).toLowerCase() === String(previous).toLowerCase()) return currentRoleId;
      return selected;
    });
    previousCurrentRoleId.current = currentRoleId;
  }, [currentRoleId]);
  useEffect(() => {
    if (!selectedRoleId) return;
    let cancelled = false;
    setRoleData({ state: {}, card: {}, summary: {}, daily: [], recentCalls: [], businesses: [] });
    setRoleLoading(true);
    setRoleError("");
    const refreshRole = () => {
      get(`/insights/maid-role?roleId=${encodeURIComponent(selectedRoleId)}`)
        .then((result) => {
          if (!cancelled) {
            setRoleData(result);
            setRoleError("");
          }
        })
        .catch((exception) => {
          if (!cancelled) setRoleError(exception.message || "角色数据加载失败");
        })
        .finally(() => {
          if (!cancelled) setRoleLoading(false);
        });
    };
    refreshRole();
    const timer = window.setInterval(refreshRole, 30_000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [selectedRoleId]);
  useEffect(() => setAvatarFailed(false), [selectedRoleId]);

  const selectedRole = roles.find((role) => String(role.RoleId ?? role.roleId).toLowerCase() === String(selectedRoleId).toLowerCase()) || {};
  const card = roleData.card || {};
  const selectedName = card.Name ?? card.name ?? selectedRole.DisplayName ?? selectedRole.displayName ?? roleData.state?.Name ?? roleData.state?.name ?? selectedRoleId;
  const avatarUrl = selectedRole.avatarUrl;
  const state = roleData.state || {};
  const summary = roleData.summary || {};
  const trend = roleData.daily || [];
  const recentCalls = roleData.recentCalls || [];
  const latestCall = recentCalls[0] || {};
  const roleUpdatedAt = card.UpdatedAt ?? card.updatedAt ?? state.UpdatedAt ?? state.updatedAt ?? currentMaid.UpdatedAt ?? currentMaid.updatedAt ?? runtime.UpdatedAt ?? runtime.updatedAt;
  const templateStatus = card.TemplateCardGenerationStatus ?? card.templateCardGenerationStatus;
  const templateStatusLabel = ({ ready: "模板已就绪", generating: "模板生成中", failed: "模板生成失败", missing: "模板未生成" })[templateStatus] || "暂无模板状态";
  const cardDescription = card.CardSummary ?? card.cardSummary ?? card.RoleTitle ?? card.roleTitle;
  const isCurrentRole = String(selectedRoleId).toLowerCase() === String(currentRoleId).toLowerCase();
  const selectedRoleIndex = Math.max(0, roles.findIndex((role) => String(role.RoleId ?? role.roleId).toLowerCase() === String(selectedRoleId).toLowerCase()));
  const selectRelativeRole = (offset) => {
    if (!roles.length) return;
    const nextRole = roles[(selectedRoleIndex + offset + roles.length) % roles.length];
    setSelectedRoleId(nextRole.RoleId ?? nextRole.roleId);
  };

  return (
    <div className="maid-compact-page kawaii-maid-space">
      <div className="maid-kawaii-banner" aria-hidden="true"><PawPrint weight="fill" /><span>MY DEAREST MAID</span><Heart weight="fill" /><b>いつもそばにいるよ</b><Sparkle weight="fill" /></div>
      {insightsError && <div className="maid-role-error" role="alert"><Warning weight="fill" />{insightsError}</div>}
      {roleError && <div className="maid-role-error" role="alert"><Warning weight="fill" />角色数据未能按现役数据库结构加载：{roleError}</div>}
      <section className="maid-compact-hero">
        <div className="maid-avatar-stage">
          <button className="maid-carousel-button" onClick={() => selectRelativeRole(-1)} disabled={roles.length < 2} aria-label="上一个角色"><CaretLeft /></button>
          <div className="portrait-core">
            <div className="orbit orbit-a" />
            <div className="orbit orbit-b" />
            <div className="orbit orbit-c" />
            {avatarUrl && !avatarFailed
              ? <img src={avatarUrl} alt={selectedName || "角色头像"} onError={() => setAvatarFailed(true)} />
              : <div className="maid-avatar-placeholder"><User /></div>}
            <div className="pulse-line" />
          </div>
          <button className="maid-carousel-button" onClick={() => selectRelativeRole(1)} disabled={roles.length < 2} aria-label="下一个角色"><CaretRight /></button>
        </div>
        <div className="maid-current-role">
          <span className="live-copy"><i /> {isCurrentRole ? (explicitCurrentRoleId ? "AMA 当前角色" : "默认展示角色") : "角色数据查看"}</span>
          <h1>{selectedName || "未同步角色"}</h1>
          <p>{cardDescription || (isCurrentRole ? (explicitCurrentRoleId ? "这是 AMA 当前正在使用的角色。" : "AMA 尚未指定当前角色，网页先展示角色列表中的第一位。") : "当前只切换网页查看的数据，不会修改 AMA 正在使用的角色。")}</p>
          <label className="maid-role-select"><span>直接选择角色</span><select aria-label="直接选择角色" value={selectedRoleId} onChange={(event) => setSelectedRoleId(event.target.value)} disabled={!roles.length}>{roles.map((role) => { const id = role.RoleId ?? role.roleId; const name = role.DisplayName ?? role.displayName ?? id; return <option key={id} value={id}>{name}{String(id).toLowerCase() === String(currentRoleId).toLowerCase() ? " · 当前" : ""}</option>; })}</select><small>{roles.length ? `${selectedRoleIndex + 1} / ${roles.length}` : "暂无角色"}</small></label>
          <div className="role-freshness">
            <span>{roleLoading ? "正在加载角色数据" : "角色更新时间"}</span>
            <strong>{roleLoading ? "…" : roleUpdatedAt ? timeAgo(roleUpdatedAt) : "暂无时间"}</strong>
          </div>
        </div>
      </section>

      {!roleError && <section className="maid-stat-grid compact-stats">
        <StatCard icon={Brain} label="LLM 调用" value={fmt(summary.LlmCallCount ?? summary.llmCallCount)} sub="全部现役业务" tone="violet" />
        <StatCard icon={Database} label="Token" value={compact(summary.TotalTokens ?? summary.totalTokens)} sub="全部现役业务" tone="cyan" />
        <StatCard icon={Pulse} label="主动判断" value={fmt(summary.ProactiveDecisionCount ?? summary.proactiveDecisionCount)} sub="该角色累计" tone="cyan" />
        <StatCard icon={ChatCircle} label="主动回应" value={fmt(summary.ProactiveResponseCount ?? summary.proactiveResponseCount)} sub="已决定回应" tone="coral" />
        <StatCard icon={Waveform} label="语音播放" value={fmt(summary.VoicePlayCount ?? summary.voicePlayCount)} sub="实际播放成功" tone="coral" />
        <StatCard icon={ArrowsClockwise} label="角色卡迭代" value={fmt(card.TemplateCardIterationCount ?? card.templateCardIterationCount)} sub="当前模板版本" tone="amber" />
      </section>}

      {!roleError && <section className="maid-ai-runtime">
        <div><span>最近 Provider</span><strong>{latestCall.Provider ?? latestCall.provider ?? "暂无记录"}</strong></div>
        <div><span>最近模型</span><strong>{latestCall.Model ?? latestCall.model ?? "暂无记录"}</strong></div>
        <div><span>最近业务</span><strong>{latestCall.SourceName ?? latestCall.sourceName ?? "暂无记录"}</strong></div>
        <div><span>输入 Token</span><strong>{fmt(summary.InputTokens ?? summary.inputTokens)}</strong></div>
        <div><span>输出 Token</span><strong>{fmt(summary.OutputTokens ?? summary.outputTokens)}</strong></div>
        <div><span>角色卡</span><strong>{templateStatusLabel}</strong></div>
      </section>}

      {!roleError && <section className="maid-compact-lower">
        <div className="maid-panel maid-compact-chart">
          <PanelHeader title="近 14 天 AI 活动" subtitle="真实 LLM 调用次数" />
          {trend.length ? (
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={trend} margin={{ left: -20, right: 8, top: 12, bottom: 0 }}>
                <defs>
                  <linearGradient id="maidCompactArea" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" stopColor="#9B8AFB" stopOpacity=".18" />
                    <stop offset="1" stopColor="#9B8AFB" stopOpacity="0" />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#34394A" strokeDasharray="2 8" vertical={false} />
                <XAxis dataKey="day" tickFormatter={(value) => String(value).slice(5)} tick={{ fill: "#7F879C", fontSize: 10 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: "#7F879C", fontSize: 10 }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={chartTip} />
                <Area type="monotone" dataKey="callCount" stroke="#9B8AFB" strokeWidth={2} fill="url(#maidCompactArea)" dot={{ fill: "#F4F2FF", stroke: "#9B8AFB", strokeWidth: 2, r: 3 }} activeDot={{ fill: "#F4F2FF", stroke: "#9B8AFB", strokeWidth: 3, r: 5 }} name="调用次数" />
              </AreaChart>
            </ResponsiveContainer>
          ) : <EmptyMini />}
        </div>
        <div className="maid-panel maid-recent-calls">
          <PanelHeader title="最近模型调用" subtitle="最新 5 条真实记录" />
          {recentCalls.length ? recentCalls.map((call, index) => (
            <article key={call.Id ?? call.id ?? index}>
              <div><strong>{call.SourceName ?? call.sourceName ?? call.Source ?? call.source ?? "未知业务"}</strong><span>{call.Model ?? call.model ?? "未知模型"} · {call.Provider ?? call.provider ?? "未知提供方"}</span></div>
              <b>{fmt(call.TotalTokens ?? call.totalTokens)} tokens</b>
              <time>{timeAgo(call.CreatedAt ?? call.createdAt)}</time>
            </article>
          )) : <EmptyMini />}
        </div>
      </section>}
    </div>
  );
}

function KawaiiPageAtmosphere({ soft = false }) {
  return <div className={`kawaii-page-atmosphere ${soft ? "soft" : ""}`} aria-hidden="true">
    <i className="page-aurora aurora-a" /><i className="page-aurora aurora-b" />
    <span className="page-charm charm-one"><Heart weight="fill" /></span>
    <span className="page-charm charm-two"><Star weight="fill" /></span>
    <span className="page-charm charm-three"><PawPrint weight="fill" /></span>
    <div className="page-spark-trail">✦　·　♡　·　✧</div>
  </div>;
}

function SealedFeature({ title, message }) {
  return <div className="sealed-feature">
    <div className="sealed-orbit"><Star weight="fill" /><Heart weight="fill" /></div>
    <div className="sealed-mascot"><Cat weight="duotone" /><LockKey weight="fill" /><i>z Z</i></div>
    <span>PASTEL AREA · SLEEPING</span><h2>{title}暂时休息中</h2><p>{message}</p><small>♡ 等它准备好后再回来看看吧 ♡</small>
  </div>;
}

function PanelHeader({ title, subtitle }) {
  return (
    <div className="panel-heading">
      <div>
        <h2>{title}</h2>
        <span>{subtitle}</span>
      </div>
      <span className="panel-live">
        <i /> 30 秒刷新
      </span>
    </div>
  );
}

function StatCard({ icon: Icon, label, value, sub, tone }) {
  return (
    <div className={`stat-card ${tone}`}>
      <Icon size={28} weight="duotone" />
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
        {sub && <small>{sub}</small>}
      </div>
    </div>
  );
}

function Progress({ label, value, suffix, violet }) {
  return (
    <div className="progress-stat">
      <div>
        <span>{label}</span>
        <strong>{suffix}</strong>
      </div>
      <div className={violet ? "progress violet" : "progress"}>
        <i style={{ width: `${Math.min(100, value)}%` }} />
      </div>
    </div>
  );
}

function BusinessMatrix({ insights = {} }) {
  const counts = insights.counts || {},
    runtime = insights.runtime || {};
  const modules = [
    {
      icon: Notebook,
      label: "知识笔记",
      value: fmt(counts.NotebookNotes),
      meta: insights.notes?.[0]?.Title || "等待新的记录",
      tone: "violet",
    },
    {
      icon: Bell,
      label: "提醒计划",
      value: fmt(counts.Reminders),
      meta: insights.reminders?.[0]?.Title || "暂无待办提醒",
      tone: "coral",
    },
    {
      icon: MicrophoneStage,
      label: "语音互动",
      value: fmt(counts.VoiceTriggerLogs),
      meta: runtime.TtsStatus || "TTS 状态待同步",
      tone: "cyan",
    },
    {
      icon: FilmSlate,
      label: "视频收藏",
      value: fmt(
        Number(counts.VideoItems || 0) + Number(counts.RemoteVideoItems || 0),
      ),
      meta:
        insights.videos?.[0]?.Title ||
        insights.remoteVideos?.[0]?.Title ||
        "暂无最近播放",
      tone: "blue",
    },
  ];
  return (
    <section className="business-matrix">
      <div className="business-title">
        <div>
          <span>AI MAID BUSINESS MATRIX</span>
          <h2>业务感知</h2>
        </div>
        <div className="runtime-chips">
          <span>LLM {runtime.OllamaStatus || "—"}</span>
          <span>TTS {runtime.TtsStatus || "—"}</span>
          <span>
            {runtime.LastLlmLatencyMs
              ? `${runtime.LastLlmLatencyMs}ms`
              : "延迟待同步"}
          </span>
        </div>
      </div>
      <div className="business-modules">
        {modules.map((item) => {
          const Icon = item.icon;
          return (
            <article key={item.label} className={item.tone}>
              <Icon size={25} weight="duotone" />
              <div>
                <span>{item.label}</span>
                <strong>{item.value}</strong>
                <small>{item.meta}</small>
              </div>
              <CaretRight />
            </article>
          );
        })}
      </div>
    </section>
  );
}

/* ========== 搜索 ========== */
function CameraMonitor() {
  const [role, setRole] = useState("viewer"),
    [room, setRoom] = useState("aimaid-private"),
    [status, setStatus] = useState("等待连接"),
    [facing, setFacing] = useState("environment");
  const local = useRef(null),
    remote = useRef(null),
    socket = useRef(null),
    peer = useRef(null),
    stream = useRef(null);
  const send = (data) =>
    socket.current?.readyState === WebSocket.OPEN &&
    socket.current.send(JSON.stringify(data));
  const createPeer = () => {
    peer.current?.close();
    const pc = new RTCPeerConnection({
      iceServers: [{ urls: "stun:stun.cloudflare.com:3478" }],
    });
    pc.onicecandidate = (e) =>
      e.candidate && send({ type: "ice", candidate: e.candidate });
    pc.ontrack = (e) => {
      remote.current.srcObject = e.streams[0];
      setStatus("直播中");
    };
    pc.onconnectionstatechange = () =>
      setStatus(
        {
          connected: "直播中",
          failed: "连接失败",
          disconnected: "连接中断",
          closed: "已停止",
        }[pc.connectionState] || "正在建立连接",
      );
    stream.current?.getTracks().forEach((t) => pc.addTrack(t, stream.current));
    peer.current = pc;
    return pc;
  };
  const connect = () => {
    socket.current?.close();
    const ws = new WebSocket(
      `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws/signal`,
    );
    socket.current = ws;
    setStatus("连接信令中");
    ws.onopen = () => send({ type: "join", room });
    ws.onclose = () => setStatus("信令已断开");
    ws.onmessage = async (e) => {
      const m = JSON.parse(e.data);
      if (m.type === "joined") setStatus(m.peers ? "对端已在线" : "等待对端");
      if (m.type === "peer-joined" && role === "publisher" && stream.current) {
        const pc = createPeer(),
          offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        send({ type: "offer", sdp: offer });
      }
      if (m.type === "offer" && role === "viewer") {
        const pc = createPeer();
        await pc.setRemoteDescription(m.sdp);
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        send({ type: "answer", sdp: answer });
      }
      if (m.type === "answer") await peer.current?.setRemoteDescription(m.sdp);
      if (m.type === "ice" && peer.current)
        try {
          await peer.current.addIceCandidate(m.candidate);
        } catch {}
      if (["peer-left", "stop"].includes(m.type)) {
        peer.current?.close();
        setStatus("对端已离线");
      }
      if (m.type === "room-full") setStatus("房间已有两台设备");
    };
  };
  const start = async (next = facing) => {
    stream.current?.getTracks().forEach((t) => t.stop());
    stream.current = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: next },
        width: { ideal: 1280 },
        height: { ideal: 720 },
      },
      audio: false,
    });
    local.current.srcObject = stream.current;
    setStatus("摄像头已开启");
    connect();
  };
  const switchCam = async () => {
    const next = facing === "environment" ? "user" : "environment";
    setFacing(next);
    await start(next);
  };
  const stop = () => {
    send({ type: "stop" });
    stream.current?.getTracks().forEach((t) => t.stop());
    peer.current?.close();
    socket.current?.close();
    setStatus("已停止");
  };
  useEffect(() => () => stop(), []);
  return (
    <div className="camera-command">
      <div className="camera-header">
        <div>
          <span className="eyebrow">WEBRTC · PRIVATE ROOM</span>
          <h2>实时手机视角</h2>
          <p>画面点对点传输，服务器只负责信令协调。</p>
        </div>
        <div className={`camera-status ${status === "直播中" ? "live" : ""}`}>
          <i />
          {status}
        </div>
      </div>
      <div className="camera-layout">
        <div className="video-console">
          {role === "publisher" ? (
            <video ref={local} autoPlay muted playsInline />
          ) : (
            <video ref={remote} autoPlay playsInline controls />
          )}
          <div className="video-placeholder">
            <Camera size={48} weight="thin" />
            <span>
              {role === "publisher"
                ? "等待开启手机摄像头"
                : "等待手机端加入房间"}
            </span>
          </div>
        </div>
        <aside className="camera-side">
          <div className="role-toggle">
            <button
              className={role === "viewer" ? "active" : ""}
              onClick={() => setRole("viewer")}
            >
              <Desktop />
              电脑观看
            </button>
            <button
              className={role === "publisher" ? "active" : ""}
              onClick={() => setRole("publisher")}
            >
              <Camera />
              手机直播
            </button>
          </div>
          <label>
            私有房间
            <input
              value={room}
              onChange={(e) =>
                setRoom(e.target.value.replace(/[^A-Za-z0-9_-]/g, ""))
              }
            />
          </label>
          {role === "viewer" ? (
            <button className="primary-camera" onClick={connect}>
              <Link />
              连接观看
            </button>
          ) : (
            <button
              className="primary-camera"
              onClick={() =>
                start().catch(() => setStatus("请检查 HTTPS 与摄像头权限"))
              }
            >
              <VideoCamera />
              开启直播
            </button>
          )}
          <button onClick={switchCam} disabled={role !== "publisher"}>
            <CameraRotate />
            切换摄像头
          </button>
          <button onClick={stop}>
            <Stop />
            停止连接
          </button>
          <div className="camera-help">
            <CheckCircle />
            请在手机和电脑输入相同房间名。手机浏览器必须通过 HTTPS 打开。
          </div>
        </aside>
      </div>
    </div>
  );
}

/* ========== ComfyUI 图像工坊 ========== */
function LegacyComfyConsole({ embedded = false }) {
  const [status, setStatus] = useState({
      agentOnline: false,
      comfyRunning: false,
    }),
    [tasks, setTasks] = useState([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [inputFileId, setInputFileId] = useState(null);
  const [form, setForm] = useState({
    workflowName: "pony_tti_api.json",
    positivePrompt:
      "score_9, score_8_up, score_7_up, masterpiece, best quality",
    negativePrompt: "score_4, score_3, low quality, blurry, bad anatomy",
    width: 896,
    height: 1152,
    steps: 25,
    cfg: 7,
    seed: -1,
  });
  const load = async () => {
    try {
      const [s, t] = await Promise.all([
        get("/comfy/status"),
        get("/comfy/tasks?limit=20"),
      ]);
      setStatus(s);
      setTasks(t);
    } catch (e) {
      setError(e.message);
    }
  };
  useEffect(() => {
    load();
    const id = setInterval(load, 3000);
    return () => clearInterval(id);
  }, []);
  const control = async (command) => {
    setBusy(true);
    setError("");
    try {
      const r = await fetch(`${API}/comfy/control/${command}`, {
          method: "POST",
        }),
        j = await r.json();
      if (!r.ok) throw new Error(j.message);
      setTimeout(load, 1000);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };
  const upload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setBusy(true);
    try {
      const body = new FormData();
      body.append("file", file);
      const r = await fetch(`${API}/comfy/uploads`, { method: "POST", body }),
        j = await r.json();
      if (!r.ok) throw new Error(j.message);
      setInputFileId(j.data.fileId);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };
  const submit = async () => {
    setBusy(true);
    setError("");
    try {
      const r = await fetch(`${API}/comfy/tasks`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ ...form, inputFileId }),
        }),
        j = await r.json();
      if (!r.ok) throw new Error(j.message);
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };
  const set = (key, value) => setForm((f) => ({ ...f, [key]: value }));
  return (
    <div className={`comfy-shell ${embedded ? "embedded" : ""}`}>
      <div className="comfy-section-title">
        <div>
          <span>COMFYUI STUDIO</span>
          <h2>图像工坊</h2>
        </div>
        <small>API Format · pony_tti_api.json</small>
      </div>
      <section className="comfy-status">
        <div>
          <span
            className={status.agentOnline ? "status-dot online" : "status-dot"}
          />
          <small>LOCAL AGENT</small>
          <strong>{status.agentOnline ? "在线" : "离线"}</strong>
        </div>
        <div>
          <span
            className={status.comfyRunning ? "status-dot online" : "status-dot"}
          />
          <small>COMFYUI</small>
          <strong>{status.comfyRunning ? "运行中" : "已停止"}</strong>
        </div>
        <div className="comfy-actions">
          <button
            onClick={() => control("start")}
            disabled={busy || !status.agentOnline || status.comfyRunning}
          >
            <Play />
            启动
          </button>
          <button
            onClick={() => control("stop")}
            disabled={busy || !status.agentOnline || !status.comfyRunning}
          >
            <Power />
            停止
          </button>
        </div>
      </section>
      {error && <div className="inline-error">{error}</div>}
      <div className="comfy-grid">
        <section className="comfy-form">
          <h2>生成参数</h2>
          <label>
            工作流
            <select
              value={form.workflowName}
              onChange={(e) => set("workflowName", e.target.value)}
            >
              <option>pony_tti_api.json</option>
            </select>
          </label>
          <label>
            正向提示词
            <textarea
              rows="5"
              value={form.positivePrompt}
              onChange={(e) => set("positivePrompt", e.target.value)}
            />
          </label>
          <label>
            反向提示词
            <textarea
              rows="3"
              value={form.negativePrompt}
              onChange={(e) => set("negativePrompt", e.target.value)}
            />
          </label>
          <div className="parameter-grid">
            {[
              ["width", "宽度"],
              ["height", "高度"],
              ["steps", "步数"],
              ["cfg", "CFG"],
              ["seed", "种子"],
            ].map(([key, label]) => (
              <label key={key}>
                {label}
                <input
                  type="number"
                  step={key === "cfg" ? ".5" : "1"}
                  value={form[key]}
                  onChange={(e) => set(key, +e.target.value)}
                />
              </label>
            ))}
          </div>
          <label className="file-input">
            参考图片
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              onChange={upload}
            />
            <span>{inputFileId ? "已安全上传" : "选择 PNG / JPEG / WebP"}</span>
          </label>
          <button
            className="comfy-submit"
            onClick={submit}
            disabled={
              busy ||
              !status.agentOnline ||
              !status.comfyRunning ||
              !form.positivePrompt.trim()
            }
          >
            <Sparkle />
            开始生成
          </button>
        </section>
        <section className="comfy-history">
          <h2>最近任务</h2>
          {tasks.length === 0 ? (
            <div className="empty-mini">
              <ImageSquare size={38} />
              <span>还没有生成记录</span>
            </div>
          ) : (
            tasks.map((t) => <TaskCard key={t.id} task={t} />)
          )}
        </section>
      </div>
    </div>
  );
}
function TaskCard({ task }) {
  return (
    <article className="task-card">
      {task.resultUrl ? (
        <img src={task.resultUrl} />
      ) : (
        <div className="task-placeholder">
          <ImageSquare />
        </div>
      )}
      <div>
        <span>
          {task.status} · {task.progress}%
        </span>
        <strong>{task.positivePrompt}</strong>
        <small>
          {task.width}×{task.height} · {task.steps} steps · CFG {task.cfg}
        </small>
        {task.errorMessage && <em>{task.errorMessage}</em>}
        <div className="task-progress">
          <i style={{ width: `${task.progress}%` }} />
        </div>
      </div>
      {task.resultUrl && (
        <a href={task.resultUrl} download>
          下载
        </a>
      )}
    </article>
  );
}

/* ========== 通用组件 ========== */
function DataPanel({ title, children }) {
  return (
    <section className="data-panel">
      <header>
        <h2>{title}</h2>
        <span className="panel-live">
          <i /> LIVE
        </span>
      </header>
      {children}
    </section>
  );
}
function EmptyMini() {
  return (
    <div className="empty-mini">
      <ChartLineUp size={32} />
      <span>等待活动数据</span>
    </div>
  );
}
function LoadingState() {
  return (
    <div className="loading-state">
      <div className="neural-loader">
        <i />
        <i />
        <i />
      </div>
      <strong>正在连接 Neural Command</strong>
      <span>读取 AI Maid 实时状态…</span>
    </div>
  );
}
function ErrorState({ message, retry }) {
  return (
    <div className="error-state">
      <Warning size={42} />
      <strong>数据链路暂时不可用</strong>
      <span>{message}</span>
      <button onClick={retry}>
        <ArrowsClockwise />
        重新连接
      </button>
    </div>
  );
}

export default App;
