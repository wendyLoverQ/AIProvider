export const UI_THEME_STORAGE_KEY = "aimaid_global_ui_theme";

export const DEFAULT_UI_THEME = {
  "--bg-page": "#19131a", "--bg-sidebar": "#120e13", "--bg-surface": "#221923", "--bg-card": "#2b202c", "--bg-card-hover": "#352637", "--bg-selected": "#4a2943",
  "--border-subtle": "#3a2c3b", "--border-normal": "#513b50", "--border-focus": "#ff8fbe",
  "--text-primary": "#fff3f8", "--text-secondary": "#d8bcc9", "--text-muted": "#967d89",
  "--accent-primary": "#ff8fbe", "--accent-secondary": "#c69cff", "--accent-soft": "#ffb8d4", "--accent-blue": "#82b7ff", "--accent-cyan": "#6fe2df", "--accent-mint": "#72ddb1", "--accent-yellow": "#ffc978", "--accent-red": "#ff718f",
  "--card-radius": "16px", "--control-radius": "10px", "--card-shadow-size": "30px",
};

const shape = { "--card-radius": "16px", "--control-radius": "10px", "--card-shadow-size": "30px" };
const preset = (id, name, description, theme) => ({ id, name, description, theme: { ...theme, ...shape } });

export const UI_THEME_PRESETS = [
  { id: "powder-pink", name: "粉紫糖果", description: "温暖柔和的粉紫渐变", theme: DEFAULT_UI_THEME },
  preset("catppuccin-mocha", "Catppuccin Mocha", "柔和、低刺激的粉彩暗色", {
    "--bg-page": "#1e1e2e", "--bg-sidebar": "#11111b", "--bg-surface": "#181825", "--bg-card": "#313244", "--bg-card-hover": "#45475a", "--bg-selected": "#45475a", "--border-subtle": "#45475a", "--border-normal": "#6c7086", "--border-focus": "#cba6f7", "--text-primary": "#cdd6f4", "--text-secondary": "#bac2de", "--text-muted": "#7f849c", "--accent-primary": "#cba6f7", "--accent-secondary": "#f5c2e7", "--accent-soft": "#b4befe", "--accent-blue": "#89b4fa", "--accent-cyan": "#89dceb", "--accent-mint": "#a6e3a1", "--accent-yellow": "#f9e2af", "--accent-red": "#f38ba8",
  }),
  preset("dracula", "Dracula", "经典、高对比的紫粉暗色", {
    "--bg-page": "#282a36", "--bg-sidebar": "#191a21", "--bg-surface": "#21222c", "--bg-card": "#343746", "--bg-card-hover": "#424450", "--bg-selected": "#44475a", "--border-subtle": "#44475a", "--border-normal": "#6272a4", "--border-focus": "#bd93f9", "--text-primary": "#f8f8f2", "--text-secondary": "#d6d6cf", "--text-muted": "#6272a4", "--accent-primary": "#bd93f9", "--accent-secondary": "#ff79c6", "--accent-soft": "#d6acff", "--accent-blue": "#bd93f9", "--accent-cyan": "#8be9fd", "--accent-mint": "#50fa7b", "--accent-yellow": "#f1fa8c", "--accent-red": "#ff5555",
  }),
  preset("nord", "Nord", "克制、清爽的极地冰蓝", {
    "--bg-page": "#2e3440", "--bg-sidebar": "#242933", "--bg-surface": "#3b4252", "--bg-card": "#434c5e", "--bg-card-hover": "#4c566a", "--bg-selected": "#3f5368", "--border-subtle": "#4c566a", "--border-normal": "#5e6b7d", "--border-focus": "#88c0d0", "--text-primary": "#eceff4", "--text-secondary": "#d8dee9", "--text-muted": "#8b96a8", "--accent-primary": "#88c0d0", "--accent-secondary": "#b48ead", "--accent-soft": "#8fbcbb", "--accent-blue": "#81a1c1", "--accent-cyan": "#88c0d0", "--accent-mint": "#a3be8c", "--accent-yellow": "#ebcb8b", "--accent-red": "#bf616a",
  }),
  preset("tokyo-night", "Tokyo Night", "深蓝底色与都市霓虹点缀", {
    "--bg-page": "#1a1b26", "--bg-sidebar": "#16161e", "--bg-surface": "#1f2335", "--bg-card": "#24283b", "--bg-card-hover": "#292e42", "--bg-selected": "#33467c", "--border-subtle": "#3b4261", "--border-normal": "#545c7e", "--border-focus": "#7aa2f7", "--text-primary": "#c0caf5", "--text-secondary": "#a9b1d6", "--text-muted": "#565f89", "--accent-primary": "#7aa2f7", "--accent-secondary": "#bb9af7", "--accent-soft": "#7dcfff", "--accent-blue": "#7aa2f7", "--accent-cyan": "#7dcfff", "--accent-mint": "#9ece6a", "--accent-yellow": "#e0af68", "--accent-red": "#f7768e",
  }),
  preset("gruvbox", "Gruvbox", "复古温暖、耐看的大地色", {
    "--bg-page": "#282828", "--bg-sidebar": "#1d2021", "--bg-surface": "#32302f", "--bg-card": "#3c3836", "--bg-card-hover": "#504945", "--bg-selected": "#665c54", "--border-subtle": "#504945", "--border-normal": "#665c54", "--border-focus": "#fe8019", "--text-primary": "#ebdbb2", "--text-secondary": "#d5c4a1", "--text-muted": "#928374", "--accent-primary": "#fe8019", "--accent-secondary": "#d3869b", "--accent-soft": "#fabd2f", "--accent-blue": "#83a598", "--accent-cyan": "#8ec07c", "--accent-mint": "#b8bb26", "--accent-yellow": "#fabd2f", "--accent-red": "#fb4934",
  }),
  preset("one-dark", "One Dark", "现代中性、清晰的编辑器风格", {
    "--bg-page": "#282c34", "--bg-sidebar": "#21252b", "--bg-surface": "#2c313a", "--bg-card": "#333842", "--bg-card-hover": "#3e4451", "--bg-selected": "#3e4451", "--border-subtle": "#3e4451", "--border-normal": "#5c6370", "--border-focus": "#61afef", "--text-primary": "#abb2bf", "--text-secondary": "#9da5b4", "--text-muted": "#5c6370", "--accent-primary": "#61afef", "--accent-secondary": "#c678dd", "--accent-soft": "#56b6c2", "--accent-blue": "#61afef", "--accent-cyan": "#56b6c2", "--accent-mint": "#98c379", "--accent-yellow": "#e5c07b", "--accent-red": "#e06c75",
  }),
  preset("rose-pine", "Rosé Pine", "玫瑰、松绿与鸢尾紫的静谧暗色", {
    "--bg-page": "#191724", "--bg-sidebar": "#12101c", "--bg-surface": "#1f1d2e", "--bg-card": "#26233a", "--bg-card-hover": "#2f2b46", "--bg-selected": "#403d52", "--border-subtle": "#2f2b46", "--border-normal": "#524f67", "--border-focus": "#c4a7e7", "--text-primary": "#e0def4", "--text-secondary": "#908caa", "--text-muted": "#6e6a86", "--accent-primary": "#c4a7e7", "--accent-secondary": "#eb6f92", "--accent-soft": "#ebbcba", "--accent-blue": "#31748f", "--accent-cyan": "#9ccfd8", "--accent-mint": "#3e8fb0", "--accent-yellow": "#f6c177", "--accent-red": "#eb6f92",
  }),
  preset("kanagawa-wave", "Kanagawa Wave", "浮世绘海浪般沉稳温暖的深色", {
    "--bg-page": "#1f1f28", "--bg-sidebar": "#16161d", "--bg-surface": "#252535", "--bg-card": "#2a2a37", "--bg-card-hover": "#363646", "--bg-selected": "#2d4f67", "--border-subtle": "#363646", "--border-normal": "#54546d", "--border-focus": "#7e9cd8", "--text-primary": "#dcd7ba", "--text-secondary": "#c8c093", "--text-muted": "#727169", "--accent-primary": "#7e9cd8", "--accent-secondary": "#957fb8", "--accent-soft": "#d27e99", "--accent-blue": "#7fb4ca", "--accent-cyan": "#7aa89f", "--accent-mint": "#98bb6c", "--accent-yellow": "#e6c384", "--accent-red": "#e46876",
  }),
  preset("everforest", "Everforest", "森林苔藓与暖木色的低刺激暗色", {
    "--bg-page": "#2d353b", "--bg-sidebar": "#232a2e", "--bg-surface": "#343f44", "--bg-card": "#3d484d", "--bg-card-hover": "#475258", "--bg-selected": "#4f5b58", "--border-subtle": "#475258", "--border-normal": "#56635f", "--border-focus": "#a7c080", "--text-primary": "#d3c6aa", "--text-secondary": "#9da9a0", "--text-muted": "#7a8478", "--accent-primary": "#a7c080", "--accent-secondary": "#d699b6", "--accent-soft": "#e67e80", "--accent-blue": "#7fbbb3", "--accent-cyan": "#83c092", "--accent-mint": "#a7c080", "--accent-yellow": "#dbbc7f", "--accent-red": "#e67e80",
  }),
  preset("ayu-mirage", "Ayu Mirage", "深海蓝灰底与金橙高光", {
    "--bg-page": "#1f2430", "--bg-sidebar": "#171b24", "--bg-surface": "#242936", "--bg-card": "#2b303d", "--bg-card-hover": "#343b4c", "--bg-selected": "#3d475e", "--border-subtle": "#343b4c", "--border-normal": "#4d5566", "--border-focus": "#ffcc66", "--text-primary": "#cccac2", "--text-secondary": "#b8cfe6", "--text-muted": "#707a8c", "--accent-primary": "#ffcc66", "--accent-secondary": "#d4bfff", "--accent-soft": "#f29e74", "--accent-blue": "#73d0ff", "--accent-cyan": "#95e6cb", "--accent-mint": "#bae67e", "--accent-yellow": "#ffd580", "--accent-red": "#f28779",
  }),
  preset("solarized-dark", "Solarized Dark", "经典蓝绿基底与均衡对比", {
    "--bg-page": "#002b36", "--bg-sidebar": "#00212b", "--bg-surface": "#073642", "--bg-card": "#0b3d49", "--bg-card-hover": "#124b58", "--bg-selected": "#165766", "--border-subtle": "#124b58", "--border-normal": "#586e75", "--border-focus": "#268bd2", "--text-primary": "#fdf6e3", "--text-secondary": "#93a1a1", "--text-muted": "#657b83", "--accent-primary": "#268bd2", "--accent-secondary": "#6c71c4", "--accent-soft": "#d33682", "--accent-blue": "#268bd2", "--accent-cyan": "#2aa198", "--accent-mint": "#859900", "--accent-yellow": "#b58900", "--accent-red": "#dc322f",
  }),
  preset("material-ocean", "Material Ocean", "深邃海洋底色与明亮语义色", {
    "--bg-page": "#0f111a", "--bg-sidebar": "#090b10", "--bg-surface": "#151923", "--bg-card": "#1a1f2b", "--bg-card-hover": "#222838", "--bg-selected": "#2c3346", "--border-subtle": "#222838", "--border-normal": "#3b4257", "--border-focus": "#82aaff", "--text-primary": "#babed8", "--text-secondary": "#a6accd", "--text-muted": "#525975", "--accent-primary": "#82aaff", "--accent-secondary": "#c792ea", "--accent-soft": "#f78c6c", "--accent-blue": "#82aaff", "--accent-cyan": "#89ddff", "--accent-mint": "#c3e88d", "--accent-yellow": "#ffcb6b", "--accent-red": "#f07178",
  }),
  preset("night-owl", "Night Owl", "夜蓝背景与高辨识度冷色高光", {
    "--bg-page": "#011627", "--bg-sidebar": "#000c1d", "--bg-surface": "#071d31", "--bg-card": "#0b253a", "--bg-card-hover": "#102f49", "--bg-selected": "#173b57", "--border-subtle": "#102a44", "--border-normal": "#294a63", "--border-focus": "#82aaff", "--text-primary": "#d6deeb", "--text-secondary": "#addb67", "--text-muted": "#637777", "--accent-primary": "#82aaff", "--accent-secondary": "#c792ea", "--accent-soft": "#ffcb8b", "--accent-blue": "#82aaff", "--accent-cyan": "#7fdbca", "--accent-mint": "#addb67", "--accent-yellow": "#ecc48d", "--accent-red": "#ef5350",
  }),
  preset("github-dark", "GitHub Dark", "中性、清晰、适合长时间阅读", {
    "--bg-page": "#0d1117", "--bg-sidebar": "#010409", "--bg-surface": "#161b22", "--bg-card": "#1c2128", "--bg-card-hover": "#262c36", "--bg-selected": "#1f3b5b", "--border-subtle": "#21262d", "--border-normal": "#30363d", "--border-focus": "#58a6ff", "--text-primary": "#f0f6fc", "--text-secondary": "#c9d1d9", "--text-muted": "#8b949e", "--accent-primary": "#58a6ff", "--accent-secondary": "#bc8cff", "--accent-soft": "#ff7b72", "--accent-blue": "#58a6ff", "--accent-cyan": "#39c5cf", "--accent-mint": "#3fb950", "--accent-yellow": "#d29922", "--accent-red": "#f85149",
  }),
];

export function isUiThemePreset(theme, candidate) {
  return Object.entries(candidate.theme).every(([key, value]) => theme[key] === value);
}

export function readUiTheme() {
  try { return { ...DEFAULT_UI_THEME, ...JSON.parse(localStorage.getItem(UI_THEME_STORAGE_KEY) || "{}") }; }
  catch { return { ...DEFAULT_UI_THEME }; }
}

export function applyUiTheme(theme) {
  Object.entries({ ...DEFAULT_UI_THEME, ...theme }).forEach(([name, value]) => document.documentElement.style.setProperty(name, value));
}

export function saveUiTheme(theme) {
  localStorage.setItem(UI_THEME_STORAGE_KEY, JSON.stringify(theme));
  applyUiTheme(theme);
}

export function applyStoredUiTheme() { applyUiTheme(readUiTheme()); }
