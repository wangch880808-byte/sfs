<script lang="ts" setup>
import type { TableColumnsType } from 'ant-design-vue';

import type { AnalysisInsight, AnalysisMetric } from './analysis/types';

import type { StatisticsApi } from '#/api/edu/statistics';

import {
  computed,
  nextTick,
  onMounted,
  onUnmounted,
  ref,
  shallowRef,
  watch,
} from 'vue';

import { Page } from '@vben/common-ui';
import { usePreferences } from '@vben/preferences';

import { Select, Spin, Table } from 'ant-design-vue';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

import {
  getClassOptions,
  getCourseAnalysis,
  getCourseOptions,
  getTotalPassRate,
  getWorkTrend,
} from '#/api/edu/statistics';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { getAverageScoreTone } from '#/utils/status-statistics';

import AnalysisPanel from './analysis/analysis-panel.vue';
import {
  BAR_RADIUS_VERTICAL,
  getAnalysisChartTheme,
} from './analysis/chart-theme';
import AnalysisInsightList from './analysis/insight-list.vue';
import AnalysisMetricGrid from './analysis/metric-grid.vue';
import { buildProcessInsights, getFullPassRate } from './analysis/summary';
import { average, formatPercent, roundTo } from './chart-kit';

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer,
]);

type TrendPoint = {
  avgScore: number;
  homeworkName: string;
  maxScore: number;
  minScore: number;
};

type HomeworkAnalysis = {
  avgScore: number;
  gapScore: number;
  homeworkId?: number;
  homeworkName: string;
  lowScore: number;
  lowStudentName: string;
  topScore: number;
  topStudentName: string;
};

type WorkTrendPayload = {
  avgScores?: number[];
  items?: Array<{
    avgScore: number;
    homeworkName: string;
    maxScore: number;
    minScore: number;
  }>;
  maxScores?: number[];
  minScores?: number[];
  workNames?: string[];
};

type CourseAnalysisPayload = {
  homeworks?: Array<{
    avgScore: number;
    homeworkId?: number;
    homeworkName: string;
    last5?: Array<{ score: number; studentName: string }>;
    top5?: Array<{ score: number; studentName: string }>;
  }>;
  items?: Array<{
    avgScore: number;
    homeworkId?: number;
    homeworkName: string;
    topScore?: number;
    topStudentName?: string;
  }>;
};

const { isDark } = usePreferences();

const loading = ref(false);
const courseOptions = ref<StatisticsApi.CourseOption[]>([]);
const classOptions = ref<StatisticsApi.ClassOption[]>([]);
const selectedCourse = ref<number>();
const selectedClass = ref<number>();

const passRate = ref<StatisticsApi.PassRateRespVO>();
const trendPoints = ref<TrendPoint[]>([]);
const homeworkAnalyses = ref<HomeworkAnalysis[]>([]);

const trendChartRef = ref<HTMLElement>();
const scoreChartRef = ref<HTMLElement>();
const structureChartRef = ref<HTMLElement>();

const trendInst = shallowRef<echarts.ECharts>();
const scoreInst = shallowRef<echarts.ECharts>();
const structureInst = shallowRef<echarts.ECharts>();

function toOptionalNumber(value: unknown) {
  if (typeof value === 'number') return value;
  return typeof value === 'string' && value ? Number(value) : undefined;
}

const courseSelectOptions = computed(() =>
  courseOptions.value.map((item) => ({ label: item.name, value: item.id })),
);
const classSelectOptions = computed(() =>
  classOptions.value.map((item) => ({ label: item.name, value: item.id })),
);
/** 没有可分析的课程时整页给一个空状态，不再逐个面板提示 */
const noCourse = computed(
  () => !loading.value && courseOptions.value.length === 0,
);

const courseAverage = computed(() =>
  roundTo(average(trendPoints.value.map((item) => item.avgScore))),
);
const trendDelta = computed(() => {
  if (trendPoints.value.length < 2) return 0;
  const first = trendPoints.value[0]?.avgScore ?? 0;
  const last = trendPoints.value.at(-1)?.avgScore ?? 0;
  return roundTo(last - first);
});
const qualityRate = computed(() =>
  roundTo(
    (passRate.value?.goodRate ?? 0) + (passRate.value?.excellentRate ?? 0),
  ),
);
/** 整体通过率；没有已批改的任务（或没有成绩走势）时为 null，不判断达标情况 */
const fullPassRate = computed(() =>
  getFullPassRate(passRate.value, trendPoints.value.length),
);
const hasGradedData = computed(() => fullPassRate.value !== null);
const hasPassRate = computed(() =>
  [
    passRate.value?.unqualifiedRate,
    passRate.value?.qualifiedRate,
    passRate.value?.goodRate,
    passRate.value?.excellentRate,
  ].some((rate) => (rate ?? 0) > 0),
);
const scoreGap = computed(() => {
  if (trendPoints.value.length === 0) return 0;
  const highest = Math.max(...trendPoints.value.map((item) => item.maxScore));
  const lowest = Math.min(...trendPoints.value.map((item) => item.minScore));
  return highest - lowest;
});
const focusHomework = computed(() => {
  return homeworkAnalyses.value.toSorted((a, b) => a.avgScore - b.avgScore)[0];
});
const highlightHomework = computed(() => {
  return homeworkAnalyses.value.toSorted((a, b) => b.avgScore - a.avgScore)[0];
});

const overviewCards = computed<AnalysisMetric[]>(() => [
  {
    label: '已分析任务',
    value: `${trendPoints.value.length}`,
    meta:
      trendPoints.value.length > 0
        ? '已纳入过程分析的实训任务数'
        : '这门课程还没有可分析的任务',
  },
  {
    label: '课程均分',
    value: trendPoints.value.length > 0 ? `${courseAverage.value}` : '—',
    meta: trendPoints.value.length > 0 ? '基于全部任务均分计算' : '还没有成绩',
  },
  {
    label: '趋势变化',
    value:
      trendPoints.value.length >= 2
        ? `${trendDelta.value >= 0 ? '+' : ''}${trendDelta.value}`
        : '—',
    meta:
      trendPoints.value.length >= 2
        ? '最近一次任务均分相对第一次的变化'
        : '任务还不够多，暂不判断趋势',
  },
  {
    label: '优良占比',
    value: hasGradedData.value ? formatPercent(qualityRate.value) : '—',
    meta: hasGradedData.value
      ? '良好与优秀学生的合计占比'
      : '还没有已批改的任务',
  },
]);

/** 过程结论：没有已批改的任务时只给一条中性说明 */
const insightItems = computed<AnalysisInsight[]>(() =>
  buildProcessInsights({
    focusHomework: focusHomework.value,
    fullPassRate: fullPassRate.value,
    scoreGap: scoreGap.value,
    trendCount: trendPoints.value.length,
    trendDelta: trendDelta.value,
  }),
);

const benchmarkRows = computed(() => {
  return homeworkAnalyses.value
    .toSorted((a, b) => a.avgScore - b.avgScore)
    .slice(0, 4);
});

const benchmarkColumns: TableColumnsType = [
  {
    dataIndex: 'homeworkName',
    ellipsis: true,
    key: 'homeworkName',
    title: '任务',
  },
  { key: 'avgScore', title: '均分', width: 120 },
  { key: 'top', title: '最高分', width: 180 },
  { key: 'low', title: '最低分', width: 180 },
  { align: 'right', key: 'gap', title: '高低分差', width: 120 },
];

/** 面板没有数据时的空状态：选了班级说明是这个班级没有，否则是课程还没有 */
function emptyStateOf(subject: string, hint: string) {
  return selectedClass.value
    ? {
        description: '可以换一个班级，或清空班级查看全部班级。',
        kind: 'filtered' as const,
        title: `这个班级还没有${subject}`,
      }
    : { description: hint, kind: 'empty' as const, title: `还没有${subject}` };
}

function normalizeTrend(payload?: WorkTrendPayload) {
  if (Array.isArray(payload?.items)) {
    return payload.items.map((item) => ({
      homeworkName: item.homeworkName,
      avgScore: Number(item.avgScore ?? 0),
      maxScore: Number(item.maxScore ?? 0),
      minScore: Number(item.minScore ?? 0),
    }));
  }
  const names = Array.isArray(payload?.workNames) ? payload.workNames : [];
  const avgScores = Array.isArray(payload?.avgScores) ? payload.avgScores : [];
  const maxScores = Array.isArray(payload?.maxScores) ? payload.maxScores : [];
  const minScores = Array.isArray(payload?.minScores) ? payload.minScores : [];
  return names.map((homeworkName, index) => ({
    homeworkName,
    avgScore: Number(avgScores[index] ?? 0),
    maxScore: Number(maxScores[index] ?? 0),
    minScore: Number(minScores[index] ?? 0),
  }));
}

function normalizeCourseAnalysis(payload?: CourseAnalysisPayload) {
  if (Array.isArray(payload?.homeworks)) {
    return payload.homeworks.map((item) => {
      const top = item.top5?.[0];
      const last = item.last5?.[0];
      const topScore = Number(top?.score ?? 0);
      const lowScore = Number(last?.score ?? 0);
      return {
        homeworkId: item.homeworkId,
        homeworkName: item.homeworkName,
        avgScore: Number(item.avgScore ?? 0),
        topScore,
        topStudentName: top?.studentName || '-',
        lowScore,
        lowStudentName: last?.studentName || '-',
        gapScore: Math.max(0, topScore - lowScore),
      };
    });
  }
  return (payload?.items || []).map((item) => ({
    homeworkId: item.homeworkId,
    homeworkName: item.homeworkName,
    avgScore: Number(item.avgScore ?? 0),
    topScore: Number(item.topScore ?? 0),
    topStudentName: item.topStudentName || '-',
    lowScore: 0,
    lowStudentName: '-',
    gapScore: 0,
  }));
}

function renderTrendChart() {
  if (!trendChartRef.value) return;
  trendInst.value?.dispose();
  const chart = echarts.init(
    trendChartRef.value,
    isDark.value ? 'dark' : undefined,
  );
  trendInst.value = chart;
  const theme = getAnalysisChartTheme();
  const { series } = theme;
  const names = trendPoints.value.map((item) => item.homeworkName);
  chart.setOption({
    ...theme.base,
    // 最高分、平均分、最低分：绿、蓝、红
    color: [series[1], series[0], series[3]],
    tooltip: { ...theme.tooltip, trigger: 'axis' },
    legend: {
      ...theme.legend,
      data: ['最高分', '平均分', '最低分'],
      right: 0,
      top: 0,
    },
    grid: { bottom: 0, containLabel: true, left: 0, right: 8, top: 40 },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        ...theme.axisLabel,
        rotate: names.length > 5 ? 22 : 0,
      },
      axisLine: theme.axisLine,
      axisTick: theme.axisTick,
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: theme.axisLabel,
      axisLine: { show: false },
      splitLine: theme.splitLine,
    },
    series: [
      {
        name: '最高分',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trendPoints.value.map((item) => item.maxScore),
      },
      {
        name: '平均分',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 3 },
        data: trendPoints.value.map((item) => item.avgScore),
      },
      {
        name: '最低分',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trendPoints.value.map((item) => item.minScore),
      },
    ],
  });
}

function renderScoreChart() {
  if (!scoreChartRef.value) return;
  scoreInst.value?.dispose();
  const chart = echarts.init(
    scoreChartRef.value,
    isDark.value ? 'dark' : undefined,
  );
  scoreInst.value = chart;
  const theme = getAnalysisChartTheme();
  const { series } = theme;
  const names = homeworkAnalyses.value.map((item) => item.homeworkName);
  chart.setOption({
    ...theme.base,
    color: [series[0], series[2]],
    tooltip: { ...theme.tooltip, trigger: 'axis' },
    legend: {
      ...theme.legend,
      data: ['任务均分', '最高分'],
      right: 0,
      top: 0,
    },
    grid: { bottom: 0, containLabel: true, left: 0, right: 8, top: 40 },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        ...theme.axisLabel,
        rotate: names.length > 5 ? 18 : 0,
      },
      axisLine: theme.axisLine,
      axisTick: theme.axisTick,
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: theme.axisLabel,
      axisLine: { show: false },
      splitLine: theme.splitLine,
    },
    series: [
      {
        name: '任务均分',
        type: 'bar',
        barWidth: '42%',
        barMaxWidth: 32,
        data: homeworkAnalyses.value.map((item) => roundTo(item.avgScore)),
        itemStyle: { borderRadius: BAR_RADIUS_VERTICAL },
      },
      {
        name: '最高分',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: homeworkAnalyses.value.map((item) => item.topScore),
      },
    ],
  });
}

function renderStructureChart() {
  if (!structureChartRef.value) return;
  structureInst.value?.dispose();
  const chart = echarts.init(
    structureChartRef.value,
    isDark.value ? 'dark' : undefined,
  );
  structureInst.value = chart;
  const theme = getAnalysisChartTheme();
  const { series } = theme;
  const segments = [
    { name: '不合格', value: roundTo(passRate.value?.unqualifiedRate ?? 0) },
    { name: '合格', value: roundTo(passRate.value?.qualifiedRate ?? 0) },
    { name: '良好', value: roundTo(passRate.value?.goodRate ?? 0) },
    { name: '优秀', value: roundTo(passRate.value?.excellentRate ?? 0) },
  ];
  chart.setOption({
    ...theme.base,
    // 不合格、合格、良好、优秀：红、黄、蓝、绿
    color: [series[3], series[2], series[0], series[1]],
    tooltip: {
      ...theme.tooltip,
      trigger: 'item',
      formatter: '{b}：{c}%',
    },
    legend: {
      ...theme.legend,
      bottom: 0,
    },
    series: [
      {
        type: 'pie',
        radius: ['48%', '68%'],
        center: ['50%', '44%'],
        label: {
          color: theme.palette.text,
          formatter: '{b}\n{c}%',
        },
        // 段与段之间用面板色隔开；只有一段时不加，免得整环在起点处断开
        itemStyle: {
          borderColor: theme.palette.surface,
          borderRadius: 4,
          borderWidth:
            segments.filter((item) => item.value > 0).length > 1 ? 2 : 0,
        },
        // 占比为 0 的段没有弧，不显示标签，免得挤在一起
        data: segments.map((item) =>
          item.value > 0
            ? item
            : { ...item, label: { show: false }, labelLine: { show: false } },
        ),
      },
    ],
  });
}

function renderAllCharts() {
  renderTrendChart();
  renderScoreChart();
  renderStructureChart();
}

function handleResize() {
  trendInst.value?.resize();
  scoreInst.value?.resize();
  structureInst.value?.resize();
}

async function loadClassOptions(courseId?: number) {
  classOptions.value = courseId ? await getClassOptions(courseId) : [];
}

async function loadAnalysisData() {
  if (!selectedCourse.value) {
    trendPoints.value = [];
    homeworkAnalyses.value = [];
    passRate.value = undefined;
    await nextTick();
    renderAllCharts();
    return;
  }
  loading.value = true;
  try {
    const [passResp, trendResp, courseResp] = await Promise.all([
      getTotalPassRate({
        courseId: selectedCourse.value,
        classId: selectedClass.value,
      }),
      getWorkTrend(selectedCourse.value, selectedClass.value),
      getCourseAnalysis(selectedCourse.value, selectedClass.value),
    ]);
    passRate.value = passResp;
    trendPoints.value = normalizeTrend(trendResp as WorkTrendPayload);
    homeworkAnalyses.value = normalizeCourseAnalysis(
      courseResp as CourseAnalysisPayload,
    );
    await nextTick();
    renderAllCharts();
  } finally {
    loading.value = false;
  }
}

async function onCourseChange(value?: unknown) {
  const courseId = toOptionalNumber(value);
  selectedCourse.value = courseId;
  selectedClass.value = undefined;
  await loadClassOptions(courseId);
  await loadAnalysisData();
}

async function onClassChange(value?: unknown) {
  selectedClass.value = toOptionalNumber(value);
  await loadAnalysisData();
}

async function init() {
  loading.value = true;
  try {
    courseOptions.value = (await getCourseOptions()) || [];
    selectedCourse.value = courseOptions.value[0]?.id;
    await loadClassOptions(selectedCourse.value);
    await loadAnalysisData();
  } finally {
    loading.value = false;
  }
}

watch(isDark, async () => {
  await nextTick();
  renderAllCharts();
});

onMounted(() => {
  init();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  trendInst.value?.dispose();
  scoreInst.value?.dispose();
  structureInst.value?.dispose();
});
</script>

<template>
  <Page
    auto-content-height
    description="按任务成绩走势、成绩结构与高低分差，观察课程推进的节奏与达成情况。"
    title="教学过程分析"
  >
    <Spin :spinning="loading" tip="正在读取教学过程数据…">
      <EmptyState
        v-if="noCourse"
        description="课程布置实训任务并完成批改后，可以在这里查看教学过程分析。"
        kind="blocked"
        title="还没有可分析的课程"
      />
      <div v-else class="analysis-page">
        <div class="analysis-filter">
          <Select
            v-model:value="selectedCourse"
            :options="courseSelectOptions"
            aria-label="课程"
            class="analysis-filter__select"
            placeholder="请选择课程"
            @change="onCourseChange"
          />
          <Select
            v-model:value="selectedClass"
            :disabled="!selectedCourse"
            :options="classSelectOptions"
            allow-clear
            aria-label="班级"
            class="analysis-filter__select"
            placeholder="全部班级"
            @change="onClassChange"
          />
        </div>

        <AnalysisMetricGrid :items="overviewCards" />

        <section
          v-if="highlightHomework && focusHomework"
          aria-label="过程观察摘要"
          class="process-highlights surface-panel"
        >
          <div class="process-highlight">
            <StatusTag label="表现最佳任务" tone="done" />
            <p class="process-highlight__value">
              {{ highlightHomework.homeworkName }}
            </p>
            <p class="process-highlight__meta">
              均分 {{ roundTo(highlightHomework.avgScore) }} 分，最高分
              {{ highlightHomework.topStudentName || '-' }}
              {{ highlightHomework.topScore }} 分
            </p>
          </div>
          <div class="process-highlight">
            <StatusTag label="重点跟进任务" tone="waiting" />
            <p class="process-highlight__value">
              {{ focusHomework.homeworkName }}
            </p>
            <p class="process-highlight__meta">
              当前均分
              {{ roundTo(focusHomework.avgScore) }} 分，最高分与最低分相差
              {{ focusHomework.gapScore }} 分，建议优先补充示例和讲评。
            </p>
          </div>
        </section>

        <div class="analysis-row">
          <AnalysisPanel
            description="同时观察最高分、平均分与最低分，判断过程推进中的稳定性。"
            title="任务成绩波动"
          >
            <div
              v-if="trendPoints.length > 0"
              ref="trendChartRef"
              class="analysis-chart"
            ></div>
            <EmptyState
              v-else
              compact
              v-bind="
                emptyStateOf(
                  '任务成绩',
                  '任务批改后，这里会显示最高分、平均分与最低分的走势。',
                )
              "
            />
          </AnalysisPanel>

          <AnalysisPanel
            description="按成绩段查看整体达标情况与高分学生占比。"
            title="成绩结构分布"
          >
            <div
              v-if="hasPassRate"
              ref="structureChartRef"
              class="analysis-chart"
            ></div>
            <EmptyState
              v-else
              compact
              v-bind="
                emptyStateOf(
                  '成绩结构数据',
                  '任务批改后，这里会按不合格、合格、良好、优秀显示占比。',
                )
              "
            />
          </AnalysisPanel>
        </div>

        <div class="analysis-row">
          <AnalysisPanel
            description="在同一张图里对比任务均分与最高分，判断任务难度和高分学生的表现。"
            title="任务均分与最高分"
          >
            <div
              v-if="homeworkAnalyses.length > 0"
              ref="scoreChartRef"
              class="analysis-chart"
            ></div>
            <EmptyState
              v-else
              compact
              v-bind="
                emptyStateOf(
                  '任务分析数据',
                  '任务批改后，这里会对比每个任务的均分与最高分。',
                )
              "
            />
          </AnalysisPanel>

          <AnalysisPanel
            description="以下结论由页面根据任务统计自动提炼，不替代教师的专业判断。"
            title="过程结论"
          >
            <AnalysisInsightList :items="insightItems" />
          </AnalysisPanel>
        </div>

        <AnalysisPanel
          description="按均分从低到高列出要优先跟进的任务，以及每个任务的最高分、最低分与分差。"
          title="任务跟进清单"
        >
          <Table
            v-if="benchmarkRows.length > 0"
            :columns="benchmarkColumns"
            :data-source="benchmarkRows"
            :pagination="false"
            row-key="homeworkName"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'avgScore'">
                <StatusTag
                  :label="`${roundTo(record.avgScore)}`"
                  :tone="getAverageScoreTone(record.avgScore)"
                  class="analysis-number"
                />
              </template>
              <template v-else-if="column.key === 'top'">
                {{ record.topStudentName }}
                <span class="analysis-number">{{ record.topScore }}</span> 分
              </template>
              <template v-else-if="column.key === 'low'">
                {{ record.lowStudentName }}
                <span class="analysis-number">{{ record.lowScore }}</span> 分
              </template>
              <template v-else-if="column.key === 'gap'">
                <span class="analysis-number">{{ record.gapScore }}</span> 分
              </template>
            </template>
          </Table>
          <EmptyState
            v-else
            compact
            v-bind="
              emptyStateOf(
                '需要跟进的任务',
                '任务批改后，这里会按均分从低到高列出需要跟进的任务。',
              )
            "
          />
        </AnalysisPanel>
      </div>
    </Spin>
  </Page>
</template>

<style scoped src="./analysis/layout.css"></style>

<style scoped>
/* 过程观察摘要：一块面板分左右两栏，中间用分隔线，不再套卡片；窄屏上下排列 */
@media (width < 768px) {
  .process-highlight + .process-highlight {
    border-top: 1px solid var(--nus-border-subtle);
  }
}

@media (width >= 768px) {
  .process-highlights {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .process-highlight + .process-highlight {
    border-left: 1px solid var(--nus-border-subtle);
  }
}

.process-highlights {
  display: grid;
}

.process-highlight {
  min-width: 0;
  padding: var(--nus-space-4) var(--nus-card-padding);
}

.process-highlight__value {
  margin: var(--nus-space-2) 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
  white-space: nowrap;
}

.process-highlight__meta {
  margin: var(--nus-space-1) 0 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}
</style>
