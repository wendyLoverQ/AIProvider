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
  ChatsCircle,
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
import CuteHomeBackground from "./CuteHomeBackground";
import RemoteCodex from "./RemoteCodex";
import "./App.css";
import "./CodexTheme.css";
import "./SemanticTheme.css";
import "./KawaiiUi.css";

const API = "/api";
const NAV = [
  { key: "workshop", label: "图像工坊", icon: ImageSquare },
  { key: "prompts", label: "Prompt 管理", icon: SlidersHorizontal },
  { key: "maid", label: "我的女仆", icon: Heart },
  { key: "monitor", label: "监控中心", icon: Pulse },
  { key: "remoteCodex", label: "远程 Codex", icon: ChatsCircle },
  { key: "camera", label: "手机监控", icon: VideoCamera, closed: true, hidden: true },
  { key: "twitter", label: "Twitter 发布", icon: XLogo },
  { key: "appearance", label: "UI 控制", icon: Palette },
  { key: "settings", label: "系统设置", icon: GearSix },
];
const VISIBLE_NAV = NAV.filter((item) => !item.hidden);
const MOBILE_NAV = VISIBLE_NAV;

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
  const result = await response.json();
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
        get("/insights/command").catch(() => ({
          counts: {},
          reminders: [],
          notes: [],
          voice: [],
          videos: [],
          remoteVideos: [],
          runtime: {},
        })),
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
  const viewFromPath = () => ({ "/workshop": "workshop", "/prompts": "prompts", "/prompt-options": "promptOptions", "/maid": "maid", "/admin/monitor": "monitor", "/remote-codex": "remoteCodex", "/camera": "camera", "/twitter": "twitter", "/appearance": "appearance", "/settings": "settings" })[window.location.pathname] || "home";
  const [view, setView] = useState(viewFromPath);
  const [promptOptionCategory, setPromptOptionCategory] = useState("");
  const dashboard = useDashboardData();
  const current = NAV.find((item) => item.key === (view === "promptOptions" ? "prompts" : view));
  useEffect(() => {
    const path = ({ workshop: "/workshop", prompts: "/prompts", promptOptions: "/prompt-options", maid: "/maid", monitor: "/admin/monitor", remoteCodex: "/remote-codex", camera: "/camera", twitter: "/twitter", appearance: "/appearance", settings: "/settings" })[view] || "/";
    if (window.location.pathname !== path) window.history.replaceState({}, "", path);
  }, [view]);
  useEffect(() => {
    const onPop = () => setView(viewFromPath());
    window.addEventListener("popstate", onPop); return () => window.removeEventListener("popstate", onPop);
  }, []);
  return (
    <div className="neural-shell">
      <aside className="rail">
        <div
          className="rail-brand"
          onClick={() => setView("home")}
          style={{ cursor: "pointer" }}
        >
          <span className="rail-mascot"><i className="mascot-ear ear-left" /><i className="mascot-ear ear-right" /><Cat weight="fill" /><b>•ᴗ•</b><em>✦</em></span>
          <span className="rail-brand-label">MAID</span>
        </div>
        <nav>
          <button
            className={view === "home" ? "nav-button active" : "nav-button"}
            data-nav-key="home"
            onClick={() => setView("home")}
            title="首页"
          >
            <House size={22} weight={view === "home" ? "duotone" : "regular"} />
            <span>首页</span>
          </button>
          {VISIBLE_NAV.map((item) => (
            <NavButton
              key={item.key}
              item={item}
              active={view === item.key || (view === "promptOptions" && item.key === "prompts")}
              onClick={() => setView(item.key)}
            />
          ))}
        </nav>
      </aside>
      <header className="mobile-head">
        <div className="mobile-logo">
          <Cat weight="fill" /> Kawaii Maid
        </div>
        <span className="live-copy">
          <i /> LIVE
        </span>
      </header>
      <main className={`workspace workspace-${view}`}>
        {!["home", "appearance", "settings"].includes(view) && <KawaiiPageAtmosphere soft={["workshop", "prompts"].includes(view)} />}
        {view !== "home" && current && (
          <div className="section-head">
            <div>
              <span className="eyebrow">AI Maid · Neural Command</span>
              <h1>{current.label}</h1>
            </div>
            <SystemClock />
          </div>
        )}
        {view === "home" && <HomeView data={dashboard} onOpenWorkshop={() => setView("workshop")} />}
        <div className={`tool-home compact-home persistent-workshop ${view === "workshop" ? "" : "persistent-view-hidden"}`} aria-hidden={view !== "workshop"}>
          <ComfyConsole embedded active={view === "workshop"} />
        </div>
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
        {view === "remoteCodex" && <RemoteCodex />}
        {view === "twitter" && <TwitterPublisher />}
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
          />
        ))}
      </nav>
    </div>
  );
}

function NavButton({ item, active, onClick }) {
  const Icon = item.icon;
  return (
    <button
      className={active ? "nav-button active" : "nav-button"}
      disabled={item.closed}
      data-nav-key={item.key}
      onClick={item.closed ? undefined : onClick}
      title={item.closed ? `${item.label} · 暂未开放` : item.label}
    >
      <Icon size={22} weight={active ? "duotone" : "regular"} />
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
  const runtime = data.insights?.runtime || {};
  const roles = data.insights?.voiceRoles || [];
  const currentMaid = data.insights?.currentMaid || {};
  const explicitCurrentRoleId = currentMaid.MaidId ?? currentMaid.maidId ?? runtime.LastRole ?? runtime.lastRole ?? "";
  const currentRoleId = explicitCurrentRoleId || roles[0]?.RoleId || roles[0]?.roleId || "";
  const [selectedRoleId, setSelectedRoleId] = useState(currentRoleId);
  const [roleData, setRoleData] = useState({ state: {}, summary: {}, daily: [], recentCalls: [] });
  const [roleLoading, setRoleLoading] = useState(false);
  const [avatarFailed, setAvatarFailed] = useState(false);

  useEffect(() => {
    if (!selectedRoleId && currentRoleId) setSelectedRoleId(currentRoleId);
  }, [currentRoleId, selectedRoleId]);
  useEffect(() => {
    if (!selectedRoleId) return;
    let cancelled = false;
    setRoleLoading(true);
    get(`/insights/maid-role?roleId=${encodeURIComponent(selectedRoleId)}`)
      .then((result) => { if (!cancelled) setRoleData(result); })
      .catch(() => { if (!cancelled) setRoleData({ state: {}, summary: {}, daily: [], recentCalls: [] }); })
      .finally(() => { if (!cancelled) setRoleLoading(false); });
    return () => { cancelled = true; };
  }, [selectedRoleId]);
  useEffect(() => setAvatarFailed(false), [selectedRoleId]);

  const selectedRole = roles.find((role) => String(role.RoleId ?? role.roleId).toLowerCase() === String(selectedRoleId).toLowerCase()) || {};
  const selectedName = selectedRole.DisplayName ?? selectedRole.displayName ?? roleData.state?.Name ?? roleData.state?.name ?? selectedRoleId;
  const avatarUrl = selectedRole.avatarUrl;
  const state = roleData.state || {};
  const summary = roleData.summary || {};
  const trend = roleData.daily || [];
  const recentCalls = roleData.recentCalls || [];
  const latestCall = recentCalls[0] || {};
  const roleUpdatedAt = state.UpdatedAt ?? state.updatedAt ?? currentMaid.UpdatedAt ?? currentMaid.updatedAt ?? runtime.UpdatedAt ?? runtime.updatedAt;
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
          <p>{isCurrentRole ? (explicitCurrentRoleId ? "这是 AMA 当前正在使用的角色。" : "AMA 尚未指定当前角色，网页先展示角色列表中的第一位。") : "当前只切换网页查看的数据，不会修改 AMA 正在使用的角色。"}</p>
          <label className="maid-role-select"><span>直接选择角色</span><select aria-label="直接选择角色" value={selectedRoleId} onChange={(event) => setSelectedRoleId(event.target.value)} disabled={!roles.length}>{roles.map((role) => { const id = role.RoleId ?? role.roleId; const name = role.DisplayName ?? role.displayName ?? id; return <option key={id} value={id}>{name}{String(id).toLowerCase() === String(currentRoleId).toLowerCase() ? " · 当前" : ""}</option>; })}</select><small>{roles.length ? `${selectedRoleIndex + 1} / ${roles.length}` : "暂无角色"}</small></label>
          <div className="role-freshness">
            <span>{roleLoading ? "正在加载角色数据" : "角色更新时间"}</span>
            <strong>{roleLoading ? "…" : roleUpdatedAt ? timeAgo(roleUpdatedAt) : "暂无时间"}</strong>
          </div>
        </div>
      </section>

      <section className="maid-stat-grid compact-stats">
        <StatCard icon={Brain} label="LLM 调用" value={fmt(summary.LlmCallCount ?? summary.llmCallCount)} sub="该角色累计" tone="violet" />
        <StatCard icon={Database} label="Token" value={compact(summary.TotalTokens ?? summary.totalTokens)} sub="该角色累计" tone="cyan" />
        <StatCard icon={ChatCircle} label="消息" value={fmt(summary.MessageCount ?? summary.messageCount)} sub="该角色消息" tone="cyan" />
        <StatCard icon={ChatsTeardrop} label="对话" value={fmt(summary.ConversationCount ?? summary.conversationCount)} sub="该角色会话" tone="coral" />
        <StatCard icon={Heart} label="互动" value={fmt(state.InteractionCount ?? state.interactionCount)} sub="角色状态" tone="coral" />
        <StatCard icon={Clock} label="陪伴时间" value={formatTime(state.CompanionshipSeconds ?? state.companionshipSeconds)} sub="角色状态" tone="amber" />
      </section>

      <section className="maid-ai-runtime">
        <div><span>最近 Provider</span><strong>{latestCall.Provider ?? latestCall.provider ?? "暂无记录"}</strong></div>
        <div><span>最近模型</span><strong>{latestCall.Model ?? latestCall.model ?? "暂无记录"}</strong></div>
        <div><span>输入 Token</span><strong>{fmt(summary.InputTokens ?? summary.inputTokens)}</strong></div>
        <div><span>输出 Token</span><strong>{fmt(summary.OutputTokens ?? summary.outputTokens)}</strong></div>
        <div><span>好感度</span><strong>{fmt(state.Favorability ?? state.favorability)}</strong></div>
        <div><span>心情</span><strong>{state.Mood ?? state.mood ?? "暂无"}</strong></div>
      </section>

      <section className="maid-compact-lower">
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
              <div><strong>{call.Model ?? call.model ?? "未知模型"}</strong><span>{call.Provider ?? call.provider ?? "未知提供方"}</span></div>
              <b>{fmt(call.TotalTokens ?? call.totalTokens)} tokens</b>
              <time>{timeAgo(call.CreatedAt ?? call.createdAt)}</time>
            </article>
          )) : <EmptyMini />}
        </div>
      </section>
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
        <i /> 实时
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
