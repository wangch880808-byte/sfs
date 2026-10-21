/**
 * 界面颜色的唯一来源（V1.0.1.6 界面重做，参照 ScholarOS 科研外壳的视觉规范）。
 *
 * 浅色、深色两套色板，派生出两组变量，启动时写成一段样式放进页面：
 * - 设计 token（--nus-*，十六进制）：新写的样式与组件直接引用；
 * - vben 的 HSL 通道变量（--background、--card、--primary 等）：外壳、Tailwind 工具类，以及从这些变量取色的
 *   ant-design-vue 全局 token 都跟着变。
 * 浅色挂在 html:root:not(.dark)，深色挂在 html:root.dark，权重高于 vben 自带的 :root 与 .dark[data-theme='default']，
 * 跟随 vben 现有的深浅色切换。主色与成功、警告、危险色由 vben 按偏好写成 <html> 的行内样式，这里用 !important 盖过去，
 * 深色下才能换成提亮的颜色；偏好里的取值（preferences.ts）与浅色一致，vben 据此生成的色阶不会跑偏。
 *
 * 文字色（正文、次要、辅助，以及做文字用的主色与状态色）在画布、面板、悬停、凹陷、浮层、选中底上的对比度都 ≥ 4.5
 * （WCAG AA），单测逐项核对。非颜色 token（字体、字号、间距、圆角、阴影、动效）在同目录的 tokens.css。
 */

export type ThemeMode = 'dark' | 'light';

/** 有语义的颜色：做文字、图标、状态点用的颜色（tone），与上面放白字的实心底（fill）分开 */
export const THEME_TONES = [
  'primary',
  'success',
  'warning',
  'danger',
  'info',
] as const;
export type ThemeTone = (typeof THEME_TONES)[number];

/**
 * 状态按“该不该注意、注意什么”归成五类，每类一色；状态组件只给圆点或图标上色，文字保持正文色：
 * neutral 未开始、草稿、未提交、已关闭；running 进行中、批改中、AI 初评中；waiting 待批改、待重做、即将截止；
 * done 已批改、已通过、已完成；failed 未通过、失败、已逾期。
 * token 为 --nus-status-<类>（圆点、图标与需要强调的文字）与 --nus-status-<类>-tint（需要强调时的浅底）。
 */
export const STATUS_TONES = {
  neutral: 'info',
  running: 'primary',
  waiting: 'warning',
  done: 'success',
  failed: 'danger',
} as const satisfies Record<string, ThemeTone>;
export type StatusTone = keyof typeof STATUS_TONES;

interface Palette {
  /** 页面画布，外壳的顶栏与侧栏也在画布上 */
  canvas: string;
  /** 面板、卡片、表格 */
  surface: string;
  /** 浮层：下拉、弹出框、对话框、抽屉 */
  elevated: string;
  /** 次级区域：表头、分组底、代码块 */
  sunken: string;
  /** 行、项悬停 */
  hover: string;
  /** 选中项（带一点主色） */
  selected: string;
  borderSubtle: string;
  border: string;
  /** 需要更明确边界处（输入框悬停） */
  borderStrong: string;
  textPrimary: string;
  textSecondary: string;
  textTertiary: string;
  /** 禁用控件的文字：WCAG 1.4.3 不要求禁用控件的对比度，不算文字 token */
  disabled: string;
  /** 做文字、图标与状态点的颜色 */
  tones: Record<ThemeTone, string>;
  /** 实心底（主按钮、危险按钮），上面放 onFill 色的字 */
  fills: Record<ThemeTone, string>;
  onFill: string;
  /** 浮层阴影的颜色 */
  shadow: string;
  /** 对话框与抽屉的遮罩 */
  overlay: string;
  /** 局部加载遮罩 */
  mask: string;
  /** 图表的分类色：前四个与主色、状态色同色相，后两个补紫与青；在面板上 ≥ 3（图形对比度） */
  chart: [string, string, string, string, string, string];
}

/**
 * 两套色板。面与线、三档文字沿用 ScholarOS；主色保留原来的蓝（色相 212），浅色 hsl(212 100% 42%)，
 * 深色提亮到 hsl(212 100% 60%)；成功、警告、危险沿用原来的色相，加深（浅色）或提亮（深色）到过 AA。
 * 深色下做文字的颜色提亮后放不了白字，实心底沿用浅色的值。
 */
export const NUS_PALETTES: Readonly<Record<ThemeMode, Palette>> = {
  light: {
    canvas: '#f7f7f8',
    surface: '#ffffff',
    elevated: '#ffffff',
    sunken: '#f2f2f4',
    hover: '#f0f0f2',
    selected: '#ebf3fc',
    borderSubtle: '#ececef',
    border: '#dcdce1',
    borderStrong: '#c6c7cd',
    textPrimary: '#1b1c1f',
    textSecondary: '#5c5f66',
    textTertiary: '#696c73',
    disabled: '#a9abb1',
    tones: {
      primary: '#0064d6',
      success: '#1b7440',
      warning: '#915a08',
      danger: '#c12527',
      info: '#67696e',
    },
    fills: {
      primary: '#0064d6',
      success: '#1b7440',
      warning: '#915a08',
      danger: '#c12527',
      info: '#67696e',
    },
    onFill: '#ffffff',
    shadow: 'rgba(15, 16, 17, 0.08)',
    overlay: 'rgba(15, 16, 17, 0.32)',
    mask: 'rgba(255, 255, 255, 0.72)',
    chart: ['#0064d6', '#1b7440', '#b07010', '#c12527', '#6b4fbb', '#0e7c86'],
  },
  dark: {
    canvas: '#0f1011',
    surface: '#16171a',
    elevated: '#1c1d21',
    sunken: '#121315',
    hover: '#202126',
    selected: '#192635',
    borderSubtle: '#232428',
    border: '#2e2f34',
    borderStrong: '#3c3d43',
    textPrimary: '#e6e6e8',
    textSecondary: '#9a9ca3',
    textTertiary: '#8a8c93',
    disabled: '#55575d',
    tones: {
      primary: '#3392ff',
      success: '#4ebc7b',
      warning: '#dda440',
      danger: '#ef6c6e',
      info: '#9a9ca3',
    },
    fills: {
      primary: '#0064d6',
      success: '#1b7440',
      warning: '#915a08',
      danger: '#c12527',
      info: '#67696e',
    },
    onFill: '#ffffff',
    shadow: 'rgba(0, 0, 0, 0.48)',
    // 深色遮罩加深（1.0.1.4 遗留：深色弹窗遮罩偏淡，背后的内容透出来）
    overlay: 'rgba(0, 0, 0, 0.56)',
    mask: 'rgba(15, 16, 17, 0.72)',
    chart: ['#3392ff', '#4ebc7b', '#dda440', '#ef6c6e', '#a48cf0', '#3fb8c4'],
  },
};

/** 文字 token：在画布、面板、悬停等底上都要 ≥ 4.5（单测逐项核对） */
export const TEXT_TOKENS = [
  '--nus-text',
  '--nus-text-secondary',
  '--nus-text-tertiary',
  ...THEME_TONES.map((tone) => `--nus-${tone}`),
  ...Object.keys(STATUS_TONES).map((status) => `--nus-status-${status}`),
] as const;

/** vben 按偏好写成 <html> 行内样式的颜色变量：主题样式要用 !important 才盖得过 */
export const INLINE_VBEN_COLOR_VARS = [
  '--primary',
  '--success',
  '--warning',
  '--destructive',
] as const;

const rgbOf = (hex: string): [number, number, number] => [
  Number.parseInt(hex.slice(1, 3), 16),
  Number.parseInt(hex.slice(3, 5), 16),
  Number.parseInt(hex.slice(5, 7), 16),
];

const toHex = (channels: number[]): string =>
  `#${channels
    .map((value) =>
      Math.round(Math.min(255, Math.max(0, value)))
        .toString(16)
        .padStart(2, '0'),
    )
    .join('')}`;

/** 两色按比例混合（#rrggbb）：weight 是第一种颜色的占比 */
export const mix = (color: string, base: string, weight: number): string => {
  const [r1, g1, b1] = rgbOf(color);
  const [r2, g2, b2] = rgbOf(base);
  const blend = (x: number, y: number) => x * weight + y * (1 - weight);
  return toHex([blend(r1, r2), blend(g1, g2), blend(b1, b2)]);
};

const channel = (value: number): number => {
  const v = value / 255;
  return v <= 0.039_28 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
};

/** WCAG 2.x 相对亮度（#rrggbb） */
const relativeLuminance = (hex: string): number => {
  const [r, g, b] = rgbOf(hex);
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
};

/** WCAG 2.x 对比度 */
export const contrastRatio = (a: string, b: string): number => {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
};

const round1 = (value: number) => Math.round(value * 10) / 10;

const hslOf = (r: number, g: number, b: number): string => {
  const [rn, gn, bn] = [r / 255, g / 255, b / 255];
  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const lightness = (max + min) / 2;
  const delta = max - min;
  let hue = 0;
  let saturation = 0;
  if (delta !== 0) {
    saturation = delta / (1 - Math.abs(2 * lightness - 1));
    if (max === rn) hue = ((gn - bn) / delta) % 6;
    else if (max === gn) hue = (bn - rn) / delta + 2;
    else hue = (rn - gn) / delta + 4;
    hue = (hue * 60 + 360) % 360;
  }
  return `${round1(hue)} ${round1(saturation * 100)}% ${round1(lightness * 100)}%`;
};

/** #rrggbb 转成 vben 用的 HSL 通道写法（“212 100% 42%”），保留一位小数，换回颜色时误差不到 1/255 */
export const hexToHslChannels = (hex: string): string => hslOf(...rgbOf(hex));

/** rgba(r, g, b, a) 转成带透明度的 HSL 通道写法（“0 0% 0% / 56%”） */
export const rgbaToHslChannels = (rgba: string): string => {
  const match = /^rgba\((\d{1,3}), (\d{1,3}), (\d{1,3}), ([\d.]+)\)$/.exec(
    rgba,
  );
  if (!match) throw new Error(`不支持的颜色写法：${rgba}`);
  const [, r, g, b, alpha] = match;
  return `${hslOf(Number(r), Number(g), Number(b))} / ${round1(Number(alpha) * 100)}%`;
};

const rgbChannels = (hex: string): string => rgbOf(hex).join(', ');

/** 一套模式下的设计 token（--nus-*） */
export const buildNusColorVars = (mode: ThemeMode): Record<string, string> => {
  const p = NUS_PALETTES[mode];
  const vars: Record<string, string> = {
    // ---- 面 ----
    '--nus-bg-canvas': p.canvas,
    '--nus-bg-surface': p.surface,
    '--nus-bg-elevated': p.elevated,
    '--nus-bg-sunken': p.sunken,
    '--nus-bg-hover': p.hover,
    '--nus-bg-selected': p.selected,
    // ---- 线 ----
    '--nus-border-subtle': p.borderSubtle,
    '--nus-border': p.border,
    '--nus-border-strong': p.borderStrong,
    // ---- 文字 ----
    '--nus-text': p.textPrimary,
    '--nus-text-secondary': p.textSecondary,
    '--nus-text-tertiary': p.textTertiary,
    '--nus-text-disabled': p.disabled,
    '--nus-on-fill': p.onFill,
    // ---- 阴影、遮罩、焦点环 ----
    '--nus-shadow-color': p.shadow,
    '--nus-overlay': p.overlay,
    '--nus-mask': p.mask,
    '--nus-focus-ring': `rgba(${rgbChannels(p.tones.primary)}, ${mode === 'dark' ? 0.32 : 0.18})`,
  };
  for (const tone of THEME_TONES) {
    const color = p.tones[tone];
    const fill = p.fills[tone];
    vars[`--nus-${tone}`] = color;
    // 文字悬停：浅色向黑加深，深色向白提亮
    vars[`--nus-${tone}-hover`] =
      mode === 'dark'
        ? mix('#ffffff', color, 0.15)
        : mix('#000000', color, 0.15);
    // 需要强调时的浅底（8%）与它的悬停（12%）
    vars[`--nus-${tone}-tint`] = mix(color, p.surface, 0.08);
    vars[`--nus-${tone}-tint-hover`] = mix(color, p.surface, 0.12);
    // 实心底的悬停与按下都向黑加深（提亮会让白字掉到 4.5 以下）
    vars[`--nus-${tone}-fill`] = fill;
    vars[`--nus-${tone}-fill-hover`] = mix('#000000', fill, 0.08);
    vars[`--nus-${tone}-fill-active`] = mix('#000000', fill, 0.16);
  }
  for (const [status, tone] of Object.entries(STATUS_TONES)) {
    vars[`--nus-status-${status}`] = p.tones[tone];
    vars[`--nus-status-${status}-tint`] = mix(p.tones[tone], p.surface, 0.08);
  }
  p.chart.forEach((color, index) => {
    vars[`--nus-chart-${index + 1}`] = color;
  });
  return vars;
};

/**
 * 一套模式下的 vben 颜色变量（HSL 通道写法）：外壳在画布上，面板与卡片是面板色，悬停与选中跟设计 token 一致。
 * 默认边框（border-border）用淡边框，输入框用边框；焦点环用主色（vben 原值是近黑色，深色下看不见）。
 */
export const buildVbenColorVars = (mode: ThemeMode): Record<string, string> => {
  const p = NUS_PALETTES[mode];
  const hsl = hexToHslChannels;
  const strongHover = mix(p.border, p.hover, 0.5);
  return {
    '--background': hsl(p.surface),
    '--background-deep': hsl(p.canvas),
    '--foreground': hsl(p.textPrimary),
    '--card': hsl(p.surface),
    '--card-foreground': hsl(p.textPrimary),
    '--popover': hsl(p.elevated),
    '--popover-foreground': hsl(p.textPrimary),
    '--muted': hsl(p.sunken),
    '--muted-foreground': hsl(p.textSecondary),
    '--primary': hsl(p.tones.primary),
    '--primary-foreground': hsl(p.onFill),
    '--destructive': hsl(p.tones.danger),
    '--destructive-foreground': hsl(p.onFill),
    '--success': hsl(p.tones.success),
    '--success-foreground': hsl(p.onFill),
    '--warning': hsl(p.tones.warning),
    '--warning-foreground': hsl(p.onFill),
    '--secondary': hsl(p.hover),
    '--secondary-foreground': hsl(p.textPrimary),
    '--accent': hsl(p.hover),
    '--accent-hover': hsl(strongHover),
    '--accent-dark': hsl(strongHover),
    '--accent-darker': hsl(p.border),
    '--accent-lighter': hsl(p.sunken),
    '--accent-foreground': hsl(p.textPrimary),
    '--heavy': hsl(strongHover),
    '--border': hsl(p.borderSubtle),
    '--input': hsl(p.border),
    '--input-placeholder': hsl(p.textTertiary),
    '--input-background': hsl(p.surface),
    '--ring': hsl(p.tones.primary),
    '--overlay': rgbaToHslChannels(p.overlay),
    '--sidebar': hsl(p.canvas),
    '--sidebar-deep': hsl(p.canvas),
    '--header': hsl(p.canvas),
  };
};

export const NUS_THEME_STYLE_ID = 'nus-theme-colors';

/** 浅色与深色两段样式；vben 行内写的四个颜色变量加 !important */
export const nusThemeCss = (): string => {
  const inline = new Set<string>(INLINE_VBEN_COLOR_VARS);
  const block = (selector: string, mode: ThemeMode) => {
    const declarations = [
      ...Object.entries(buildNusColorVars(mode)),
      ...Object.entries(buildVbenColorVars(mode)).map(
        ([name, value]) =>
          [name, inline.has(name) ? `${value} !important` : value] as const,
      ),
    ];
    return `${selector}{${declarations.map(([name, value]) => `${name}:${value}`).join(';')}}`;
  };
  return [
    block('html:root:not(.dark)', 'light'),
    block('html:root.dark', 'dark'),
  ].join('\n');
};

/** 把颜色样式写进页面（只写一次）；深浅色由 vben 在 <html> 上切换的 dark 类决定 */
export const applyNusTheme = (doc: Document = document): void => {
  if (doc.querySelector(`#${NUS_THEME_STYLE_ID}`)) return;
  const style = doc.createElement('style');
  style.id = NUS_THEME_STYLE_ID;
  style.textContent = nusThemeCss();
  doc.head.append(style);
};

/**
 * 偏好里的主色与状态色（取浅色值）：vben 据此生成 --primary-50…900 等色阶，并写进 <html> 的行内样式；
 * 主题样式会用上面的色板盖过行内的四个变量，这里保持一致是为了色阶不跑偏。
 */
export const PREFERENCE_THEME_COLORS = {
  colorDestructive: NUS_PALETTES.light.tones.danger,
  colorPrimary: NUS_PALETTES.light.tones.primary,
  colorSuccess: NUS_PALETTES.light.tones.success,
  colorWarning: NUS_PALETTES.light.tones.warning,
} as const;
