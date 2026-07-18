import { useCallback, useEffect, useState } from "react";
import {
  ArrowClockwise,
  BracketsCurly,
  CheckCircle,
  Code,
  Cube,
  Database,
  Gauge,
  MagnifyingGlass,
  ShieldCheck,
  TerminalWindow,
  WarningCircle,
} from "@phosphor-icons/react";
import "./FoundryWorkbench.css";

const API = "/api/foundry";

async function request(path, options) {
  const response = await fetch(`${API}${path}`, options);
  let payload;
  try { payload = await response.json(); }
  catch { throw new Error(`Foundry 服务返回了无法解析的响应（HTTP ${response.status}）`); }
  if (!response.ok || payload.code !== 200) throw new Error(payload.message || `请求失败（HTTP ${response.status}）`);
  return payload.data;
}

const operationLabels = {
  "block-number": "最新区块",
  balance: "账户余额",
  code: "合约字节码",
  call: "合约调用结果",
};

export default function FoundryWorkbench() {
  const [status, setStatus] = useState(null);
  const [statusError, setStatusError] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [address, setAddress] = useState("");
  const [signature, setSignature] = useState("balanceOf(address)(uint256)");
  const [argumentsText, setArgumentsText] = useState("");
  const [result, setResult] = useState(null);
  const [queryError, setQueryError] = useState("");
  const [running, setRunning] = useState("");

  const loadStatus = useCallback(async () => {
    setRefreshing(true);
    try { setStatus(await request("/status")); setStatusError(""); }
    catch (error) { setStatusError(error.message); }
    finally { setRefreshing(false); }
  }, []);

  useEffect(() => { loadStatus(); }, [loadStatus]);

  const run = async (operation) => {
    setRunning(operation);
    setQueryError("");
    try {
      let next;
      if (operation === "block-number") next = await request("/block-number");
      else if (operation === "balance") next = await request(`/balance?address=${encodeURIComponent(address.trim())}`);
      else if (operation === "code") next = await request(`/code?address=${encodeURIComponent(address.trim())}`);
      else {
        const args = argumentsText.split("\n").map((item) => item.trim()).filter(Boolean);
        next = await request("/call", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ address: address.trim(), signature: signature.trim(), arguments: args }),
        });
      }
      setResult(next);
    } catch (error) { setQueryError(error.message); }
    finally { setRunning(""); }
  };

  const castAvailable = status?.tools?.find((tool) => tool.name === "Cast")?.available;
  const queryReady = Boolean(status?.rpcConfigured && castAvailable);

  return <section className="foundry-workbench">
    <header className="foundry-hero">
      <div className="foundry-hero-icon"><Cube weight="duotone" /></div>
      <div>
        <span>FOUNDRY · EVM READ CONSOLE</span>
        <h2>只读链上工作台</h2>
        <p>服务器通过 Foundry Cast 访问固定 Ethereum RPC；不接收私钥，不发送交易，不执行任意命令。</p>
      </div>
      <button type="button" onClick={loadStatus} disabled={refreshing}>
        <ArrowClockwise className={refreshing ? "spin" : ""} />{refreshing ? "检测中" : "检测工具链"}
      </button>
    </header>

    {statusError && <div className="foundry-alert error"><WarningCircle />{statusError}</div>}
    <section className="foundry-status" aria-label="Foundry 工具状态">
      {(status?.tools || ["Forge", "Cast", "Anvil", "Chisel"].map((name) => ({ name }))).map((tool) =>
        <article key={tool.name} className={tool.available ? "online" : "offline"}>
          <TerminalWindow />
          <div><span>{tool.name}</span><strong>{tool.available ? "可用" : refreshing ? "检测中" : "不可用"}</strong><small>{tool.version || "尚未检测到版本"}</small></div>
          {tool.available ? <CheckCircle weight="fill" /> : <WarningCircle />}
        </article>)}
      <article className={status?.rpcConfigured ? "online rpc" : "offline rpc"}>
        <Database />
        <div><span>Ethereum RPC</span><strong>{status?.rpcConfigured ? "已配置" : "未配置"}</strong><small>{status?.rpcHost || "服务器环境变量 FOUNDRY_RPC_URL"}</small></div>
        {status?.rpcConfigured ? <ShieldCheck weight="fill" /> : <WarningCircle />}
      </article>
    </section>

    <div className="foundry-grid">
      <section className="foundry-query-panel">
        <header><div><span>READ OPERATIONS</span><h3>链上查询</h3></div><ShieldCheck /><small>固定 RPC · 只读命令</small></header>
        <button className="foundry-block-button" type="button" onClick={() => run("block-number")} disabled={!queryReady || Boolean(running)}>
          <Gauge /> <span><strong>读取最新区块</strong><small>cast block-number</small></span><b>{running === "block-number" ? "读取中…" : "执行"}</b>
        </button>
        <label className="foundry-field">
          <span>EVM 地址</span>
          <div><MagnifyingGlass /><input value={address} onChange={(event) => setAddress(event.target.value)} placeholder="0x 开头的 40 位地址" spellCheck="false" /></div>
        </label>
        <div className="foundry-actions">
          <button type="button" onClick={() => run("balance")} disabled={!queryReady || Boolean(running)}><Database />{running === "balance" ? "查询中…" : "查询 ETH 余额"}</button>
          <button type="button" onClick={() => run("code")} disabled={!queryReady || Boolean(running)}><Code />{running === "code" ? "查询中…" : "读取合约字节码"}</button>
        </div>
        <div className="foundry-divider"><span>CONTRACT CALL</span></div>
        <label className="foundry-field"><span>Solidity 函数签名</span><div><BracketsCurly /><input value={signature} onChange={(event) => setSignature(event.target.value)} spellCheck="false" /></div></label>
        <label className="foundry-field"><span>参数（每行一个）</span><textarea value={argumentsText} onChange={(event) => setArgumentsText(event.target.value)} rows="4" placeholder={"0x0000…\n1000000000000000000"} spellCheck="false" /></label>
        <button className="foundry-call-button" type="button" onClick={() => run("call")} disabled={!queryReady || Boolean(running)}>
          <TerminalWindow />{running === "call" ? "调用中…" : "执行只读 Cast Call"}
        </button>
      </section>

      <section className="foundry-result-panel" aria-live="polite">
        <header><div><span>COMMAND OUTPUT</span><h3>执行结果</h3></div><i className={queryReady ? "ready" : ""} /></header>
        {queryError ? <div className="foundry-result-error"><WarningCircle /><strong>查询失败</strong><p>{queryError}</p></div>
          : result ? <div className="foundry-result-value"><span>{operationLabels[result.operation] || result.operation}</span><pre>{result.result}</pre><small>执行时间 {result.executedAt ? new Date(result.executedAt).toLocaleString("zh-CN", { hour12: false }) : "—"}</small></div>
            : <div className="foundry-result-empty"><TerminalWindow /><strong>等待链上查询</strong><p>选择左侧操作后，Cast 的真实输出会显示在这里。</p></div>}
      </section>
    </div>
  </section>;
}
