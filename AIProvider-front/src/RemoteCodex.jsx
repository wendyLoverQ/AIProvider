import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowClockwise,
  ChatCircleDots,
  PaperPlaneTilt,
  Plus,
  SignIn,
  Stop,
  Warning,
} from "@phosphor-icons/react";
import "./RemoteCodex.css";
import "./RemoteCodexDevice.css";
import "./RemoteCodexLayoutFixes.css";
import "./RemoteCodexQuota.css";

const API = "/api/remote-codex";

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });
  const json = await response
    .json()
    .catch(() => ({ message: `HTTP ${response.status}` }));
  if (!response.ok || json.code !== 200)
    throw new Error(json.message || `HTTP ${response.status}`);
  return json.data;
}

const time = (value) =>
  value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—";
const resetTime = (value) =>
  value
    ? new Date(value * 1000).toLocaleString("zh-CN", { hour12: false })
    : "未提供";
const DEVICE_LOGIN_URL = "https://auth.openai.com/codex/device";

export default function RemoteCodex() {
  const [status, setStatus] = useState(null);
  const [conversations, setConversations] = useState([]);
  const [selectedId, setSelectedId] = useState("");
  const [conversation, setConversation] = useState(null);
  const [quota, setQuota] = useState(null);
  const [quotaError, setQuotaError] = useState("");
  const [prompt, setPrompt] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [models, setModels] = useState([]);
  const [model, setModel] = useState("");
  const messagesEndRef = useRef(null);
  const loginCode = useMemo(
    () => status?.loginOutput?.match(/\b[A-Z0-9]{4}-[A-Z0-9]{5}\b/)?.[0] || "",
    [status?.loginOutput],
  );
  const messageCount = conversation?.messages?.length || 0;
  const quotaSnapshot = quota?.rateLimitsByLimitId?.codex || quota?.rateLimits;
  const primaryQuota = quotaSnapshot?.primary;

  const load = useCallback(
    async (quiet = false) => {
      try {
        const [nextStatus, nextList] = await Promise.all([
          request("/status"),
          request("/conversations"),
        ]);
        setStatus(nextStatus);
        setConversations(nextList || []);
        setError("");
        const target = selectedId || nextList?.[0]?.id;
        if (target) {
          setSelectedId(target);
          setConversation(await request(`/conversations/${target}`));
        }
      } catch (exception) {
        if (!quiet) setError(exception.message);
      }
    },
    [selectedId],
  );

  useEffect(() => {
    load();
  }, [load]);
  useEffect(() => {
    if (!status?.loggedIn) return;
    request("/models")
      .then((value) => {
        const list = value?.data || [];
        setModels(list);
        setModel(
          (current) =>
            current ||
            list.find((item) => item.isDefault)?.model ||
            list[0]?.model ||
            "",
        );
      })
      .catch((exception) => setError(exception.message));
  }, [status?.loggedIn]);
  useEffect(() => {
    if (!selectedId) return undefined;
    const controller = new AbortController();
    Promise.resolve()
      .then(() =>
        fetch(`${API}/conversations/${selectedId}/events`, {
          signal: controller.signal,
        }),
      )
      .then(async (response) => {
        if (!response.ok || !response.body)
          throw new Error(`实时连接失败：HTTP ${response.status}`);
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const frames = buffer.split("\n\n");
          buffer = frames.pop() || "";
          for (const frame of frames) {
            const data = frame
              .split("\n")
              .filter((line) => line.startsWith("data:"))
              .map((line) => line.slice(5))
              .join("\n");
            if (data) setConversation(JSON.parse(data));
          }
        }
      })
      .catch((exception) => {
        if (exception.name !== "AbortError") setError(exception.message);
      });
    return () => controller.abort();
  }, [selectedId]);
  useEffect(() => {
    if (
      (!conversation?.status?.includes("RUNNING") &&
        status?.loginState !== "RUNNING")
    )
      return undefined;
    const timer = setInterval(() => load(true), 1500);
    return () => clearInterval(timer);
  }, [conversation?.status, load, status?.loginState]);
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView?.({
      behavior: "smooth",
      block: "end",
    });
  }, [conversation?.id, conversation?.status, messageCount]);
  const loadQuota = useCallback(async () => {
    if (!status?.loggedIn) return;
    try {
      setQuota(await request("/quota"));
      setQuotaError("");
    } catch (exception) {
      setQuota(null);
      setQuotaError(exception.message);
    }
  }, [status?.loggedIn]);
  useEffect(() => {
    if (!status?.loggedIn) return undefined;
    loadQuota();
    const timer = setInterval(loadQuota, 60000);
    return () => clearInterval(timer);
  }, [loadQuota, status?.loggedIn]);

  const create = async () => {
    setBusy(true);
    try {
      const next = await request("/conversations", { method: "POST" });
      setSelectedId(next.id);
      setConversation(next);
      await load(true);
    } catch (exception) {
      setError(exception.message);
    } finally {
      setBusy(false);
    }
  };
  const select = async (id) => {
    setSelectedId(id);
    try {
      setConversation(await request(`/conversations/${id}`));
      setError("");
    } catch (exception) {
      setError(exception.message);
    }
  };
  const startLogin = async () => {
    const authWindow = window.open(
      DEVICE_LOGIN_URL,
      "_blank",
      "noopener,noreferrer",
    );
    setBusy(true);
    try {
      setStatus(await request("/login", { method: "POST" }));
      setError(
        authWindow
          ? ""
          : "浏览器拦截了授权页，请点击下方“打开 OpenAI 授权页”。",
      );
    } catch (exception) {
      setError(exception.message);
    } finally {
      setBusy(false);
    }
  };
  const send = async (event) => {
    event.preventDefault();
    const content = prompt.trim();
    if (!content || !conversation || busy) return;
    setBusy(true);
    try {
      const running = conversation.status === "RUNNING";
      const next = await request(
        `/conversations/${conversation.id}/${running ? "steer" : "messages"}`,
        { method: "POST", body: JSON.stringify({ content, model }) },
      );
      setPrompt("");
      setConversation(next);
      setError("");
    } catch (exception) {
      setError(exception.message);
    } finally {
      setBusy(false);
    }
  };
  const interrupt = async () => {
    if (!conversation || busy) return;
    setBusy(true);
    try {
      setConversation(
        await request(`/conversations/${conversation.id}/interrupt`, {
          method: "POST",
        }),
      );
      setError("");
    } catch (exception) {
      setError(exception.message);
    } finally {
      setBusy(false);
    }
  };
  const sendOnEnter = (event) => {
    if (
      event.key !== "Enter" ||
      event.shiftKey ||
      event.nativeEvent.isComposing
    )
      return;
    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  };
  const active = useMemo(
    () => conversations.find((item) => item.id === selectedId),
    [conversations, selectedId],
  );

  return (
    <section className="remote-codex-shell">
      <header className="remote-codex-toolbar">
        <div>
          <span>REMOTE WORKSPACE</span>
          <strong>
            {status?.loggedIn ? "Codex 已登录 · 完整权限" : "Codex 未登录"}
          </strong>
          <small>{status?.workingDirectory || "工作目录未配置"}</small>
        </div>
        {status?.loggedIn && (
          <section className="remote-codex-quota" aria-label="Codex 额度">
            <div>
              <span>账户</span>
              <strong>
                {quotaSnapshot?.planType?.toUpperCase() ||
                  (quotaError ? "不可用" : "读取中")}
              </strong>
            </div>
            <div>
              <span>当前窗口剩余</span>
              <strong>
                {primaryQuota
                  ? `${Math.max(0, 100 - primaryQuota.usedPercent)}%`
                  : "—"}
              </strong>
            </div>
            <div>
              <span>额度窗口</span>
              <strong>
                {primaryQuota?.windowDurationMins
                  ? `${Math.round(primaryQuota.windowDurationMins / 1440)} 天`
                  : "—"}
              </strong>
            </div>
            <div>
              <span>重置时间</span>
              <strong>{resetTime(primaryQuota?.resetsAt)}</strong>
            </div>
            {quotaError && <small title={quotaError}>额度接口不可用</small>}
          </section>
        )}
        <div>
          <button
            type="button"
            onClick={() => {
              load();
              loadQuota();
            }}
          >
            <ArrowClockwise />
            刷新
          </button>
        </div>
      </header>
      {error && (
        <div className="remote-codex-error">
          <Warning />
          {error}
        </div>
      )}
      {!status?.loggedIn && (
        <section className="remote-codex-login">
          <div>
            <SignIn />
            <span>
              <strong>先完成一次 Codex 登录</strong>
              <small>
                点击后会打开 OpenAI 授权页，再输入页面显示的一次性代码。
              </small>
            </span>
          </div>
          <button
            type="button"
            onClick={startLogin}
            disabled={busy || status?.loginState === "RUNNING"}
          >
            {status?.loginState === "RUNNING"
              ? "正在等待你确认"
              : "开始设备登录"}
          </button>
          {loginCode && (
            <div className="remote-codex-device">
              <a href={DEVICE_LOGIN_URL} target="_blank" rel="noreferrer">
                打开 OpenAI 授权页
              </a>
              <strong>{loginCode}</strong>
              <small>
                此代码 15 分钟内有效；完成授权后页面会自动进入可对话状态。
              </small>
            </div>
          )}
          {status?.loginOutput && (
            <details>
              <summary>查看 Codex 登录原始输出</summary>
              <pre>{status.loginOutput}</pre>
            </details>
          )}
        </section>
      )}
      <div className="remote-codex-layout">
        <aside className="remote-codex-list">
          <header>
            <div>
              <strong>对话</strong>
              <small>{conversations.length} 个</small>
            </div>
            <button
              type="button"
              onClick={create}
              disabled={busy || !status?.loggedIn}
              aria-label="新建远程 Codex 对话"
            >
              <Plus />
            </button>
          </header>
          <div>
            {conversations.map((item) => (
              <button
                type="button"
                className={item.id === selectedId ? "active" : ""}
                key={item.id}
                onClick={() => select(item.id)}
              >
                <ChatCircleDots />
                <span>
                  <strong>{item.title}</strong>
                  <small>
                    {item.status === "RUNNING"
                      ? "回复中…"
                      : time(item.updatedAt)}
                  </small>
                </span>
              </button>
            ))}
          </div>
        </aside>
        <article className="remote-codex-chat">
          {conversation ? (
            <>
              <header>
                <div>
                  <strong>{active?.title || conversation.title}</strong>
                  <small>
                    {conversation.status === "RUNNING"
                      ? "Codex 正在处理"
                      : "可以继续对话"}
                  </small>
                </div>
                <div className="remote-codex-turn-controls">
                  <label>
                    <span>模型</span>
                    <select
                      value={model}
                      onChange={(event) => setModel(event.target.value)}
                      disabled={conversation.status === "RUNNING"}
                    >
                      {models.map((item) => (
                        <option value={item.model} key={item.id}>
                          {item.displayName}
                          {item.isDefault ? "（默认）" : ""}
                        </option>
                      ))}
                    </select>
                  </label>
                  {conversation.status === "RUNNING" && (
                    <button
                      type="button"
                      onClick={interrupt}
                      disabled={busy}
                      aria-label="停止 Codex"
                    >
                      <Stop />
                      停止
                    </button>
                  )}
                </div>
              </header>
              <div className="remote-codex-messages" aria-live="polite">
                {(conversation.messages || []).length ? (
                  conversation.messages.map((message) => (
                    <div
                      className={`remote-message ${message.role}`}
                      key={message.id}
                    >
                      <span>{message.role === "user" ? "你" : "Codex"}</span>
                      <p>{message.content}</p>
                      <time>{time(message.createdAt)}</time>
                    </div>
                  ))
                ) : (
                  <div className="remote-codex-empty">
                    <ChatCircleDots />
                    <strong>这是一个新对话</strong>
                    <span>输入任务后，Codex 会在 AWS 上处理并回复。</span>
                  </div>
                )}
                {(conversation.liveEvents || []).map((event) => (
                  <div
                    className={`remote-codex-live-event ${event.status}`}
                    key={event.id}
                  >
                    <header>
                      <strong>{event.title}</strong>
                      <span>
                        {event.status === "inProgress"
                          ? "执行中"
                          : event.status === "failed"
                            ? "失败"
                            : "完成"}
                      </span>
                    </header>
                    <pre>{event.detail}</pre>
                  </div>
                ))}
                {conversation.liveMessage && (
                  <div className="remote-message assistant live">
                    <span>Codex · 实时回复</span>
                    <p>{conversation.liveMessage}</p>
                  </div>
                )}
                {conversation.status === "RUNNING" &&
                  !conversation.liveMessage &&
                  !(conversation.liveEvents || []).length && (
                    <div className="remote-codex-thinking">
                      Codex 正在思考和执行…
                    </div>
                  )}
                {conversation.errorMessage && (
                  <div className="remote-codex-turn-error">
                    <Warning />
                    {conversation.errorMessage}
                  </div>
                )}
                <span
                  className="remote-codex-scroll-anchor"
                  ref={messagesEndRef}
                  aria-hidden="true"
                />
              </div>
              <form className="remote-codex-composer" onSubmit={send}>
                <label htmlFor="remote-codex-prompt">发送给 Codex</label>
                <textarea
                  id="remote-codex-prompt"
                  value={prompt}
                  onChange={(event) => setPrompt(event.target.value)}
                  onKeyDown={sendOnEnter}
                  placeholder={
                    conversation.status === "RUNNING"
                      ? "输入补充要求，发送后会插入当前执行…"
                      : "描述你要完成的开发任务…"
                  }
                  disabled={!status?.loggedIn}
                />
                <button
                  type="submit"
                  disabled={!prompt.trim() || busy || !status?.loggedIn}
                  aria-label={
                    conversation.status === "RUNNING" ? "插话" : "发送消息"
                  }
                >
                  <PaperPlaneTilt />
                </button>
                {conversation.status === "RUNNING" && (
                  <small className="remote-codex-steer-hint">
                    Enter 插话 · Shift+Enter 换行
                  </small>
                )}
              </form>
            </>
          ) : (
            <div className="remote-codex-empty">
              <ChatCircleDots />
              <strong>
                {status?.loggedIn ? "新建一个对话" : "完成登录后即可对话"}
              </strong>
              <span>这里只提供最基础的新建、选择、发送和回复功能。</span>
            </div>
          )}
        </article>
      </div>
    </section>
  );
}
