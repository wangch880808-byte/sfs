import { describe, expect, it, vi } from 'vitest';

import { NUS_PALETTES } from '#/styles/theme/palette';

import {
  applyCapacityChartOption,
  buildCapacityChartOption,
  getCapacityChartKind,
  getChartPalette,
} from './chart-kit';

const ITEMS = [
  { name: '需求分析', value: 66.66 },
  { name: '编码实现', value: 80 },
  { name: '测试验证', value: 120 },
];

describe('能力分布图', () => {
  it('少于 3 个指标画横向条形图，3 个及以上画雷达图', () => {
    expect(getCapacityChartKind(1)).toBe('bar');
    expect(getCapacityChartKind(2)).toBe('bar');
    expect(getCapacityChartKind(3)).toBe('radar');
  });

  it('1 个指标：数值轴在 x、上限 100，类目轴在 y，柱宽不超过 24，标签显示百分比', () => {
    const option: any = buildCapacityChartOption(ITEMS.slice(0, 1), {
      palette: getChartPalette(),
    });
    expect(option.radar).toBeUndefined();
    expect(option.xAxis).toMatchObject({ max: 100, min: 0, type: 'value' });
    expect(option.yAxis).toMatchObject({
      data: ['需求分析'],
      inverse: true,
      type: 'category',
    });
    const [series] = option.series;
    expect(series).toMatchObject({ barMaxWidth: 24, type: 'bar' });
    expect(series.data).toEqual([66.7]);
    expect(series.label.formatter({ value: 66.7 })).toBe('66.7%');
  });

  it('2 个指标仍是条形图，按小数位取整、超出 100 的截到 100', () => {
    const option: any = buildCapacityChartOption(
      ITEMS.filter((item) => item.name !== '编码实现'),
      {
        digits: 0,
        palette: getChartPalette(),
      },
    );
    expect(option.series[0].type).toBe('bar');
    expect(option.yAxis.data).toEqual(['需求分析', '测试验证']);
    expect(option.series[0].data).toEqual([67, 100]);
  });

  it('3 个指标画雷达图，每个维度上限 100', () => {
    const option: any = buildCapacityChartOption(ITEMS, {
      palette: getChartPalette(),
      radar: { center: ['50%', '54%'], radius: '70%' },
    });
    expect(option.xAxis).toBeUndefined();
    expect(option.radar).toMatchObject({ radius: '70%', shape: 'polygon' });
    expect(option.radar.indicator).toEqual([
      { max: 100, name: '需求分析' },
      { max: 100, name: '编码实现' },
      { max: 100, name: '测试验证' },
    ]);
    expect(option.series[0].type).toBe('radar');
    expect(option.series[0].data[0].value).toEqual([66.7, 80, 100]);
  });

  it('颜色取设计 token：默认主色，也可以传入图表分类色', () => {
    const palette = getChartPalette();
    const bar: any = buildCapacityChartOption(ITEMS.slice(0, 2), { palette });
    expect(bar.color).toEqual([NUS_PALETTES.light.tones.primary]);
    const radar: any = buildCapacityChartOption(ITEMS, {
      color: palette.series[0],
      palette,
    });
    expect(radar.color).toEqual([NUS_PALETTES.light.chart[0]]);
  });

  it('画图时先清空再设置，条形图与雷达图来回切换不残留坐标系', () => {
    const calls: string[] = [];
    const chart = {
      clear: vi.fn(() => calls.push('clear')),
      setOption: vi.fn((_option: unknown) => calls.push('setOption')),
    };
    applyCapacityChartOption(chart, ITEMS.slice(0, 1), {
      palette: getChartPalette(),
    });
    applyCapacityChartOption(chart, ITEMS, { palette: getChartPalette() });
    expect(calls).toEqual(['clear', 'setOption', 'clear', 'setOption']);
    expect(chart.setOption.mock.calls[1]?.[0]).toHaveProperty('radar');
  });
});
