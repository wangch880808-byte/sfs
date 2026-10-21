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

/** 渲染命令统计图表 */
function renderCommandStats() {
  if (!props.redisData?.commandStats) {
    return;
  }

  // 处理数据
  const commandStats = [] as any[];
  const nameList = [] as string[];
  props.redisData.commandStats.forEach((row) => {
    commandStats.push({
      name: row.command,
      value: row.calls,
    });
    nameList.push(row.command);
  });

  // 渲染图表：颜色取设计 token（深浅色切换后重新取色），标题由外层卡片承担
  const palette = getChartPalette();
  renderEcharts({
    color: palette.series,
    tooltip: {
      trigger: 'item',
      formatter: '{a}<br/>{b}：{c}（{d}%）',
    },
    // 图例放在下方横向滚动，窄卡片里也不会压住图形；各命令的名字与次数在图例和悬停提示里看
    legend: {
      type: 'scroll',
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      data: nameList,
      textStyle: {
        color: palette.muted,
      },
      pageTextStyle: {
        color: palette.muted,
      },
    },
    series: [
      {
        name: '命令',
        type: 'pie',
        radius: ['12%', '70%'],
        center: ['50%', '45%'],
        data: commandStats,
        roseType: 'radius',
        itemStyle: {
          borderColor: palette.surface,
          borderWidth: 1,
        },
        label: {
          show: false,
        },
        labelLine: {
          show: false,
        },
        emphasis: {
          label: {
            show: true,
            color: palette.text,
            formatter: '{b}：{c}',
          },
          labelLine: {
            show: true,
            lineStyle: { color: palette.border },
          },
        },
      },
    ],
  });
}

/** 监听数据变化，重新渲染图表 */
watch(
  () => props.redisData,
  (newVal) => {
    if (newVal) {
      renderCommandStats();
    }
  },
  { deep: true },
);

/** 深浅色切换后按新的 token 重新取色 */
watch(isDark, () => {
  if (props.redisData) {
    renderCommandStats();
  }
});

onMounted(() => {
  if (props.redisData) {
    renderCommandStats();
  }
});
</script>

<template>
  <EchartsUI ref="chartRef" height="420px" />
</template>
