import { useCallback, useEffect, useRef, useState } from "react";
import { ArrowClockwise, Copy, DownloadSimple, File, PaperPlaneTilt, TrayArrowUp, Trash } from "@phosphor-icons/react";
import { readJsonResponse } from "./apiResponse";
import "./FileTransfer.css";

const API = "/api/file-transfer";
const PREVIEWABLE_IMAGE = /\.(?:avif|bmp|gif|jpe?g|png|webp)$/i;

function formatSize(bytes) {
  const value = Number(bytes || 0);
  if (value < 1024) return `${value} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let size = value / 1024;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) { size /= 1024; unit += 1; }
  return `${size >= 100 ? size.toFixed(0) : size >= 10 ? size.toFixed(1) : size.toFixed(2)} ${units[unit]}`;
}

function uploadFile(file, onProgress) {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("POST", `${API}/upload`);
    request.responseType = "json";
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100));
    };
    request.onload = () => {
      const body = request.response;
      if (request.status >= 200 && request.status < 300 && body?.code === 200) resolve(body.data);
      else reject(new Error(body?.message || `上传失败 · HTTP ${request.status}`));
    };
    request.onerror = () => reject(new Error("上传连接中断"));
    const form = new FormData();
    form.append("file", file, file.name);
    request.send(form);
  });
}

export default function FileTransfer() {
  const inputRef = useRef(null);
  const [files, setFiles] = useState([]);
  const [state, setState] = useState("loading");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [dragActive, setDragActive] = useState(false);
  const [uploading, setUploading] = useState({ active: false, name: "", index: 0, total: 0, percent: 0 });
  const [deleting, setDeleting] = useState("");
  const [selected, setSelected] = useState(() => new Set());
  const [transferText, setTransferText] = useState("");
  const [textLoading, setTextLoading] = useState(true);
  const [textSaving, setTextSaving] = useState(false);

  const load = useCallback(async () => {
    setState("loading");
    setError("");
    try {
      const response = await fetch(`${API}/files`);
      const body = await readJsonResponse(response, "文件列表响应异常");
      if (!response.ok || body.code !== 200) throw new Error(body.message || `读取失败 · HTTP ${response.status}`);
      const nextFiles = body.data || [];
      setFiles(nextFiles);
      setSelected((current) => {
        const available = new Set(nextFiles.map((file) => file.fileName));
        return new Set(Array.from(current).filter((fileName) => available.has(fileName)));
      });
      setState("ready");
    } catch (exception) {
      setError(exception.message);
      setState("error");
    }
  }, []);

  const loadText = useCallback(async () => {
    setTextLoading(true);
    try {
      const response = await fetch(`${API}/text`);
      const body = await readJsonResponse(response, "文本中转响应异常");
      if (!response.ok || body.code !== 200) throw new Error(body.message || `读取失败 · HTTP ${response.status}`);
      setTransferText(body.data?.text || "");
    } catch (exception) {
      setError(exception.message);
    } finally {
      setTextLoading(false);
    }
  }, []);

  useEffect(() => { load(); loadText(); }, [load, loadText]);

  const submitFiles = async (selected) => {
    const next = Array.from(selected || []);
    if (!next.length || uploading.active) return;
    setError("");
    setNotice("");
    try {
      for (let index = 0; index < next.length; index += 1) {
        const file = next[index];
        setUploading({ active: true, name: file.name, index: index + 1, total: next.length, percent: 0 });
        await uploadFile(file, (percent) => setUploading((current) => ({ ...current, percent })));
      }
      setNotice(`已上传 ${next.length} 个文件`);
      await load();
    } catch (exception) {
      setError(exception.message);
    } finally {
      setUploading({ active: false, name: "", index: 0, total: 0, percent: 0 });
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  const remove = async (fileName) => {
    if (!window.confirm(`确定删除“${fileName}”吗？`)) return;
    setDeleting(fileName);
    setError("");
    setNotice("");
    try {
      const response = await fetch(`${API}/${encodeURIComponent(fileName)}`, { method: "DELETE" });
      const body = await readJsonResponse(response, "删除响应异常");
      if (!response.ok || body.code !== 200) throw new Error(body.message || `删除失败 · HTTP ${response.status}`);
      setNotice(`已删除 ${fileName}`);
      await load();
    } catch (exception) {
      setError(exception.message);
    } finally {
      setDeleting("");
    }
  };

  const allSelected = files.length > 0 && selected.size === files.length;
  const toggleAll = () => setSelected(allSelected ? new Set() : new Set(files.map((file) => file.fileName)));
  const toggleFile = (fileName) => setSelected((current) => {
    const next = new Set(current);
    if (next.has(fileName)) next.delete(fileName);
    else next.add(fileName);
    return next;
  });
  const downloadSelected = () => {
    if (!selected.size) return;
    const form = document.createElement("form");
    form.method = "POST";
    form.action = `${API}/download-batch`;
    form.hidden = true;
    selected.forEach((fileName) => {
      const input = document.createElement("input");
      input.type = "hidden";
      input.name = "fileName";
      input.value = fileName;
      form.appendChild(input);
    });
    document.body.appendChild(form);
    form.submit();
    form.remove();
  };
  const sendText = async () => {
    setTextSaving(true);
    setError("");
    setNotice("");
    try {
      const response = await fetch(`${API}/text`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: transferText }),
      });
      const body = await readJsonResponse(response, "文本发送响应异常");
      if (!response.ok || body.code !== 200) throw new Error(body.message || `发送失败 · HTTP ${response.status}`);
      setNotice("文本已发送到中转站");
    } catch (exception) {
      setError(exception.message);
    } finally {
      setTextSaving(false);
    }
  };
  const copyText = async () => {
    try {
      await navigator.clipboard.writeText(transferText);
      setNotice("文本已复制");
      setError("");
    } catch (exception) {
      setError(exception.message || "复制失败");
    }
  };

  return <section className="file-transfer-page" aria-label="文件中转">
    <div className="file-transfer-toolbar">
      <div><strong>服务器文件夹</strong><span>同名文件会直接覆盖，仅手动删除</span></div>
      <button type="button" onClick={() => { load(); loadText(); }} disabled={state === "loading" || uploading.active}><ArrowClockwise />刷新</button>
    </div>

    <label className={`file-transfer-dropzone${dragActive ? " is-dragging" : ""}${uploading.active ? " is-uploading" : ""}`}
      onDragEnter={(event) => { event.preventDefault(); setDragActive(true); }}
      onDragOver={(event) => event.preventDefault()}
      onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setDragActive(false); }}
      onDrop={(event) => { event.preventDefault(); setDragActive(false); submitFiles(event.dataTransfer.files); }}>
      <input ref={inputRef} type="file" multiple disabled={uploading.active} onChange={(event) => submitFiles(event.target.files)} />
      <TrayArrowUp weight="duotone" />
      <strong>{uploading.active ? uploading.name : "点击选择文件，或拖到这里"}</strong>
      <span>{uploading.active ? `正在上传 ${uploading.index} / ${uploading.total}` : "不限类型、大小和数量"}</span>
      {uploading.active && <progress max="100" value={uploading.percent} aria-label={`${uploading.name} 上传进度`}>{uploading.percent}%</progress>}
      {uploading.active && <em>{uploading.percent}%</em>}
    </label>

    {(error || notice) && <div className={`file-transfer-message${error ? " is-error" : " is-success"}`} role={error ? "alert" : "status"}>{error || notice}</div>}

    <div className="file-transfer-list-card">
      <header><div><strong>现有文件</strong><span>{state === "ready" ? `${files.length} 个` : "读取中"}</span></div>
        <button type="button" onClick={downloadSelected} disabled={!selected.size}><DownloadSimple />批量下载{selected.size ? `（${selected.size}）` : ""}</button>
      </header>
      {state === "loading" && <div className="file-transfer-state" role="status">正在读取服务器文件…</div>}
      {state === "error" && <div className="file-transfer-state is-error"><span>文件列表读取失败</span><button type="button" onClick={load}>重新加载</button></div>}
      {state === "ready" && files.length === 0 && <div className="file-transfer-state"><File weight="duotone" /><span>服务器文件夹为空</span></div>}
      {state === "ready" && files.length > 0 && <div className="file-transfer-table-wrap"><table>
        <thead><tr><th className="file-transfer-select"><input type="checkbox" checked={allSelected} onChange={toggleAll} aria-label="全选文件" /></th><th>文件名</th><th>大小</th><th>上传时间</th><th>操作</th></tr></thead>
        <tbody>{files.map((file) => <tr key={file.fileName}>
          <td className="file-transfer-select"><input type="checkbox" checked={selected.has(file.fileName)} onChange={() => toggleFile(file.fileName)} aria-label={`选择 ${file.fileName}`} /></td>
          <td><div className="file-transfer-file-cell">
            {PREVIEWABLE_IMAGE.test(file.fileName)
              ? <img className="file-transfer-thumbnail" src={`${API}/preview/${encodeURIComponent(file.fileName)}`} alt="" loading="lazy" />
              : <File />}
            <span title={file.fileName}>{file.fileName}</span>
          </div></td>
          <td>{formatSize(file.fileSize)}</td>
          <td>{new Date(file.uploadedAt).toLocaleString("zh-CN", { hour12: false })}</td>
          <td><div className="file-transfer-actions">
            <a href={`${API}/download/${encodeURIComponent(file.fileName)}`} download={file.fileName}><DownloadSimple />下载</a>
            <button type="button" className="danger" disabled={deleting === file.fileName} onClick={() => remove(file.fileName)}><Trash />{deleting === file.fileName ? "删除中" : "删除"}</button>
          </div></td>
        </tr>)}</tbody>
      </table></div>}
    </div>

    <form className="file-transfer-text-card" onSubmit={(event) => { event.preventDefault(); sendText(); }}>
      <header><div><strong>文本中转</strong><span>保存最新一份文本，发送后另一台设备刷新即可复制</span></div>
        <button type="button" onClick={copyText} disabled={textLoading || !transferText}><Copy />复制</button>
      </header>
      <div><textarea value={transferText} onChange={(event) => setTransferText(event.target.value)} disabled={textLoading || textSaving} aria-label="中转文本" placeholder={textLoading ? "正在读取…" : "在这里粘贴要中转的文本"} />
        <button type="submit" disabled={textLoading || textSaving}><PaperPlaneTilt />{textSaving ? "发送中" : "发送"}</button>
      </div>
    </form>
  </section>;
}
