<script lang="ts" setup>
import type { StudentCapacityApi } from '#/api/edu/student/capacity';

import {
  computed,
  nextTick,
  onMounted,
  onUnmounted,
  ref,
  shallowRef,
  watch,
} from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { usePreferences } from '@vben/preferences';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, Card, Progress, Spin } from 'ant-design-vue';
import { BarChart, LineChart, RadarChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  RadarComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

import {
  exportTrainingReportPdf,
  getReportBasic,
  getReportSkills,
  getReportTrend,
} from '#/api/edu/student/capacity';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import {
  getCertificateEligibilityMeta,
  getListedHighlight,
  getPendingEvaluationMeta,
  getSkillHighlightMeta,
} from '#/utils/status-capacity';
import {
  applyCapacityChartOption,
  getChartPalette,
} from '#/views/edu/statistics/chart-kit';

import { toCapacityChartItems } from './capacity-summary';

echarts.use([
  BarChart,
  LineChart,
  RadarChart,
  GridComponent,
  LegendComponent,
  RadarComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer,
]);

const router = useRouter();
const { isDark } = usePreferences();
const loading = ref(false);
const exporting = ref(false);

async function handleExportPdf() {
  exporting.value = true;
  try {
    const source = await exportTrainingReportPdf();
    downloadFileFromBlobPart({ fileName: '个人训练报告.pdf', source });
  } finally {
    exporting.value = false;
  }
}
const basic = ref<StudentCapacityApi.ReportBasic>();
const skills = ref<StudentCapacityApi.ReportSkills>();
const trend = ref<StudentCapacityApi.ReportTrend>();

const radarChartRef = ref<HTMLElement>();
const trendChartRef = ref<HTMLElement>();
const radarInstance = shallowRef<echarts.ECharts>();
const trendInstance = shallowRef<echarts.ECharts>();

const pendingMeta = getPendingEvaluationMeta();
const certificateMeta = computed(() =>
  getCertificateEligibilityMeta(basic.value?.certificateEligible),
);

/** 基础数据：数字统一用正文色 */
const basicMetrics = computed(() => [
  { label: '总任务数', value: basic.value?.totalTasks ?? 0 },
  { label: '已提交', value: basic.value?.submittedTasks ?? 0 },
  { label: '待批改', value: basic.value?.pendingReviewTasks ?? 0 },
  { label: '已评定', value: basic.value?.evaluatedTasks ?? 0 },
  { label: '已达标', value: basic.value?.qualifiedTasks ?? 0 },
]);

const qualifiedRateText = computed(() => {
  const rate = basic.value?.qualifiedRate;
  return rate === null || rate === undefined ? '待评定' : `${rate}%`;
});

/** 能力分布图：只画已有达标率的指标（能力画像只统计关联了能力指标的任务） */
const chartItems = computed(() =>
  toCapacityChartItems(skills.value?.skills ?? []),
);
const hasRadarData = computed(() => chartItems.value.length > 0);
/** 任务计数统计全部任务：有已评定的任务、却没有能画的指标，说明这些任务都没关联能力指标 */
const onlyUnindicated = computed(
  () => !hasRadarData.value && (basic.value?.evaluatedTasks ?? 0) > 0,
);

/** 技能分析一行：按后端给出的优势、薄弱名单标注 */
const skillRows = computed(() =>
  (skills.value?.skills ?? []).map((item) => ({
    ...item,
    highlight: getSkillHighlightMeta(
      getListedHighlight(
        item.indicatorName,
        skills.value?.strengths,
        skills.value?.weaknesses,
      ),
    ),
  })),
);

/** 图表的公共配色：颜色取设计 token，深浅色切换后重画 */
function chartTheme() {
  const palette = getChartPalette();
  return {
    palette,
    tooltip: {
      backgroundColor: palette.surface,
      borderColor: palette.border,
      textStyle: { color: palette.text },
    },
  };
}

/** 能力分布图：3 个及以上指标画雷达图，1～2 个画横向条形图 */
function renderRadarChart() {
  if (!radarChartRef.value || chartItems.value.length === 0) return;
  radarInstance.value?.dispose();
  const chart = echarts.init(radarChartRef.value);
  radarInstance.value = chart;
  const { palette, tooltip } = chartTheme();
  applyCapacityChartOption(chart, chartItems.value, {
    digits: 0,
    palette,
    tooltip,
  });
}

function renderTrendChart() {
  if (!trendChartRef.value || !trend.value?.trends?.length) return;
  trendInstance.value?.dispose();
  const chart = echarts.init(trendChartRef.value);
  trendInstance.value = chart;
  const records = trend.value.trends;
  const { palette, tooltip } = chartTheme();
  chart.setOption({
    backgroundColor: 'transparent',
    color: palette.series,
    tooltip: { ...tooltip, trigger: 'axis' },
    grid: { left: 8, right: 24, top: 36, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: records.map((r) => r.recordDate),
      axisLabel: { color: palette.muted },
      axisLine: { lineStyle: { color: palette.border } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      name: '总分',
      nameTextStyle: { color: palette.muted },
      axisLabel: { color: palette.muted },
      splitLine: { lineStyle: { color: palette.border } },
    },
    series: [
      {
        name: '总分',
        type: 'line',
        symbolSize: 6,
        lineStyle: { width: 2 },
        data: records.map((r) => r.totalScore),
      },
    ],
  });
}

async function loadData() {
  loading.value = true;
  try {
    const [b, s, t] = await Promise.all([
      getReportBasic(),
      getReportSkills(),
      getReportTrend(),
    ]);
    basic.value = b;
    skills.value = s;
    trend.value = t;
    await nextTick();
    renderRadarChart();
    renderTrendChart();
  } finally {
    loading.value = false;
  }
}

function handleResize() {
  radarInstance.value?.resize();
  trendInstance.value?.resize();
}

watch(isDark, async () => {
  await nextTick();
  renderRadarChart();
  renderTrendChart();
});

onMounted(() => {
  loadData();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  radarInstance.value?.dispose();
  trendInstance.value?.dispose();
});
</script>

<template>
  <Page
    auto-content-height
    description="汇总你的任务完成、能力达标与成绩变化，可以导出 PDF 留存。"
    title="实训报告"
  >
    <template #extra>
      <Button @click="router.back()">返回</Button>
      <Button :loading="exporting" type="primary" @click="handleExportPdf">
        导出 PDF
      </Button>
    </template>

    <Spin :spinning="loading" tip="正在读取实训报告…">
      <div class="page-stack">
        <!-- 基础数据 -->
        <div class="metric-grid lg:grid-cols-3">
          <div
            v-for="item in basicMetrics"
            :key="item.label"
            class="metric-card"
          >
            <div class="metric-card__label">{{ item.label }}</div>
            <div class="metric-card__value">{{ item.value }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-card__label">达标率</div>
            <div class="metric-card__value">{{ qualifiedRateText }}</div>
            <div v-if="certificateMeta" class="metric-card__meta">
              <StatusTag
                :label="certificateMeta.label"
                :tone="certificateMeta.tone"
              />
            </div>
          </div>
        </div>

        <!-- 能力雷达图 + 能力分析 -->
        <div class="grid gap-4 lg:grid-cols-2">
          <Card title="能力雷达图">
            <div
              v-if="hasRadarData"
              ref="radarChartRef"
              class="report-chart"
            ></div>
            <EmptyState
              v-else-if="onlyUnindicated"
              compact
              description="这些任务计入任务统计，不计入能力画像。关联了能力指标的任务批改后，这里会显示达标率分布。"
              title="已评定的任务都未关联能力指标"
            />
            <EmptyState
              v-else
              compact
              description="教师批改你的任务后，这里会按指标显示达标率分布。"
              title="还没有已评定的指标"
            />
          </Card>
          <Card title="能力分析">
            <ul v-if="skillRows.length > 0" class="report-rows">
              <li
                v-for="item in skillRows"
                :key="item.indicatorName"
                class="report-row"
              >
                <span :title="item.indicatorName" class="report-row__name">
                  {{ item.indicatorName }}
                </span>
                <div class="report-row__value">
                  <Progress
                    v-if="item.qualifiedRate !== null"
                    :percent="Math.round(item.qualifiedRate)"
                    size="small"
                    status="normal"
                  />
                  <StatusTag
                    v-else
                    :label="pendingMeta.label"
                    :tone="pendingMeta.tone"
                  />
                </div>
                <span class="report-row__tag">
                  <StatusTag
                    v-if="item.highlight"
                    :label="item.highlight.label"
                    :tone="item.highlight.tone"
                  />
                </span>
              </li>
            </ul>
            <EmptyState
              v-else
              compact
              description="课程设置能力指标并布置任务后，这里会列出每项指标的达标率。"
              title="还没有能力指标"
            />
          </Card>
        </div>

        <!-- 排名趋势折线图 -->
        <Card title="排名趋势">
          <div
            v-if="trend?.trends?.length"
            ref="trendChartRef"
            class="report-chart"
          ></div>
          <EmptyState
            v-else
            compact
            description="任务批改后会记录总分，这里按时间显示变化。"
            title="还没有趋势数据"
          />
        </Card>
      </div>
    </Spin>
  </Page>
</template>

<style scoped>
.report-chart {
  width: 100%;
  height: 360px;
}

.report-rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.report-row {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr) 56px;
  gap: 16px;
  align-items: center;
  min-height: 32px;
}

.report-row__name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  white-space: nowrap;
}

.report-row__value {
  min-width: 0;
}

.report-row__value :deep(.ant-progress) {
  margin: 0;
}

.report-row__tag {
  display: flex;
  justify-content: flex-end;
}
</style>
