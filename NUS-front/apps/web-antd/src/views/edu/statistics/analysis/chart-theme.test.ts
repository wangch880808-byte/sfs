import { afterEach, describe, expect, it } from 'vitest';

import { NUS_PALETTES } from '#/styles/theme/palette';

import {
  BAR_RADIUS_HORIZONTAL,
  BAR_RADIUS_VERTICAL,
  getAnalysisChartTheme,
} from './chart-theme';

const TOKENS = {
  '--nus-bg-elevated': NUS_PALETTES.dark.elevated,
  '--nus-bg-surface': NUS_PALETTES.dark.surface,
  '--nus-border-strong': NUS_PALETTES.dark.borderStrong,
  '--nus-border-subtle': NUS_PALETTES.dark.borderSubtle,
  '--nus-chart-1': NUS_PALETTES.dark.chart[0],
  '--nus-text': NUS_PALETTES.dark.textPrimary,
  '--nus-text-tertiary': NUS_PALETTES.dark.textTertiary,
};

afterEach(() => {
  for (const name of Object.keys(TOKENS)) {
    document.documentElement.style.removeProperty(name);
  }
});

describe('学情分析图表样式', () => {
  it('背景透明，露出面板色', () => {
    expect(getAnalysisChartTheme().base.backgroundColor).toBe('transparent');
  });

  it('取页面上的设计 token：深色下坐标轴用辅助色、分隔线用淡边框、提示框用浮层底', () => {
    for (const [name, value] of Object.entries(TOKENS)) {
      document.documentElement.style.setProperty(name, value);
    }
    const theme = getAnalysisChartTheme();
    const dark = NUS_PALETTES.dark;
    expect(theme.axisLabel.color).toBe(dark.textTertiary);
    expect(theme.legend.textStyle.color).toBe(dark.textTertiary);
    expect(theme.splitLine.lineStyle.color).toBe(dark.borderSubtle);
    expect(theme.axisLine.lineStyle.color).toBe(dark.borderSubtle);
    expect(theme.tooltip.backgroundColor).toBe(dark.elevated);
    expect(theme.tooltip.borderColor).toBe(dark.borderSubtle);
    expect(theme.tooltip.textStyle.color).toBe(dark.textPrimary);
    expect(theme.tooltip.axisPointer.lineStyle.color).toBe(dark.borderStrong);
    expect(theme.series[0]).toBe(dark.chart[0]);
    // 没设的分类色用浅色的值兜底，下标取值一定有值
    expect(theme.series[5]).toBe(NUS_PALETTES.light.chart[5]);
  });

  it('读不到 token 时用浅色的值', () => {
    const theme = getAnalysisChartTheme();
    const light = NUS_PALETTES.light;
    expect(theme.tooltip.backgroundColor).toBe(light.elevated);
    expect(theme.legend.inactiveColor).toBe(light.borderStrong);
    expect(theme.series).toEqual([...light.chart]);
  });

  it('提示框的圆角引用 token，投影用浮层投影色，不写死颜色', () => {
    const { extraCssText, shadowColor } = getAnalysisChartTheme().tooltip;
    expect(extraCssText).toBe('border-radius: var(--nus-radius);');
    expect(shadowColor).toBe(NUS_PALETTES.light.shadow);
    document.documentElement.style.setProperty(
      '--nus-shadow-color',
      NUS_PALETTES.dark.shadow,
    );
    expect(getAnalysisChartTheme().tooltip.shadowColor).toBe(
      NUS_PALETTES.dark.shadow,
    );
    document.documentElement.style.removeProperty('--nus-shadow-color');
  });

  it('柱子只圆柱头一端，圆角取标签档', () => {
    expect(BAR_RADIUS_VERTICAL).toEqual([4, 4, 0, 0]);
    expect(BAR_RADIUS_HORIZONTAL).toEqual([0, 4, 4, 0]);
  });
});
