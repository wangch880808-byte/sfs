import type { ThemeMode } from './palette';

import { buildNusColorVars, mix, NUS_PALETTES } from './palette';

/** 系统字体栈（与 tokens.css 的 --nus-font-sans 一致）：不加网络字体 */
export const NUS_FONT_FAMILY = [
  '-apple-system',
  'BlinkMacSystemFont',
  "'Segoe UI'",
  "'PingFang SC'",
  "'Hiragino Sans GB'",
  "'Microsoft YaHei'",
  "'Helvetica Neue'",
  'Arial',
  'sans-serif',
  "'Apple Color Emoji'",
  "'Segoe UI Emoji'",
  "'Segoe UI Symbol'",
].join(', ');

interface AntdThemeOptions {
  /** 系统开启“减少动态效果”时，过渡时长全部为 0 */
  reducedMotion?: boolean;
}

/**
 * ant-design-vue 的全局与组件 token（V1.0.1.6 界面规范）：颜色取 palette.ts 的色板，尺寸取 tokens.css 的规范值。
 *
 * - 种子 token（主色、状态色、字体、字号、圆角、控件高度）经 ant-design-vue 的深浅色算法派生其余颜色；
 *   文字、面、线、填充、选中、焦点环这些派生值再按色板逐个覆盖，保证与 vben 外壳、设计 token 是同一套颜色。
 * - 深色下做文字的主色提亮后放不了白字：日期选择的选中格、步骤条、徽标数在组件层改用实心底；
 *   主按钮与危险主按钮的实心底由 @vben/styles/antd 的样式补上（按钮的悬停文字也用主色，组件 token 分不开）。
 */
export function buildAntdTheme(
  mode: ThemeMode,
  options: AntdThemeOptions = {},
) {
  const p = NUS_PALETTES[mode];
  const colorVars = buildNusColorVars(mode);
  /** 取设计 token 的值；名字写错直接报错 */
  const token$ = (name: string): string => {
    const value = colorVars[name];
    if (value === undefined) throw new Error(`缺少设计 token ${name}`);
    return value;
  };
  const duration = (ms: number) =>
    options.reducedMotion ? '0s' : `${ms / 1000}s`;
  const popoverShadow = `0 0 0 1px ${p.borderSubtle}, 0 8px 24px ${p.shadow}`;
  const dialogShadow = `0 0 0 1px ${p.borderSubtle}, 0 16px 48px ${p.shadow}`;
  /** 状态色按比例叠在面板色上：提示条、状态浅底的底色与边框 */
  const tint = (tone: keyof typeof p.tones, weight: number) =>
    mix(p.tones[tone], p.surface, weight);
  const statusSurfaces = Object.fromEntries(
    (
      [
        ['Success', 'success'],
        ['Warning', 'warning'],
        ['Error', 'danger'],
        ['Info', 'primary'],
      ] as const
    ).flatMap(([name, tone]) => [
      [`color${name}Bg`, tint(tone, 0.08)],
      [`color${name}BgHover`, tint(tone, 0.12)],
      [`color${name}Border`, tint(tone, 0.28)],
      [`color${name}BorderHover`, tint(tone, 0.4)],
    ]),
  ) as Record<string, string>;
  const fill = {
    colorPrimary: p.fills.primary,
    colorPrimaryActive: token$('--nus-primary-fill-active'),
    colorPrimaryHover: token$('--nus-primary-fill-hover'),
  };

  const token = {
    // ---- 种子 ----
    borderRadius: 6,
    colorBgBase: p.surface,
    colorError: p.tones.danger,
    colorInfo: p.tones.primary,
    colorPrimary: p.tones.primary,
    colorSuccess: p.tones.success,
    colorTextBase: p.textPrimary,
    colorWarning: p.tones.warning,
    controlHeight: 32,
    fontFamily: NUS_FONT_FAMILY,
    fontSize: 14,
    wireframe: false,
    // 弹层基础层级调高，避免下拉被弹窗或最大化的表格遮住
    zIndexPopupBase: 2000,

    // ---- 尺寸：圆角四档（标签 4、按钮与输入框 6、卡片与表格 8），控件 32 / 28 / 40 ----
    borderRadiusLG: 8,
    borderRadiusOuter: 4,
    borderRadiusSM: 4,
    borderRadiusXS: 2,
    controlHeightLG: 40,
    controlHeightSM: 28,
    fontSizeHeading4: 20,
    fontSizeHeading5: 15,
    fontWeightStrong: 600,

    // ---- 文字 ----
    colorIcon: p.textTertiary,
    colorIconHover: p.textPrimary,
    colorText: p.textPrimary,
    colorTextDescription: p.textTertiary,
    colorTextDisabled: p.disabled,
    colorTextHeading: p.textPrimary,
    colorTextLabel: p.textSecondary,
    colorTextPlaceholder: p.textTertiary,
    colorTextQuaternary: p.disabled,
    colorTextSecondary: p.textSecondary,
    colorTextTertiary: p.textTertiary,

    // ---- 主色做文字、链接、选中 ----
    colorLink: p.tones.primary,
    colorLinkActive: p.tones.primary,
    colorLinkHover: token$('--nus-primary-hover'),
    colorPrimaryActive: p.tones.primary,
    colorPrimaryBg: p.selected,
    colorPrimaryBgHover: token$('--nus-primary-tint-hover'),
    colorPrimaryHover: token$('--nus-primary-hover'),
    colorPrimaryText: p.tones.primary,
    colorPrimaryTextActive: p.tones.primary,
    colorPrimaryTextHover: token$('--nus-primary-hover'),
    colorErrorText: p.tones.danger,
    colorSuccessText: p.tones.success,
    colorWarningText: p.tones.warning,

    // ---- 提示条与状态浅底：取状态色的浅底，不用算法从加深后的种子色派生（浅色下会发灰发浑） ----
    ...statusSurfaces,

    // ---- 面、线、填充 ----
    colorBgContainer: p.surface,
    colorBgContainerDisabled: p.sunken,
    colorBgElevated: p.elevated,
    colorBgLayout: p.canvas,
    colorBgMask: p.overlay,
    colorBgSpotlight: mode === 'dark' ? p.borderStrong : p.textPrimary,
    colorBgTextActive: p.border,
    colorBgTextHover: p.hover,
    colorBorder: p.border,
    colorBorderSecondary: p.borderSubtle,
    colorFill: p.border,
    colorFillAlter: p.sunken,
    colorFillContent: p.hover,
    colorFillContentHover: p.border,
    colorFillQuaternary: p.sunken,
    colorFillSecondary: p.hover,
    colorFillTertiary: p.hover,
    colorSplit: p.borderSubtle,
    controlItemBgActive: p.selected,
    controlItemBgActiveHover: token$('--nus-primary-tint-hover'),
    controlItemBgHover: p.hover,
    controlOutline: token$('--nus-focus-ring'),
    controlOutlineWidth: 2,

    // ---- 阴影只给浮层 ----
    boxShadow: popoverShadow,
    boxShadowSecondary: popoverShadow,
    boxShadowTertiary: `0 0 0 1px ${p.borderSubtle}`,

    // ---- 动效：120 / 180ms ----
    motionDurationFast: duration(120),
    motionDurationMid: duration(180),
    motionDurationSlow: duration(180),
  };

  const components = {
    Badge: { colorError: p.fills.danger },
    Card: { fontSizeLG: 15 },
    DatePicker: fill,
    Drawer: { fontSizeLG: 15 },
    Modal: {
      borderRadiusLG: 12,
      boxShadow: dialogShadow,
      fontSizeHeading5: 15,
    },
    Steps: fill,
    Tooltip: { colorTextLightSolid: '#ffffff' },
  };

  return { components, token };
}
