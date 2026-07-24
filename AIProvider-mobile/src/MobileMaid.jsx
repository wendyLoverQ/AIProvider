import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowClockwise, Brain, ChatCircle, Heart, Sparkle, User, Warning, Waveform } from "@phosphor-icons/react";

const readData = async (path) => {
  const response = await fetch(`/api${path}`);
  const body = await response.json().catch(() => null);
  if (!response.ok || body?.code !== 200) throw new Error(body?.message || `HTTP ${response.status}`);
  return body.data;
};

const value = (source, pascal, camel, fallback = 0) => source?.[pascal] ?? source?.[camel] ?? fallback;
const count = (number) => Number(number || 0).toLocaleString("zh-CN");
const dateTime = (input) => input ? new Date(input).toLocaleString("zh-CN", { hour12: false }) : "暂无记录";

export default function MobileMaid() {
  const [command, setCommand] = useState(null);
  const [roleId, setRoleId] = useState("");
  const [roleData, setRoleData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const roles = command?.voiceRoles || [];
  const currentMaid = command?.currentMaid || {};
  const runtime = command?.runtime || {};
  const currentRoleId = value(currentMaid, "MaidId", "maidId", "") || value(runtime, "LastRole", "lastRole", "") || value(roles[0], "RoleId", "roleId", "");

  const loadCommand = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const next = await readData("/insights/command");
      setCommand(next);
      setRoleId((existing) => existing || value(next?.currentMaid, "MaidId", "maidId", "") || value(next?.runtime, "LastRole", "lastRole", "") || value(next?.voiceRoles?.[0], "RoleId", "roleId", ""));
    } catch (exception) {
      setError(exception.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadCommand(); }, [loadCommand]);
  useEffect(() => {
    if (!roleId) { setRoleData(null); return; }
    let active = true;
    setLoading(true);
    setError("");
    readData(`/insights/maid-role?roleId=${encodeURIComponent(roleId)}`)
      .then((next) => { if (active) setRoleData(next); })
      .catch((exception) => { if (active) setError(exception.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [roleId]);

  const selectedRole = useMemo(
    () => roles.find((role) => String(value(role, "RoleId", "roleId", "")).toLowerCase() === String(roleId).toLowerCase()) || {},
    [roleId, roles],
  );
  const card = roleData?.card || {};
  const summary = roleData?.summary || {};
  const state = roleData?.state || {};
  const calls = roleData?.recentCalls || [];
  const displayName = value(card, "Name", "name", "") || value(selectedRole, "DisplayName", "displayName", "") || roleId || "未同步角色";
  const avatarUrl = selectedRole.avatarUrl;

  return <section className="mobile-maid">
    <header className="mobile-maid-hero">
      <div className="mobile-maid-avatar">{avatarUrl ? <img src={avatarUrl} alt={`${displayName}头像`} /> : <User />}</div>
      <div>
        <span><Heart weight="fill" />{String(roleId).toLowerCase() === String(currentRoleId).toLowerCase() ? "当前角色" : "角色查看"}</span>
        <h2>{displayName}</h2>
        <p>{value(card, "CardSummary", "cardSummary", "") || value(card, "RoleTitle", "roleTitle", "") || "查看角色状态与真实模型活动。"}</p>
      </div>
      <button type="button" onClick={loadCommand} disabled={loading} aria-label="刷新女仆数据"><ArrowClockwise className={loading ? "spin" : ""} /></button>
    </header>

    {roles.length > 0 && <label className="mobile-maid-select"><span>选择角色</span><select value={roleId} onChange={(event) => setRoleId(event.target.value)}>{roles.map((role) => {
      const id = value(role, "RoleId", "roleId", "");
      return <option value={id} key={id}>{value(role, "DisplayName", "displayName", id)}{String(id).toLowerCase() === String(currentRoleId).toLowerCase() ? " · 当前" : ""}</option>;
    })}</select></label>}

    {error && <div className="mobile-inline-error" role="alert"><Warning />{error}</div>}
    {loading && !roleData ? <div className="mobile-loading">正在读取角色数据…</div> : <>
      <div className="mobile-maid-stats">
        <article><Brain /><span>LLM 调用</span><strong>{count(value(summary, "LlmCallCount", "llmCallCount"))}</strong></article>
        <article><Sparkle /><span>Token</span><strong>{count(value(summary, "TotalTokens", "totalTokens"))}</strong></article>
        <article><ChatCircle /><span>主动回应</span><strong>{count(value(summary, "ProactiveResponseCount", "proactiveResponseCount"))}</strong></article>
        <article><Waveform /><span>语音播放</span><strong>{count(value(summary, "VoicePlayCount", "voicePlayCount"))}</strong></article>
      </div>
      <section className="mobile-maid-detail">
        <h3>角色状态</h3>
        <dl>
          <div><dt>最近模型</dt><dd>{value(calls[0], "Model", "model", "暂无记录")}</dd></div>
          <div><dt>Provider</dt><dd>{value(calls[0], "Provider", "provider", "暂无记录")}</dd></div>
          <div><dt>最近业务</dt><dd>{value(calls[0], "SourceName", "sourceName", "暂无记录")}</dd></div>
          <div><dt>更新时间</dt><dd>{dateTime(value(card, "UpdatedAt", "updatedAt", value(state, "UpdatedAt", "updatedAt", "")))}</dd></div>
        </dl>
      </section>
      <section className="mobile-maid-calls">
        <h3>最近模型调用</h3>
        {calls.length ? calls.slice(0, 5).map((call, index) => <article key={value(call, "Id", "id", index)}>
          <div><strong>{value(call, "SourceName", "sourceName", "未知业务")}</strong><span>{value(call, "Model", "model", "未知模型")}</span></div>
          <time>{dateTime(value(call, "CreatedAt", "createdAt", ""))}</time>
        </article>) : <p>暂无模型调用记录</p>}
      </section>
    </>}
  </section>;
}
