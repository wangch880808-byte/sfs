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

import { Button, message, Select, Spin, Table } from 'ant-design-vue';
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

import { streamClassAnalysis } from '#/api/edu/ai-review';
import {
  getClassOptions,
  getCourseAnalysis,
  getCourseOptions,
  getTotalPassRate,
  getWorkTrend,
} from '#/api/edu/statistics';
import { EmptyState } from '#/components/empty-state';
import { MarkdownView } from '#/components/markdown-view';
import { StatusTag } from '#/components/status-tag';
import { getRiskLevelMeta, getTaskRiskTone } from '#/utils/status-statistics';

import AnalysisPanel from './analysis/analysis-panel.vue';
import {
  BAR_RADIUS_HORIZONTAL,
  BAR_RADIUS_VERTICAL,
  getAnalysisChartTheme,
} from './analysis/chart-theme';
import AnalysisInsightList from './analysis/insight-list.vue';
import AnalysisMetricGrid from './analysis/metric-grid.vue';
import {
  buildIssueDimensions,
  buildIssueInsights,
  getFullPassRate,
  getIssueOverallScore,
} from './analysis/summary';
import { average, clampPercent, roundTo } from './chart-kit';

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

type TrendPoint = {
  avgScore: number;
  homeworkName: string;
  maxScore: number;
  minScore: number;
};

type HomeworkRiskRow = {
  avgScore: number;
  gapScore: number;
  homeworkId?: number;
  homeworkName: string;
  lowScore: number;
  riskScore: number;
  topScore: number;
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
};

const { isDark } = usePreferences();

const loading = ref(false);
const aiLoading = ref(false);
const aiAbortController = ref<AbortController>();
const courseOptions = ref<StatisticsApi.CourseOption[]>([]);
const classOptions = ref<StatisticsApi.ClassOption[]>([]);
const selectedCourse = ref<number>();
const selectedClass = ref<number>();
const selectedHomework = ref<number>();

const passRate = ref<StatisticsApi.PassRateRespVO>();
const trendPoints = ref<TrendPoint[]>([]);
const taskRisks = ref<HomeworkRiskRow[]>([]);
const aiReport = ref('');

const radarChartRef = ref<HTMLElement>();
const heatChartRef = ref<HTMLElement>();
const taskChartRef = ref<HTMLElement>();
const aiReportRef = ref<HTMLElement>();

const radarInst = shallowRef<echarts.ECharts>();
const heatInst = shallowRef<echarts.ECharts>();
const taskInst = shallowRef<echarts.ECharts>();

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
const homeworkSelectOptions = computed(() =>
  taskRisks.value
    .filter((item) => item.homeworkId)
    .map((item) => ({ label: item.homeworkName, value: item.homeworkId })),
);
/** 没有可分析的课程时整页给一个空状态，不再逐个面板提示 */
const noCourse = computed(
  () => !loading.value && courseOptions.value.length === 0,
);
const avgScore = computed(() =>
  roundTo(average(trendPoints.value.map((item) => item.avgScore))),
);
/** 整体通过率；没有已批改的任务（或没有成绩走势）时为 null，得分、风险与结论都不下判断 */
const fullPassRate = computed(() =>
  getFullPassRate(passRate.value, trendPoints.value.length),
);
const hasData = computed(() => fullPassRate.value !== null);
const aiDisabled = computed(
  () =>
    !hasData.value ||
    !selectedCourse.value ||
    !selectedClass.value ||
    !selectedHomework.value,
);
const qualityRate = computed(() =>
  roundTo(
    (passRate.value?.goodRate ?? 0) + (passRate.value?.excellentRate ?? 0),
  ),
);
const trendDelta = computed(() => {
  if (trendPoints.value.length < 2) return 0;
  return roundTo(
    (trendPoints.value.at(-1)?.avgScore ?? 0) -
      (trendPoints.value[0]?.avgScore ?? 0),
  );
});
const volatility = computed(() => {
  if (trendPoints.value.length < 2) return 0;
  const changes: number[] = [];
  for (let index = 1; index < trendPoints.value.length; index += 1) {
    changes.push(
      Math.abs(
        (trendPoints.value[index]?.avgScore ?? 0) -
          (trendPoints.value[index - 1]?.avgScore ?? 0),
      ),
    );
  }
  return roundTo(average(changes));
});
const polarizationGap = computed(() =>
  roundTo(average(taskRisks.value.map((item) => item.gapScore))),
);
const focusTask = computed(() => taskRisks.value[0]);
const topRiskRows = computed(() => taskRisks.value.slice(0, 4));

/** 四个维度得分；没有已批改的任务时为空，不画雷达图和热度图 */
const dimensionScores = computed(() =>
  buildIssueDimensions({
    avgScore: avgScore.value,
    fullPassRate: fullPassRate.value,
    polarizationGap: polarizationGap.value,
    qualityRate: qualityRate.value,
    trendDelta: trendDelta.value,
    volatility: volatility.value,
  }),
);

const overallScore = computed(() =>
  getIssueOverallScore(dimensionScores.value),
);
const riskLevel = computed(() =>
  overallScore.value === null ? null : getRiskLevelMeta(overallScore.value),
);

const derivedIssues = computed<AnalysisInsight[]>(() =>
  buildIssueInsights({
    focusTask: focusTask.value,
    fullPassRate: fullPassRate.value,
    polarizationGap: polarizationGap.value,
    trendCount: trendPoints.value.length,
    trendDelta: trendDelta.value,
  }),
);

const hasAiReport = computed(() => aiReport.value.trim().length > 0);

const overviewCards = computed<AnalysisMetric[]>(() => [
  {
    label: '综合研判得分',
    value: overallScore.value === null ? '暂无数据' : `${overallScore.value}`,
    meta: hasData.value
      ? '基于任务均分、通过率、波动与分层差距推导'
      : '还没有已批改的任务，暂不研判',
  },
  {
    label: '风险等级',
    value: riskLevel.value?.label ?? '暂无数据',
    tone: riskLevel.value?.tone,
    meta: '用于标记当前班级在该课程中的风险级别',
  },
  {
    label: '波动指数',
    value:
      hasData.value && trendPoints.value.length >= 2
        ? `${volatility.value}`
        : '—',
    meta: '相邻两次任务均分的平均变化幅度',
  },
  {
    label: '重点问题数',
    value: `${taskRisks.value.filter((item) => item.riskScore >= 45).length}`,
    meta: '需要优先跟进的高风险任务数量',
  },
]);

const taskColumns: TableColumnsType = [
  {
    dataIndex: 'homeworkName',
    ellipsis: true,
    key: 'homeworkName',
    title: '任务',
  },
  { key: 'riskScore', title: '风险热度', width: 112 },
  { align: 'right', key: 'avgScore', title: '均分', width: 88 },
  {
    align: 'right',
    dataIndex: 'gapScore',
    key: 'gapScore',
    title: '高低分差',
    width: 88,
  },
  {
    align: 'right',
    dataIndex: 'lowScore',
    key: 'lowScore',
    title: '最低分',
    width: 88,
  },
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

/** 研判面板（维度、热度、结论）没有已批改的任务时的空状态 */
const noDataState = computed(() =>
  emptyStateOf(
    '已批改的任务',
    '任务批改后，这里会按教学节奏、任务适配、分层支持和结果保障给出研判。',
  ),
);

/**
 * AI 研判报告还没有内容时：没有已批改的任务、缺班级或任务都是前置条件不满足，选好了就是还没生成
 */
const aiEmptyState = computed(() => {
  if (selectedCourse.value && !hasData.value) {
    return {
      description: '任务批改后，才能根据统计生成 AI 研判。',
      kind: 'blocked' as const,
      title: selectedClass.value
        ? '这个班级还没有已批改的任务'
        : '还没有已批改的任务',
    };
  }
  return aiDisabled.value
    ? {
        description: 'AI 研判按班级和实训任务生成，选好后点击 AI 研判。',
        kind: 'blocked' as const,
        title: '先选择班级和实训任务',
      }
    : {
        description: '点击 AI 研判，根据当前班级与任务的统计生成辅助分析。',
        kind: 'empty' as const,
        title: '还没有研判报告',
      };
});

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

function normalizeTaskRisks(payload?: CourseAnalysisPayload) {
  return (payload?.homeworks || [])
    .map((item) => {
      const topScore = Number(item.top5?.[0]?.score ?? 0);
      const lowScore = Number(item.last5?.[0]?.score ?? 0);
      const avg = Number(item.avgScore ?? 0);
      const gap = Math.max(0, topScore - lowScore);
      const riskScore = roundTo(clampPercent((100 - avg) * 0.65 + gap * 0.35));
      return {
        homeworkId: item.homeworkId,
        homeworkName: item.homeworkName,
        avgScore: avg,
        topScore,
        lowScore,
        gapScore: gap,
        riskScore,
      };
    })
    .toSorted((a, b) => b.riskScore - a.riskScore);
}

function renderRadarChart() {
  if (!radarChartRef.value) return;
  radarInst.value?.dispose();
  const chart = echarts.init(
    radarChartRef.value,
    isDark.value ? 'dark' : undefined,
  );
  radarInst.value = chart;
  const theme = getAnalysisChartTheme();
  const color = theme.series[0];
  chart.setOption({
    ...theme.base,
    tooltip: theme.tooltip,
    radar: {
      radius: '68%',
      center: ['50%', '54%'],
      indicator: dimensionScores.value.map((item) => ({
        name: item.name,
        max: 100,
      })),
      splitLine: { lineStyle: { color: theme.palette.border } },
      splitArea: { areaStyle: { color: ['transparent'] } },
      axisName: { color: theme.palette.muted },
      axisLine: { lineStyle: { color: theme.palette.border } },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            name: '维度得分',
            value: dimensionScores.value.map((item) => item.score),
            areaStyle: { color, opacity: 0.16 },
            lineStyle: { color, width: 2 },
            itemStyle: { color },
            symbolSize: 6,
          },
        ],
      },
    ],
  });
}

function renderHeatChart() {
  if (!heatChartRef.value) return;
  heatInst.value?.dispose();
  const chart = echarts.init(
    heatChartRef.value,
    isDark.value ? 'dark' : undefined,
  );
  heatInst.value = chart;
  const theme = getAnalysisChartTheme();
  const data = dimensionScores.value.map((item) => ({
    name: item.name,
    value: roundTo(100 - item.score),
  }));
  chart.setOption({
    ...theme.base,
    color: [theme.series[2]],
    tooltip: { ...theme.tooltip, trigger: 'axis' },
    grid: { bottom: 0, containLabel: true, left: 0, right: 32, top: 8 },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: theme.axisLabel,
      axisLine: { show: false },
      splitLine: theme.splitLine,
    },
    yAxis: {
      type: 'category',
      data: data.map((item) => item.name),
      axisLabel: theme.axisLabel,
      axisLine: theme.axisLine,
      axisTick: theme.axisTick,
    },
    series: [
      {
        name: '问题热度',
        type: 'bar',
        barWidth: 16,
        label: {
          show: true,
          position: 'right',
          color: theme.palette.text,
          formatter: ({ value }: { value: number }) => `${value}`,
        },
        itemStyle: { borderRadius: BAR_RADIUS_HORIZONTAL },
        data: data.map((item) => item.value),
      },
    ],
  });
}

function renderTaskChart() {
  if (!taskChartRef.value) return;
  taskInst.value?.dispose();
  const chart = echarts.init(
    taskChartRef.value,
    isDark.value ? 'dark' : undefined,
  );
  taskInst.value = chart;
  const theme = getAnalysisChartTheme();
  const { series } = theme;
  const rows = taskRisks.value.slice(0, 6);
  chart.setOption({
    ...theme.base,
    // 风险热度用红，高低分差用蓝
    color: [series[3], series[0]],
    tooltip: { ...theme.tooltip, trigger: 'axis' },
    legend: {
      ...theme.legend,
      data: ['风险热度', '高低分差'],
      right: 0,
      top: 0,
    },
    grid: { bottom: 0, containLabel: true, left: 0, right: 8, top: 40 },
    xAxis: {
      type: 'category',
      data: rows.map((item) => item.homeworkName),
      axisLabel: {
        ...theme.axisLabel,
        rotate: rows.length > 4 ? 18 : 0,
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
        name: '风险热度',
        type: 'bar',
        barWidth: '40%',
        barMaxWidth: 32,
        itemStyle: { borderRadius: BAR_RADIUS_VERTICAL },
        data: rows.map((item) => item.riskScore),
      },
      {
        name: '高低分差',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: rows.map((item) => item.gapScore),
      },
    ],
  });
}

function renderAllCharts() {
  renderRadarChart();
  renderHeatChart();
  renderTaskChart();
}

function handleResize() {
  radarInst.value?.resize();
  heatInst.value?.resize();
  taskInst.value?.resize();
}

async function loadClassOptions(courseId?: number) {
  classOptions.value = courseId ? await getClassOptions(courseId) : [];
}

async function loadAnalysis() {
  if (!selectedCourse.value) {
    passRate.value = undefined;
    trendPoints.value = [];
    taskRisks.value = [];
    selectedHomework.value = undefined;
    aiReport.value = '';
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
    taskRisks.value = normalizeTaskRisks(courseResp as CourseAnalysisPayload);
    if (
      !taskRisks.value.some(
        (item) => item.homeworkId === selectedHomework.value,
      )
    ) {
      selectedHomework.value = taskRisks.value.find(
        (item) => item.homeworkId,
      )?.homeworkId;
    }
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
  selectedHomework.value = undefined;
  aiReport.value = '';
  await loadClassOptions(courseId);
  await loadAnalysis();
}

async function onClassChange(value?: unknown) {
  selectedClass.value = toOptionalNumber(value);
  selectedHomework.value = undefined;
  aiReport.value = '';
  await loadAnalysis();
}

async function handleAiAnalysis() {
  if (aiDisabled.value) return;
  if (!selectedCourse.value || !selectedClass.value || !selectedHomework.value)
    return;
  if (taskRisks.value.length === 0) {
    aiReport.value = '这个班级还没有实训任务，无法生成 AI 研判。';
    message.warning('这个班级还没有实训任务，无法生成 AI 研判');
    return;
  }
  aiAbortController.value?.abort();
  aiLoading.value = true;
  aiReport.value = '';
  const controller = new AbortController();
  aiAbortController.value = controller;
  try {
    await streamClassAnalysis({
      ctrl: controller,
      data: {
        classId: selectedClass.value,
        courseId: selectedCourse.value,
        homeworkId: selectedHomework.value,
      },
      onMessage(content) {
        aiReport.value += content;
      },
    });
    const report = aiReport.value.trim();
    if (!report) {
      aiReport.value = 'AI 研判没有返回内容。请检查默认模型配置后重试。';
      message.warning('AI 研判没有返回内容');
      return;
    }
    aiReport.value = report;
    message.success('AI 研判已生成');
  } catch (error: any) {
    const errorMessage =
      error?.msg || error?.message || '原因未知，请稍后重试。';
    aiReport.value = `AI 研判没有成功：${errorMessage}`;
    message.error(aiReport.value);
  } finally {
    aiLoading.value = false;
    aiAbortController.value = undefined;
  }
}

/** 页头的 AI 研判：报告面板在页面底部，开始生成时把它滚进视野 */
function handleAiAnalysisFromHeader() {
  const task = handleAiAnalysis();
  nextTick(() => {
    const reduceMotion = window.matchMedia?.(
      '(prefers-reduced-motion: reduce)',
    ).matches;
    aiReportRef.value?.scrollIntoView({
      behavior: reduceMotion ? 'auto' : 'smooth',
      block: 'start',
    });
  });
  return task;
}

async function init() {
  loading.value = true;
  try {
    courseOptions.value = (await getCourseOptions()) || [];
    selectedCourse.value = courseOptions.value[0]?.id;
    await loadClassOptions(selectedCourse.value);
    await loadAnalysis();
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
  aiAbortController.value?.abort();
  window.removeEventListener('resize', handleResize);
  radarInst.value?.dispose();
  heatInst.value?.dispose();
  taskInst.value?.dispose();
});
</script>

<template>
  <Page
    auto-content-height
    description="根据任务均分、通过率、波动与高低分差推导研判指标，帮你找出需要优先处理的教学问题。"
    title="教学问题研判"
  >
    <template #extra>
      <Button
        :disabled="aiDisabled"
        :loading="aiLoading"
        type="primary"
        @click="handleAiAnalysisFromHeader"
      >
        AI 研判
      </Button>
    </template>

    <Spin :spinning="loading" tip="正在读取教学问题研判数据…">
      <EmptyState
        v-if="noCourse"
        description="课程布置实训任务并完成批改后，可以在这里研判教学问题。"
        kind="blocked"
        title="还没有可研判的课程"
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
            placeholder="请选择班级"
            @change="onClassChange"
          />
          <Select
            v-model:value="selectedHomework"
            :disabled="!selectedClass"
            :options="homeworkSelectOptions"
            allow-clear
            aria-label="实训任务"
            class="analysis-filter__select"
            placeholder="请选择实训任务"
          />
        </div>

        <AnalysisMetricGrid :items="overviewCards" />

        <div class="analysis-row">
          <AnalysisPanel
            description="四个维度分别观察教学节奏、任务适配、分层支持和结果保障。"
            title="研判维度雷达"
          >
            <EmptyState
              v-if="!selectedCourse"
              compact
              description="在上方选择课程后生成维度雷达。"
              kind="blocked"
              title="先选择课程"
            />
            <div
              v-else-if="dimensionScores.length > 0"
              ref="radarChartRef"
              class="analysis-chart analysis-chart--tall"
            ></div>
            <EmptyState v-else compact v-bind="noDataState" />
          </AnalysisPanel>

          <AnalysisPanel
            description="热度越高，说明该维度当前越值得优先处理。"
            title="问题热度"
          >
            <div
              v-if="dimensionScores.length > 0"
              ref="heatChartRef"
              class="analysis-chart analysis-chart--tall"
            ></div>
            <EmptyState
              v-else-if="!selectedCourse"
              compact
              description="选择课程后，这里会按维度显示问题热度。"
              kind="blocked"
              title="先选择课程"
            />
            <EmptyState v-else compact v-bind="noDataState" />
          </AnalysisPanel>
        </div>

        <div class="analysis-row">
          <AnalysisPanel
            description="风险热度综合考虑任务均分偏低与高低分差过大两种情况。"
            title="高风险任务画像"
          >
            <template v-if="taskRisks.length > 0">
              <div
                ref="taskChartRef"
                class="analysis-chart analysis-chart--short"
              ></div>
              <Table
                :columns="taskColumns"
                :data-source="topRiskRows"
                :pagination="false"
                class="issue-task-table"
                row-key="homeworkName"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'riskScore'">
                    <StatusTag
                      :label="`${record.riskScore}`"
                      :tone="getTaskRiskTone(record.riskScore)"
                      class="analysis-number"
                    />
                  </template>
                  <template v-else-if="column.key === 'avgScore'">
                    <span class="analysis-number">
                      {{ roundTo(record.avgScore) }}
                    </span>
                  </template>
                  <template
                    v-else-if="
                      column.key === 'gapScore' || column.key === 'lowScore'
                    "
                  >
                    <span class="analysis-number">
                      {{ record[column.key] }}
                    </span>
                  </template>
                </template>
              </Table>
            </template>
            <EmptyState
              v-else
              compact
              v-bind="
                emptyStateOf(
                  '任务研判数据',
                  '任务批改后，这里会按风险热度列出需要关注的任务。',
                )
              "
            />
          </AnalysisPanel>

          <AnalysisPanel
            description="下列结论由当前课程统计自动推导，用于辅助教师确定处理顺序。"
            title="问题结论"
          >
            <AnalysisInsightList
              v-if="derivedIssues.length > 0"
              :items="derivedIssues"
            />
            <EmptyState v-else compact v-bind="noDataState" />
          </AnalysisPanel>
        </div>

        <div ref="aiReportRef" class="issue-report-anchor">
          <AnalysisPanel
            description="选择班级和实训任务后，可生成基于课程现状的辅助分析，用于补充教师判断。"
            title="AI 研判报告"
          >
            <template #extra>
              <Button
                :disabled="aiDisabled"
                :loading="aiLoading"
                @click="handleAiAnalysis"
              >
                {{ hasAiReport ? '重新生成' : '生成研判' }}
              </Button>
            </template>
            <MarkdownView
              v-if="hasAiReport"
              :content="aiReport"
              class="issue-report"
            />
            <div
              v-else-if="aiLoading"
              class="issue-report-loading"
              role="status"
            >
              <Spin size="small" />
              <span>正在生成研判报告，内容会边生成边显示。</span>
            </div>
            <EmptyState v-else compact v-bind="aiEmptyState" />
          </AnalysisPanel>
        </div>
      </div>
    </Spin>
  </Page>
</template>

<style scoped src="./analysis/layout.css"></style>

<style scoped>
.issue-task-table {
  margin-top: var(--nus-space-4);
}

/* 页头点 AI 研判后滚到报告面板，留出一点上边距 */
.issue-report-anchor {
  scroll-margin-top: var(--nus-space-4);
}

.issue-report-loading {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  padding: var(--nus-space-4) 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

/*
 * AI 研判报告：MarkdownView 自带的字号与文字颜色是写死的浅色值，深色下看不清，这里按界面规范改用 token。
 * 标题统一 15/600，正文 14，行内代码用凹陷底。
 */
.issue-report {
  font-family: inherit;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.issue-report :deep(h1),
.issue-report :deep(h2),
.issue-report :deep(h3),
.issue-report :deep(h4),
.issue-report :deep(h5),
.issue-report :deep(h6) {
  margin: var(--nus-space-4) 0 var(--nus-space-2);
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.issue-report > :deep(:first-child) {
  margin-top: 0;
}

.issue-report :deep(p),
.issue-report :deep(li) {
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.issue-report :deep(p + p),
.issue-report :deep(ul + p),
.issue-report :deep(ol + p),
.issue-report :deep(p + ul),
.issue-report :deep(p + ol) {
  margin-top: var(--nus-space-2);
}

.issue-report :deep(ul),
.issue-report :deep(ol) {
  padding-left: 20px;
  margin: var(--nus-space-2) 0 0;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.issue-report :deep(li) {
  margin: var(--nus-space-1) 0 0;
}

.issue-report :deep(strong) {
  font-weight: var(--nus-fw-semibold);
  color: var(--nus-text);
}

.issue-report :deep(a) {
  color: var(--nus-primary);
}

.issue-report :deep(blockquote) {
  padding-left: var(--nus-space-3);
  margin: var(--nus-space-2) 0 0;
  color: var(--nus-text-secondary);
  border-left: 2px solid var(--nus-border);
}

.issue-report :deep(:not(pre) > code) {
  padding: 1px 6px;
  font-family: var(--nus-font-mono);
  font-size: var(--nus-fs-dense);
  color: var(--nus-text);
  background: var(--nus-bg-sunken);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-sm);
}

.issue-report :deep(table) {
  margin-top: var(--nus-space-2);
  font-size: var(--nus-fs-dense);
  border-collapse: collapse;
}

.issue-report :deep(th),
.issue-report :deep(td) {
  padding: 6px 10px;
  color: var(--nus-text);
  text-align: left;
  border: 1px solid var(--nus-border-subtle);
}

.issue-report :deep(th) {
  font-weight: var(--nus-fw-medium);
  background: var(--nus-bg-sunken);
}
</style>
