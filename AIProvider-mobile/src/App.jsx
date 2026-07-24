import { useEffect, useState } from "react";
import {
  ChatsCircle,
  FolderSimple,
  Heart,
  List,
  Palette,
  Pulse,
  Star,
  X,
} from "@phosphor-icons/react";
import FavoriteMediaLibrary from "../../AIProvider-front/src/FavoriteMediaLibrary";
import MonitorCenter from "../../AIProvider-front/src/MonitorCenter";
import RemoteCodex from "../../AIProvider-front/src/RemoteCodex";
import UiControl from "../../AIProvider-front/src/UiControl";
import FileTransfer from "../../AIProvider-front/src/FileTransfer";
import MobileMaid from "./MobileMaid";

const WORKSPACES = [
  { key: "favorites", path: "favorites", label: "我的最爱", description: "服务器媒体原件", icon: Star },
  { key: "maid", path: "maid", label: "我的女仆", description: "角色与模型状态", icon: Heart },
  { key: "monitor", path: "monitor", label: "监控中心", description: "服务、资源与费用", icon: Pulse },
  { key: "codex", path: "remote-codex", label: "远程 Codex", description: "远程对话与任务", icon: ChatsCircle },
  { key: "appearance", path: "appearance", label: "UI 控制", description: "主题与组件外观", icon: Palette },
  { key: "files", path: "file-transfer", label: "文件中转", description: "跨设备文件与文本", icon: FolderSimple },
];

const fromPath = () => {
  const relative = window.location.pathname.replace(/^\/mobile\/?/, "").replace(/\/$/, "");
  return WORKSPACES.find((item) => item.path === relative)?.key || "favorites";
};

export default function App() {
  const [view, setView] = useState(fromPath);
  const [menuOpen, setMenuOpen] = useState(false);
  const current = WORKSPACES.find((item) => item.key === view) || WORKSPACES[0];

  const open = (key) => {
    setView(key);
    setMenuOpen(false);
  };

  useEffect(() => {
    const path = `/mobile/${current.path}`;
    if (window.location.pathname !== path) window.history.replaceState({}, "", path);
  }, [current.path]);

  useEffect(() => {
    const onPopState = () => setView(fromPath());
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  return (
    <div className="mobile-app">
      <header className="mobile-topbar">
        <div className="mobile-brand">
          <span>AI</span>
          <div><strong>AIProvider</strong><small>Mobile Control</small></div>
        </div>
        <button type="button" className="mobile-menu-button" onClick={() => setMenuOpen((value) => !value)} aria-label={menuOpen ? "关闭工作区菜单" : "打开工作区菜单"} aria-expanded={menuOpen}>
          {menuOpen ? <X /> : <List />}
        </button>
      </header>

      {menuOpen && (
        <nav className="mobile-drawer" aria-label="移动端工作区">
          {WORKSPACES.map((item) => {
            const Icon = item.icon;
            return <button type="button" key={item.key} className={view === item.key ? "active" : ""} onClick={() => open(item.key)}>
              <Icon weight={view === item.key ? "fill" : "regular"} />
              <span><strong>{item.label}</strong><small>{item.description}</small></span>
            </button>;
          })}
        </nav>
      )}

      <main className={`mobile-workspace mobile-workspace-${view}`}>
        <section className="mobile-page-title">
          <div><span>移动工作区</span><h1>{current.label}</h1><p>{current.description}</p></div>
          <current.icon weight="duotone" />
        </section>
        <div className="mobile-page-content">
          {view === "favorites" && <FavoriteMediaLibrary />}
          {view === "maid" && <MobileMaid />}
          {view === "monitor" && <MonitorCenter />}
          {view === "codex" && <RemoteCodex />}
          {view === "appearance" && <UiControl />}
          {view === "files" && <FileTransfer />}
        </div>
      </main>

      <nav className="mobile-bottom-nav" aria-label="主要工作区">
        {WORKSPACES.map((item) => {
          const Icon = item.icon;
          return <button type="button" key={item.key} className={view === item.key ? "active" : ""} onClick={() => open(item.key)} aria-current={view === item.key ? "page" : undefined}>
            <Icon weight={view === item.key ? "fill" : "regular"} />
            <span>{item.label.replace("远程 ", "")}</span>
          </button>;
        })}
      </nav>
    </div>
  );
}
