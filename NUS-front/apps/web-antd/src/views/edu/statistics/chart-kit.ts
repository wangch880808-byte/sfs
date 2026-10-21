import { NUS_PALETTES } from '#/styles/theme/palette';

export interface ChartPalette {
  text: string;
  muted: string;
  border: string;
  primary: string;
  success: string;
  warning: string;
  danger: string;
  info: string;
  accent: string;
  surface: string;
  series: string[];
}

/** 读设计 token（--nus-*，十六进制；见 styles/theme/palette.ts），深浅色切换后重新取色即可 */
function resolveToken(name: string, fallback: string) {
  if (typeof window === 'undefined') {
    return fallback;
  }
  return (
    getComputedStyle(document.documentElement).getPropertyValue(name).trim() ||
    fallback
  );
}

export function getChartPalette(): ChartPalette {
  // 页面还没写入主题样式时（如单测环境）退回浅色色板
  const fallback = NUS_PALETTES.light;
  const series = fallback.chart.map((color, index) =>
    resolveToken(`--nus-chart-${index + 1}`, color),
  );
  return {
    text: resolveToken('--nus-text', fallback.textPrimary),
    muted: resolveToken('--nus-text-tertiary', fallback.textTertiary),
    border: resolveToken('--nus-border-subtle', fallback.borderSubtle),
    primary: resolveToken('--nus-primary', fallback.tones.primary),
    success: resolveToken('--nus-success', fallback.tones.success),
    warning: resolveToken('--nus-warning', fallback.tones.warning),
    danger: resolveToken('--nus-danger', fallback.tones.danger),
    info: resolveToken('--nus-info', fallback.tones.info),
    accent: resolveToken('--nus-bg-sunken', fallback.sunken),
    surface: resolveToken('--nus-bg-surface', fallback.surface),
    series,
  };
}

export function average(values: Array<null | number | undefined>) {
  const numbers = values.filter(
    (item): item is number => typeof item === 'number' && Number.isFinite(item),
  );
  if (numbers.length === 0) {
    return 0;
  }
  return numbers.reduce((sum, current) => sum + current, 0) / numbers.length;
}

export function clampPercent(value: number) {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.max(0, Math.min(100, value));
}

export function roundTo(value: number, digits = 1) {
  if (!Number.isFinite(value)) {
    return 0;
  }
  const base = 10 ** digits;
  return Math.round(value * base) / base;
}

export function formatPercent(value: number, digits = 1) {
  return `${roundTo(clampPercent(value), digits)}%`;
}

/** 能力图的一项：指标名与达标率（百分数） */
export interface CapacityChartItem {
  name: string;
  value: number;
}

/** 能力图的样式：颜色取自设计 token，页面已有的公共项（透明背景、提示框）可以传进来 */
export interface CapacityChartStyle {
  palette: ChartPalette;
  /** 系列色，默认主色 */
  color?: string;
  /** 每张图的公共项，默认透明背景 */
  base?: Record<string, unknown>;
  tooltip?: Record<string, unknown>;
  /** 雷达图的半径与圆心 */
  radar?: { center?: [string, string]; radius?: string };
  /** 系列名，默认「达标率」 */
  seriesName?: string;
  /** 数值保留的小数位，默认 1 */
  digits?: number;
}

export type CapacityChartKind = 'bar' | 'radar';

/** ECharts 的雷达图至少要 3 个维度才围得成多边形 */
export const CAPACITY_RADAR_MIN_DIMENSIONS = 3;

/** 指标少于 3 个时画横向条形图，3 个及以上画雷达图 */
export function getCapacityChartKind(count: number): CapacityChartKind {
  return count < CAPACITY_RADAR_MIN_DIMENSIONS ? 'bar' : 'radar';
}

/**
 * 能力分布图（学生能力分析、实训报告、单项能力评测共用）：
 * 只有 1～2 个指标时雷达图会画成一条线，改画横向条形图（数值轴上限 100，标签显示百分比）；3 个及以上画雷达图。
 * 两种图切换时调用方要先 chart.clear() 再 setOption（见 applyCapacityChartOption），免得残留上一种图的坐标系。
 */
export function buildCapacityChartOption(
  items: CapacityChartItem[],
  style: CapacityChartStyle,
) {
  const { palette } = style;
  const color = style.color ?? palette.primary;
  const seriesName = style.seriesName ?? '达标率';
  const base = style.base ?? { backgroundColor: 'transparent' };
  const tooltip = style.tooltip ?? {
    backgroundColor: palette.surface,
    borderColor: palette.border,
    textStyle: { color: palette.text },
  };
  const values = items.map((item) =>
    roundTo(clampPercent(item.value), style.digits ?? 1),
  );

  if (getCapacityChartKind(items.length) === 'bar') {
    return {
      ...base,
      color: [color],
      tooltip: {
        ...tooltip,
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        valueFormatter: (value: number) => `${value}%`,
      },
      grid: { bottom: 0, containLabel: true, left: 0, right: 48, top: 8 },
      xAxis: {
        type: 'value',
        min: 0,
        max: 100,
        axisLabel: { color: palette.muted, formatter: '{value}%' },
        axisLine: { show: false },
        splitLine: { lineStyle: { color: palette.border } },
      },
      yAxis: {
        type: 'category',
        // 第一个指标排在最上面
        inverse: true,
        data: items.map((item) => item.name),
        axisLabel: { color: palette.muted },
        axisLine: { lineStyle: { color: palette.border } },
        axisTick: { show: false },
      },
      series: [
        {
          name: seriesName,
          type: 'bar',
          barMaxWidth: 24,
          // 只圆柱头一端，取标签档圆角（与 analysis/chart-theme 的 BAR_RADIUS_HORIZONTAL 一致）
          itemStyle: { borderRadius: [0, 4, 4, 0] },
          label: {
            show: true,
            position: 'right',
            color: palette.text,
            formatter: ({ value }: { value: number }) => `${value}%`,
          },
          data: values,
        },
      ],
    };
  }

  return {
    ...base,
    color: [color],
    tooltip,
    radar: {
      center: style.radar?.center ?? ['50%', '54%'],
      radius: style.radar?.radius ?? '66%',
      shape: 'polygon',
      indicator: items.map((item) => ({ name: item.name, max: 100 })),
      axisName: { color: palette.muted },
      axisLine: { lineStyle: { color: palette.border } },
      splitLine: { lineStyle: { color: palette.border } },
      splitArea: { show: false },
    },
    series: [
      {
        type: 'radar',
        symbolSize: 6,
        data: [
          {
            name: seriesName,
            value: values,
            areaStyle: { color, opacity: 0.16 },
            lineStyle: { color, width: 2 },
            itemStyle: { color },
          },
        ],
      },
    ],
  };
}

/** 画能力分布图：条形图与雷达图来回切换时先清空，再按新配置画 */
export function applyCapacityChartOption(
  chart: { clear: () => void; setOption: (option: any) => void },
  items: CapacityChartItem[],
  style: CapacityChartStyle,
) {
  chart.clear();
  chart.setOption(buildCapacityChartOption(items, style));
}
