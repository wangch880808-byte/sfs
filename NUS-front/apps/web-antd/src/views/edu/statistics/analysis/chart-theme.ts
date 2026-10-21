import type { ChartPalette } from '../chart-kit';

import { NUS_PALETTES } from '#/styles/theme/palette';

import { getChartPalette } from '../chart-kit';

/**
 * 学情分析页（教学过程分析、教学问题研判、单项能力评测）共用的图表样式（V1.0.1.6 界面规范）。
 * 颜色全部取设计 token：背景透明，露出面板色；坐标轴文字用辅助色，分隔线用淡边框；提示框用浮层底、淡边框和浮层阴影，
 * 深色下也看得清。字号沿用 ECharts 默认的 12（与辅助说明一致），不在这里写。
 * token 在深浅色切换后才有新值，所以每次画图时重新取，切换主题后重建图表即可。
 */

/** 读一个设计 token（--nus-*，十六进制）；读不到时用浅色的值 */
function readToken(name: string, fallback: string) {
  if (typeof window === 'undefined') {
    return fallback;
  }
  return (
    getComputedStyle(document.documentElement).getPropertyValue(name).trim() ||
    fallback
  );
}

function readFontFamily() {
  if (typeof window === 'undefined') {
    return 'sans-serif';
  }
  return getComputedStyle(document.body).fontFamily || 'sans-serif';
}

/** 图表的 6 个分类色（--nus-chart-1…6）：前四个与主色、成功、警告、危险同色相 */
export type ChartSeriesColors = readonly [
  string,
  string,
  string,
  string,
  string,
  string,
];

export interface AnalysisChartTheme {
  palette: ChartPalette;
  /** 按下标取分类色，一定有值（读不到 token 时用浅色的值） */
  series: ChartSeriesColors;
  /** 每张图的公共项：透明背景与字体 */
  base: {
    backgroundColor: string;
    textStyle: { color: string; fontFamily: string };
  };
  axisLabel: { color: string };
  axisLine: { lineStyle: { color: string } };
  axisTick: { show: boolean };
  splitLine: { lineStyle: { color: string } };
  legend: {
    icon: string;
    inactiveColor: string;
    itemGap: number;
    itemHeight: number;
    itemWidth: number;
    textStyle: { color: string };
  };
  tooltip: {
    axisPointer: { lineStyle: { color: string } };
    backgroundColor: string;
    borderColor: string;
    borderWidth: number;
    extraCssText: string;
    padding: [number, number];
    shadowBlur: number;
    shadowColor: string;
    shadowOffsetX: number;
    shadowOffsetY: number;
    textStyle: { color: string };
  };
}

export function getAnalysisChartTheme(): AnalysisChartTheme {
  const palette = getChartPalette();
  const elevated = readToken('--nus-bg-elevated', NUS_PALETTES.light.elevated);
  const borderStrong = readToken(
    '--nus-border-strong',
    NUS_PALETTES.light.borderStrong,
  );
  const shadowColor = readToken(
    '--nus-shadow-color',
    NUS_PALETTES.light.shadow,
  );
  const [c1, c2, c3, c4, c5, c6] = NUS_PALETTES.light.chart;
  const pick = (index: number, fallback: string) =>
    palette.series[index] || fallback;
  return {
    palette,
    series: [
      pick(0, c1),
      pick(1, c2),
      pick(2, c3),
      pick(3, c4),
      pick(4, c5),
      pick(5, c6),
    ],
    base: {
      backgroundColor: 'transparent',
      textStyle: { color: palette.muted, fontFamily: readFontFamily() },
    },
    axisLabel: { color: palette.muted },
    axisLine: { lineStyle: { color: palette.border } },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: palette.border } },
    legend: {
      icon: 'roundRect',
      inactiveColor: borderStrong,
      itemGap: 16,
      itemHeight: 8,
      itemWidth: 12,
      textStyle: { color: palette.muted },
    },
    tooltip: {
      axisPointer: { lineStyle: { color: borderStrong } },
      backgroundColor: elevated,
      borderColor: palette.border,
      borderWidth: 1,
      // 圆角与投影跟浮层一致（--nus-radius、--nus-shadow-popover 的取值），替换 ECharts 自带的黑色投影
      extraCssText: 'border-radius: var(--nus-radius);',
      padding: [8, 12],
      shadowBlur: 24,
      shadowColor,
      shadowOffsetX: 0,
      shadowOffsetY: 8,
      textStyle: { color: palette.text },
    },
  };
}

/** 柱子的圆角：只圆柱头一端，取标签档（4） */
export const BAR_RADIUS_VERTICAL = [4, 4, 0, 0];
export const BAR_RADIUS_HORIZONTAL = [0, 4, 4, 0];
