export const UI_THEME_STORAGE_KEY = "aimaid_global_ui_theme";

const ACCESSIBLE_DERIVED_TOKENS = {
  "--text-muted-readable": "color-mix(in srgb, var(--text-muted) 55%, var(--text-primary))",
  "--border-interactive": "color-mix(in srgb, var(--border-normal) 42%, var(--text-primary))",
};

export const DEFAULT_UI_THEME = {
  "--bg-page": "#19131a", "--bg-sidebar": "#120e13", "--bg-surface": "#221923", "--bg-card": "#2b202c", "--bg-card-hover": "#352637", "--bg-selected": "#4a2943",
  "--border-subtle": "#3a2c3b", "--border-normal": "#6f5269", "--border-focus": "#ff8fbe",
  "--text-primary": "#fff3f8", "--text-secondary": "#d8bcc9", "--text-muted": "#b39aa6",
  "--accent-primary": "#ff8fbe", "--accent-secondary": "#c69cff", "--accent-soft": "#ffb8d4", "--accent-blue": "#82b7ff", "--accent-cyan": "#6fe2df", "--accent-mint": "#72ddb1", "--accent-yellow": "#ffc978", "--accent-red": "#ff718f",
  "--card-radius": "16px", "--control-radius": "10px", "--card-shadow-size": "30px",
  ...ACCESSIBLE_DERIVED_TOKENS,
};

const shape = { "--card-radius": "16px", "--control-radius": "10px", "--card-shadow-size": "30px", ...ACCESSIBLE_DERIVED_TOKENS };
const preset = (id, name, description, theme) => ({ id, name, description, theme: { ...theme, ...shape } });
export const UI_THEME_PRESETS = [
  { id: "powder-pink", name: "粉紫糖果", description: "温暖柔和的粉紫渐变", theme: DEFAULT_UI_THEME },
  preset("dracula", "Dracula", "经典、高对比的紫粉暗色", {
    "--bg-page": "#282a36", "--bg-sidebar": "#191a21", "--bg-surface": "#21222c", "--bg-card": "#343746", "--bg-card-hover": "#424450", "--bg-selected": "#4b3f60", "--border-subtle": "#44475a", "--border-normal": "#737da5", "--border-focus": "#bd93f9", "--text-primary": "#f8f8f2", "--text-secondary": "#e1e1da", "--text-muted": "#a6a8b8", "--accent-primary": "#bd93f9", "--accent-secondary": "#ff79c6", "--accent-soft": "#d6acff", "--accent-blue": "#8aa7ff", "--accent-cyan": "#8be9fd", "--accent-mint": "#50fa7b", "--accent-yellow": "#f1fa8c", "--accent-red": "#ff5555",
  }),
  preset("nord", "Nord", "克制、清爽的极地冰蓝", {
    "--bg-page": "#242b36", "--bg-sidebar": "#1b212a", "--bg-surface": "#323c4a", "--bg-card": "#3d4959", "--bg-card-hover": "#4b596c", "--bg-selected": "#315367", "--border-subtle": "#4c596a", "--border-normal": "#718096", "--border-focus": "#88c0d0", "--text-primary": "#f4f7fb", "--text-secondary": "#d8dee9", "--text-muted": "#afbaca", "--accent-primary": "#88c0d0", "--accent-secondary": "#b48ead", "--accent-soft": "#8fbcbb", "--accent-blue": "#81a1c1", "--accent-cyan": "#8fdae8", "--accent-mint": "#a3be8c", "--accent-yellow": "#ebcb8b", "--accent-red": "#d87882",
  }),
  preset("tokyo-night", "Tokyo Night", "深蓝底色与都市霓虹点缀", {
    "--bg-page": "#111525", "--bg-sidebar": "#090c18", "--bg-surface": "#1b2035", "--bg-card": "#252b46", "--bg-card-hover": "#303858", "--bg-selected": "#334f91", "--border-subtle": "#3b4261", "--border-normal": "#66709a", "--border-focus": "#7aa2f7", "--text-primary": "#d5dcff", "--text-secondary": "#b9c2eb", "--text-muted": "#8d96bd", "--accent-primary": "#7aa2f7", "--accent-secondary": "#bb9af7", "--accent-soft": "#7dcfff", "--accent-blue": "#7aa2f7", "--accent-cyan": "#7dcfff", "--accent-mint": "#9ece6a", "--accent-yellow": "#e0af68", "--accent-red": "#f7768e",
  }),
  preset("gruvbox", "Gruvbox", "复古温暖、耐看的大地色", {
    "--bg-page": "#28251f", "--bg-sidebar": "#1d1b17", "--bg-surface": "#34302a", "--bg-card": "#453d34", "--bg-card-hover": "#574c40", "--bg-selected": "#6a4b2c", "--border-subtle": "#574c40", "--border-normal": "#7c6f64", "--border-focus": "#fe8019", "--text-primary": "#fbf1c7", "--text-secondary": "#e5d4ad", "--text-muted": "#bcab95", "--accent-primary": "#fe8019", "--accent-secondary": "#d3869b", "--accent-soft": "#fabd2f", "--accent-blue": "#83a598", "--accent-cyan": "#8ec07c", "--accent-mint": "#b8bb26", "--accent-yellow": "#fabd2f", "--accent-red": "#fb4934",
  }),
  preset("everforest", "Everforest", "森林苔藓与暖木色的低刺激暗色", {
    "--bg-page": "#202b27", "--bg-sidebar": "#18211e", "--bg-surface": "#2d3a34", "--bg-card": "#394940", "--bg-card-hover": "#465a4e", "--bg-selected": "#435d49", "--border-subtle": "#4d5f56", "--border-normal": "#6b7d73", "--border-focus": "#a7c080", "--text-primary": "#e6dcc3", "--text-secondary": "#c7c3ad", "--text-muted": "#aeb9ac", "--accent-primary": "#a7c080", "--accent-secondary": "#d699b6", "--accent-soft": "#e67e80", "--accent-blue": "#7fbbb3", "--accent-cyan": "#83c092", "--accent-mint": "#b7d38d", "--accent-yellow": "#dbbc7f", "--accent-red": "#e67e80",
  }),
  preset("solarized-dark", "Solarized Dark", "经典蓝绿基底与均衡对比", {
    "--bg-page": "#002b36", "--bg-sidebar": "#001d26", "--bg-surface": "#073642", "--bg-card": "#0d4652", "--bg-card-hover": "#145663", "--bg-selected": "#5b4c12", "--border-subtle": "#1f5964", "--border-normal": "#6c848b", "--border-focus": "#d3a52a", "--text-primary": "#fdf6e3", "--text-secondary": "#d3ccba", "--text-muted": "#a5b1ae", "--accent-primary": "#d3a52a", "--accent-secondary": "#6c71c4", "--accent-soft": "#d33682", "--accent-blue": "#268bd2", "--accent-cyan": "#2aa198", "--accent-mint": "#9eb40d", "--accent-yellow": "#d3a52a", "--accent-red": "#dc322f",
  }),
  preset("github-dark", "GitHub Dark", "中性、清晰、适合长时间阅读", {
    "--bg-page": "#0d1117", "--bg-sidebar": "#010409", "--bg-surface": "#161b22", "--bg-card": "#1c2128", "--bg-card-hover": "#262c36", "--bg-selected": "#1f3b5b", "--border-subtle": "#30363d", "--border-normal": "#57606a", "--border-focus": "#58a6ff", "--text-primary": "#f0f6fc", "--text-secondary": "#c9d1d9", "--text-muted": "#9da7b1", "--accent-primary": "#58a6ff", "--accent-secondary": "#bc8cff", "--accent-soft": "#ff7b72", "--accent-blue": "#58a6ff", "--accent-cyan": "#39c5cf", "--accent-mint": "#3fb950", "--accent-yellow": "#d29922", "--accent-red": "#f85149",
  }),
  preset("monokai-pro-ce", "Monokai Pro CE", "暖黑底色与高饱和粉橙强调", {
    "--bg-page": "#19181a", "--bg-sidebar": "#111012", "--bg-surface": "#221f22", "--bg-card": "#2d2a2e", "--bg-card-hover": "#403e41", "--bg-selected": "#5a3040", "--border-subtle": "#403e41", "--border-normal": "#727072", "--border-focus": "#ff6188", "--text-primary": "#fcfcfa", "--text-secondary": "#d5d4d2", "--text-muted": "#a9a7a8", "--accent-primary": "#ff6188", "--accent-secondary": "#fc9867", "--accent-soft": "#ab9df2", "--accent-blue": "#78dce8", "--accent-cyan": "#78dce8", "--accent-mint": "#a9dc76", "--accent-yellow": "#ffd866", "--accent-red": "#ff6188",
  }),
  preset("oxocarbon", "Oxocarbon", "工业黑灰与电光青蓝对撞", {
    "--bg-page": "#080808", "--bg-sidebar": "#000000", "--bg-surface": "#161616", "--bg-card": "#262626", "--bg-card-hover": "#393939", "--bg-selected": "#164d4b", "--border-subtle": "#393939", "--border-normal": "#6f6f6f", "--border-focus": "#3ddbd9", "--text-primary": "#f2f4f8", "--text-secondary": "#dde1e6", "--text-muted": "#a2a9b0", "--accent-primary": "#3ddbd9", "--accent-secondary": "#78a9ff", "--accent-soft": "#be95ff", "--accent-blue": "#78a9ff", "--accent-cyan": "#3ddbd9", "--accent-mint": "#42be65", "--accent-yellow": "#f1c21b", "--accent-red": "#fa4d56",
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
