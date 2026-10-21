<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { InfraRedisApi } from '#/api/infra/redis';

import { onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { usePreferences } from '@vben/preferences';

import { getChartPalette } from '#/views/edu/statistics/chart-kit';

const props = defineProps<{
  redisData?: InfraRedisApi.RedisMonitorInfo;
}>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);
const { isDark } = usePreferences();

/** 读字号 token（如 --nus-fs-display），换成 ECharts 需要的数字 */
function tokenSize(name: string) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(
    name,
  );
  return Number.parseFloat(value) || undefined;
}

/** 解析内存值，移除单位，转为数字 */
function parseMemoryValue(memStr: string | undefined): number {
  if (!memStr) {
    return 0;
  }
  try {
    // 从字符串中提取数字部分，例如 "1.2M" 中的 1.2
    const str = String(memStr); // 显式转换为字符串类型
    const match = str.match(/^([\d.]+)/);
    return match ? Number.parseFloat(match[1] as string) : 0;
  } catch {
    return 0;
  }
}

/** 渲染内存使用图表 */
function renderMemoryChart() {
  if (!props.redisData?.info) {
    return;
  }

  // 处理数据
  const usedMemory = props.redisData.info.used_memory_human || '0';
  const memoryValue = parseMemoryValue(usedMemory);

  // 渲染图表：颜色取设计 token（深浅色切换后重新取色），标题由外层卡片承担
  const palette = getChartPalette();
  renderEcharts({
    tooltip: {
      formatter: `{b}<br/>{a}：${usedMemory}`,
    },
    series: [
      {
        name: '峰值',
        type: 'gauge',
        min: 0,
        max: 100,
        splitNumber: 10,
        radius: '85%',
        center: ['50%', '55%'],
        startAngle: 225,
        endAngle: -45,
        axisLine: {
          lineStyle: {
            color: [[1, palette.border]],
            width: 10,
          },
        },
        progress: {
          show: true,
          width: 10,
          itemStyle: { color: palette.primary },
        },
        axisTick: {
          length: 4,
          lineStyle: { color: palette.border },
        },
        splitLine: {
          length: 10,
          lineStyle: { color: palette.border },
        },
        axisLabel: {
          color: palette.muted,
          distance: 16,
          fontSize: tokenSize('--nus-fs-caption'),
        },
        pointer: {
          width: 4,
          show: true,
          itemStyle: { color: palette.primary },
        },
        anchor: {
          show: true,
          size: 8,
          itemStyle: { color: palette.primary },
        },
        title: {
          color: palette.muted,
          fontSize: tokenSize('--nus-fs-dense'),
          offsetCenter: [0, '72%'],
        },
        detail: {
          show: true,
          offsetCenter: [0, '42%'],
          color: palette.text,
          fontSize: tokenSize('--nus-fs-display'),
          fontWeight: 600,
          formatter: usedMemory,
        },
        data: [
          {
            value: memoryValue,
            name: '内存消耗',
          },
        ],
      },
    ],
  });
}

/** 监听数据变化，重新渲染图表 */
watch(
  () => props.redisData,
  (newVal) => {
    if (newVal) {
      renderMemoryChart();
    }
  },
  { deep: true },
);

/** 深浅色切换后按新的 token 重新取色 */
watch(isDark, () => {
  if (props.redisData) {
    renderMemoryChart();
  }
});

onMounted(() => {
  if (props.redisData) {
    renderMemoryChart();
  }
});
</script>

<template>
  <EchartsUI ref="chartRef" height="420px" />
</template>
