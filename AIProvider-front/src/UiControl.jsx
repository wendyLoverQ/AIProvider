import { useState } from "react";
import { ArrowCounterClockwise, CheckCircle, Palette } from "@phosphor-icons/react";
import { DEFAULT_UI_THEME, isUiThemePreset, readUiTheme, saveUiTheme, UI_THEME_PRESETS } from "./uiTheme";
import "./UiControl.css";
import "./UiControlPresets.css";

const GROUPS = [
  ["页面与卡片", [["--bg-page", "页面背景"], ["--bg-sidebar", "侧边栏"], ["--bg-surface", "主面板"], ["--bg-card", "卡片"], ["--bg-card-hover", "卡片悬停"], ["--bg-selected", "选中状态"]]],
  ["边框与文字", [["--border-subtle", "弱边框"], ["--border-normal", "普通边框"], ["--border-focus", "聚焦边框"], ["--text-primary", "主要文字"], ["--text-secondary", "次要文字"], ["--text-muted", "弱化文字"]]],
  ["主色与点缀", [["--accent-primary", "主色"], ["--accent-secondary", "副色"], ["--accent-soft", "柔和色"], ["--accent-blue", "蓝色"], ["--accent-cyan", "青色"], ["--accent-mint", "薄荷色"], ["--accent-yellow", "黄色"], ["--accent-red", "红色"]]],
];

export default function UiControl() {
  const [theme, setTheme] = useState(readUiTheme);
  const [saved, setSaved] = useState(true);
  const update = (name, value) => {
    const next = { ...theme, [name]: value };
    setTheme(next); saveUiTheme(next); setSaved(true);
  };
  const selectPreset = (preset) => { const next = { ...preset.theme }; setTheme(next); saveUiTheme(next); setSaved(true); };
  const reset = () => { const next = { ...DEFAULT_UI_THEME }; setTheme(next); saveUiTheme(next); setSaved(true); };
  return <div className="ui-control-page">
    <section className="ui-control-hero">
      <div><span>GLOBAL APPEARANCE</span><h2><Palette />全局 UI 控制</h2><p>这里的修改会实时应用到首页、图像工坊、图片编辑、视频编辑、市场行情、Prompt、我的女仆、监控、链上工具、Twitter、UI 控制和系统设置。</p></div>
      <div className="ui-control-state"><CheckCircle />{saved ? "已自动保存" : "等待保存"}</div>
    </section>
    <div className="ui-control-layout">
      <section className="ui-token-panel">
        <div className="ui-token-group ui-preset-group"><h3>主题预设</h3><p>点击即可全站切换，选择后仍可继续微调。</p><div className="ui-preset-grid">
          {UI_THEME_PRESETS.map((preset) => { const active = isUiThemePreset(theme, preset); return <button type="button" className={active ? "active" : ""} aria-pressed={active} onClick={() => selectPreset(preset)} key={preset.id}>
            <span className="ui-preset-colors">{["--bg-page", "--bg-card", "--accent-primary", "--accent-secondary", "--accent-cyan"].map((name) => <i key={name} style={{ background: preset.theme[name] }} />)}</span>
            <strong>{preset.name}</strong><small>{preset.description}</small>{active && <b><CheckCircle />正在使用</b>}
          </button>; })}
        </div></div>
        {GROUPS.map(([title, tokens]) => <div className="ui-token-group" key={title}><h3>{title}</h3><div className="ui-color-grid">
          {tokens.map(([name, label]) => <label key={name}><span>{label}<small>{name}</small></span><div><input type="color" value={theme[name]} onChange={(e) => update(name, e.target.value)} /><input key={theme[name]} defaultValue={theme[name]} maxLength="7" onBlur={(e) => /^#[0-9a-fA-F]{6}$/.test(e.target.value) ? update(name, e.target.value) : (e.target.value = theme[name])} /></div></label>)}
        </div></div>)}
        <div className="ui-token-group"><h3>组件形态</h3><div className="ui-range-grid">
          <label><span>卡片圆角<b>{theme["--card-radius"]}</b></span><input type="range" min="8" max="28" value={parseInt(theme["--card-radius"])} onChange={(e) => update("--card-radius", `${e.target.value}px`)} /></label>
          <label><span>控件圆角<b>{theme["--control-radius"]}</b></span><input type="range" min="4" max="20" value={parseInt(theme["--control-radius"])} onChange={(e) => update("--control-radius", `${e.target.value}px`)} /></label>
          <label><span>卡片阴影<b>{theme["--card-shadow-size"]}</b></span><input type="range" min="0" max="60" value={parseInt(theme["--card-shadow-size"])} onChange={(e) => update("--card-shadow-size", `${e.target.value}px`)} /></label>
        </div></div>
        <button className="ui-reset" onClick={reset}><ArrowCounterClockwise />恢复粉紫默认主题</button>
      </section>
      <aside className="ui-live-preview">
        <span>实时预览</span><h3>统一主题组件</h3><p>卡片、按钮、输入框和选中状态共用同一套全局变量。</p>
        <div className="ui-preview-selected">当前选中菜单</div>
        <input placeholder="输入框预览" />
        <button>主要按钮</button>
        <div className="ui-preview-swatches">{["--accent-primary", "--accent-secondary", "--accent-blue", "--accent-cyan", "--accent-mint", "--accent-yellow", "--accent-red"].map((name) => <i key={name} style={{ background: theme[name] }} />)}</div>
      </aside>
    </div>
  </div>;
}
