<script lang="ts" setup>
import type { TableColumnsType } from 'ant-design-vue';

import type { AnalysisInsight, AnalysisMetric } from './analysis/types';

import type { StudentCapacityApi } from '#/api/edu/student/capacity';
import type { StatusTone } from '#/styles/theme/palette';

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

import { Progress, Spin, Table, Tag } from 'ant-design-vue';
import { BarChart, LineChart, RadarChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  RadarComponent,
  TooltipComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

import {
  getCapacityAnalysis,
  getCapacityDetail,
  getReportBasic,
  getReportSkills,
  getReportTrend,
} from '#/api/edu/student/capacity';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import {
  getInsightToneAtLeast,
  getQualifiedLevelMeta,
  getQualifiedRateTone,
  getSkillTaskStatusMeta,
} from '#/utils/status-statistics';

import AnalysisPanel from './analysis/analysis-panel.vue';
import {
  BAR_RADIUS_HORIZONTAL,
  getAnalysisChartTheme,
} from './analysis/chart-theme';
import AnalysisInsightList from './analysis/insight-list.vue';
import AnalysisMetricGrid from './analysis/metric-grid.vue';
import {
  applyCapacityChartOption,
  average,
  formatPercent,
  roundTo,
} from './chart-kit';

echarts.use([
  BarChart,
  LineChart,
  RadarChart,
  GridComponent,
  LegendComponent,
  RadarComponent,
  TooltipComponent,
  CanvasRenderer,
]);

type IndicatorSummary = {
  evaluatedTasks: number;
  indicatorFirstId: number;
  indicatorName: string;
  pendingReviewTasks: number;
  qualifiedRate: null | number;
  qualifiedTasks: number;
  submittedTasks: number;
  totalTasks: number;
};

type BasicSummary = {
  evaluatedTasks: number;
  pendingReviewTasks: number;
  qualifiedRate: null | number;
  qualifiedTasks: number;
  submittedTasks: number;
  totalTasks: number;
};

type SkillSummary = {
  indicatorName: string;
  qualifiedRate: null | number;
};

type TrendRecord = {
  recordDate: string;
  totalScore?: number;
};

type SecondIndicator = {
  indicatorName: string;
  indicatorSecondId: number;
  qualifiedRate: null | number;
  qualifiedTasks: number;
  totalTasks: number;
};

type HomeworkItem = {
  homeworkId: number;
  homeworkName: string;
  indicatorSecondId?: number;
  qualified?: boolean | null;
  recordStatus?: null | number;
  score?: null | number;
};

const { isDark } = usePreferences();

const loading = ref(false);
const analysisIndicators = ref<IndicatorSummary[]>([]);
const basic = ref<BasicSummary>();
const skillSummaries = ref<SkillSummary[]>([]);
const strengths = ref<string[]>([]);
const weaknesses = ref<string[]>([]);
const trendRecords = ref<TrendRecord[]>([]);
const detailSecondIndicators = ref<SecondIndicator[]>([]);
const detailHomeworks = ref<HomeworkItem[]>([]);
const activeIndicatorId = ref<number>();
const activeIndicatorName = ref('');

const radarChartRef = ref<HTMLElement>();
const trendChartRef = ref<HTMLElement>();
const detailChartRef = ref<HTMLElement>();

const radarInst = shallowRef<echarts.ECharts>();
const trendInst = shallowRef<echarts.ECharts>();
const detailInst = shallowRef<echarts.ECharts>();

const submissionRate = computed(() => {
  const total = basic.value?.totalTasks ?? 0;
  const submitted = basic.value?.submittedTasks ?? 0;
  return total > 0 ? roundTo((submitted / total) * 100) : 0;
});

const qualifiedRate = computed<null | number>(() => {
  if (typeof basic.value?.qualifiedRate === 'number') {
    return roundTo(basic.value.qualifiedRate);
  }
  return null;
});

const averageDimensionRate = computed(() => {
  const rates = analysisIndicators.value
    .map((item) => item.qualifiedRate)
    .filter((rate): rate is number => rate !== null);
  return rates.length > 0 ? roundTo(average(rates)) : null;
});
const activeIndicator = computed(() => {
  return analysisIndicators.value.find(
    (item) => item.indicatorFirstId === activeIndicatorId.value,
  );
});
const evaluatedIndicators = computed(() =>
  analysisIndicators.value.filter((item) => item.qualifiedRate !== null),
);
const evaluatedSecondIndicators = computed(() =>
  detailSecondIndicators.value.filter((item) => item.qualifiedRate !== null),
);
/** 相关任务里显示关联的二级指标名称 */
const secondIndicatorNames = computed(
  () =>
    new Map(
      detailSecondIndicators.value.map((item) => [
        item.indicatorSecondId,
        item.indicatorName,
      ]),
    ),
);

const overviewCards = computed<AnalysisMetric[]>(() => [
  {
    label: '总任务数',
    value: `${basic.value?.totalTasks ?? 0}`,
    meta: '全部实训任务，含未关联能力指标的任务',
  },
  {
    label: '提交完成率',
    value: formatPercent(submissionRate.value),
    meta: `已提交 ${basic.value?.submittedTasks ?? 0} 项，待批改 ${basic.value?.pendingReviewTasks ?? 0} 项`,
  },
  {
    label: '达标率',
    value:
      qualifiedRate.value === null
        ? '待评定'
        : formatPercent(qualifiedRate.value),
    // 总体达标情况：有评定结果时在标签右侧显示
    status:
      qualifiedRate.value === null
        ? undefined
        : getQualifiedLevelMeta(qualifiedRate.value),
    meta: `已评定 ${basic.value?.evaluatedTasks ?? 0} 项，已达标 ${basic.value?.qualifiedTasks ?? 0} 项`,
  },
  {
    label: '累计总分',
    value: `${trendRecords.value.at(-1)?.totalScore ?? 0}`,
    meta: '按已批改任务成绩汇总',
  },
]);

const insightItems = computed(() => {
  const items: AnalysisInsight[] = [
    {
      title: '整体完成情况',
      tone: getInsightToneAtLeast(submissionRate.value, 85, 65),
      detail:
        submissionRate.value >= 85
          ? `当前任务提交完成率达到 ${formatPercent(submissionRate.value)}，学习推进节奏稳定。`
          : `当前任务提交完成率为 ${formatPercent(submissionRate.value)}，建议优先补齐未提交任务。`,
    },
  ];

  if (qualifiedRate.value === null) {
    items.push({
      title: '能力达成情况',
      tone: 'neutral',
      detail: '当前暂无已批改数据，能力结论待评定。',
    });
  } else {
    items.push({
      title: '能力达成情况',
      tone: getInsightToneAtLeast(qualifiedRate.value, 75, 60),
      detail:
        qualifiedRate.value >= 75
          ? `当前总体达标率为 ${formatPercent(qualifiedRate.value)}，能力表现较稳定。`
          : `当前总体达标率为 ${formatPercent(qualifiedRate.value)}，仍需围绕薄弱指标持续练习。`,
    });
  }

  if (strengths.value.length > 0) {
    items.push({
      title: '优势能力',
      tone: 'positive',
      detail: `当前表现较稳的一级指标包括：${strengths.value.join('、')}。`,
    });
  }

  if (weaknesses.value.length > 0) {
    items.push({
      title: '优先补强',
      tone: 'warning',
      detail: `当前建议优先补强的一级指标包括：${weaknesses.value.join('、')}。`,
    });
  }

  if (
    activeIndicator.value?.qualifiedRate !== null &&
    activeIndicator.value?.qualifiedRate !== undefined
  ) {
    items.push({
      title: '当前查看指标',
      tone: activeIndicator.value.qualifiedRate >= 70 ? 'neutral' : 'warning',
      detail: `「${activeIndicator.value.indicatorName}」当前达标率 ${formatPercent(activeIndicator.value.qualifiedRate)}，相关任务 ${activeIndicator.value.totalTasks} 项。`,
    });
  }

  return items;
});

const taskColumns: TableColumnsType = [
  {
    dataIndex: 'homeworkName',
    ellipsis: true,
    key: 'homeworkName',
    title: '任务',
  },
  { key: 'status', title: '状态', width: 148 },
  { align: 'right', key: 'score', title: '得分', width: 72 },
  { ellipsis: true, key: 'second', title: '关联二级指标', width: 160 },
];

/** 能力概况的进度条：80 以上（优势）用成功色，低于 60（需要补强）用警告色，其余用图表主色 */
const RATE_BAR_COLORS: Record<StatusTone, string> = {
  done: 'var(--nus-status-done)',
  failed: 'var(--nus-status-failed)',
  neutral: 'var(--nus-chart-1)',
  running: 'var(--nus-chart-1)',
  waiting: 'var(--nus-status-waiting)',
};

function getRateBarColor(rate: number) {
  return RATE_BAR_COLORS[getQualifiedRateTone(rate)];
}

function normalizeAnalysis(
  payload: StudentCapacityApi.CapacityAnalysis,
): IndicatorSummary[] {
  return payload.indicators.map((item) => ({
    indicatorFirstId: item.indicatorFirstId,
    indicatorName: item.indicatorName,
    totalTasks: item.totalTasks,
    submittedTasks: item.submittedTasks,
    pendingReviewTasks: item.pendingReviewTasks,
    evaluatedTasks: item.evaluatedTasks,
    qualifiedTasks: item.qualifiedTasks,
    qualifiedRate: item.qualifiedRate,
  }));
}

function normalizeBasic(payload: StudentCapacityApi.ReportBasic): BasicSummary {
  return {
    totalTasks: payload.totalTasks,
    submittedTasks: payload.submittedTasks,
    pendingReviewTasks: payload.pendingReviewTasks,
    evaluatedTasks: payload.evaluatedTasks,
    qualifiedTasks: payload.qualifiedTasks,
    qualifiedRate: payload.qualifiedRate,
  };
}

function normalizeSkills(payload: StudentCapacityApi.ReportSkills) {
  return {
    skills: payload.skills.map((item) => ({
      indicatorName: item.indicatorName,
      qualifiedRate: item.qualifiedRate,
    })),
    strengths: payload.strengths,
    weaknesses: payload.weaknesses,
  };
}

function normalizeTrend(
  payload: StudentCapacityApi.ReportTrend,
): TrendRecord[] {
  return payload.trends.map((item) => ({
    recordDate: item.recordDate,
    totalScore: item.totalScore ?? undefined,
  }));
}

function normalizeDetail(payload: StudentCapacityApi.CapacityDetail) {
  return {
    indicatorName: payload.indicatorFirstName,
    secondIndicators: payload.secondIndicators.map((item) => ({
      indicatorSecondId: item.indicatorSecondId,
      indicatorName: item.indicatorName,
      totalTasks: item.totalTasks,
      qualifiedTasks: item.qualifiedTasks,
      qualifiedRate: item.qualifiedRate,
    })),
    homeworks: payload.homeworks.map((item) => ({
      homeworkId: item.homeworkId,
      homeworkName: item.homeworkName,
      indicatorSecondId: item.indicatorSecondId,
      score: item.score,
      qualified: item.qualified,
      recordStatus: item.recordStatus,
    })),
  };
}

/** 能力分布图：3 个及以上指标画雷达图，1～2 个画横向条形图 */
function renderRadarChart() {
  if (!radarChartRef.value || evaluatedIndicators.value.length === 0) return;
  radarInst.value?.dispose();
  const chart = echarts.init(
    radarChartRef.value,
    isDark.value ? 'dark' : undefined,
  );
  radarInst.value = chart;
  const theme = getAnalysisChartTheme();
  applyCapacityChartOption(
    chart,
    evaluatedIndicators.value.map((item) => ({
      name: item.indicatorName,
      value: item.qualifiedRate!,
    })),
    {
      base: theme.base,
      color: theme.series[0],
      palette: theme.palette,
      radar: { center: ['50%', '54%'], radius: '70%' },
      tooltip: theme.tooltip,
    },
  );
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
  chart.setOption({
    ...theme.base,
    color: [theme.series[0]],
    tooltip: { ...theme.tooltip, trigger: 'axis' },
    legend: { ...theme.legend, data: ['综合得分'], right: 0, top: 0 },
    grid: { bottom: 0, containLabel: true, left: 0, right: 8, top: 40 },
    xAxis: {
      type: 'category',
      data: trendRecords.value.map((item) => item.recordDate),
      axisLabel: theme.axisLabel,
      axisLine: theme.axisLine,
      axisTick: theme.axisTick,
    },
    yAxis: {
      type: 'value',
      axisLabel: theme.axisLabel,
      axisLine: { show: false },
      splitLine: theme.splitLine,
    },
    series: [
      {
        name: '综合得分',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trendRecords.value.map((item) => item.totalScore ?? null),
      },
    ],
  });
}

function renderDetailChart() {
  if (!detailChartRef.value || evaluatedSecondIndicators.value.length === 0)
    return;
  detailInst.value?.dispose();
  const chart = echarts.init(
    detailChartRef.value,
    isDark.value ? 'dark' : undefined,
  );
  detailInst.value = chart;
  const theme = getAnalysisChartTheme();
  chart.setOption({
    ...theme.base,
    color: [theme.series[0]],
    tooltip: { ...theme.tooltip, trigger: 'axis' },
    grid: { bottom: 0, containLabel: true, left: 0, right: 48, top: 8 },
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
      data: evaluatedSecondIndicators.value.map((item) => item.indicatorName),
      axisLabel: theme.axisLabel,
      axisLine: theme.axisLine,
      axisTick: theme.axisTick,
    },
    series: [
      {
        name: '达标率',
        type: 'bar',
        barWidth: 16,
        itemStyle: { borderRadius: BAR_RADIUS_HORIZONTAL },
        label: {
          show: true,
          position: 'right',
          color: theme.palette.text,
          formatter: ({ value }: { value: number }) => `${value}%`,
        },
        data: evaluatedSecondIndicators.value.map((item) =>
          roundTo(item.qualifiedRate!),
        ),
      },
    ],
  });
}

function renderAllCharts() {
  renderRadarChart();
  renderTrendChart();
  renderDetailChart();
}

function handleResize() {
  radarInst.value?.resize();
  trendInst.value?.resize();
  detailInst.value?.resize();
}

async function loadActiveIndicatorDetail(indicatorFirstId?: number) {
  if (!indicatorFirstId) {
    activeIndicatorName.value = '';
    detailSecondIndicators.value = [];
    detailHomeworks.value = [];
    await nextTick();
    renderDetailChart();
    return;
  }
  const detailPayload = await getCapacityDetail(indicatorFirstId);
  const normalized = normalizeDetail(detailPayload);
  activeIndicatorName.value = normalized.indicatorName;
  detailSecondIndicators.value = normalized.secondIndicators;
  detailHomeworks.value = normalized.homeworks;
  await nextTick();
  renderDetailChart();
}

async function switchIndicator(indicatorFirstId: number) {
  activeIndicatorId.value = indicatorFirstId;
  await loadActiveIndicatorDetail(indicatorFirstId);
}

async function init() {
  loading.value = true;
  try {
    const [analysisPayload, basicPayload, skillsPayload, trendPayload] =
      await Promise.all([
        getCapacityAnalysis(),
        getReportBasic(),
        getReportSkills(),
        getReportTrend(),
      ]);

    analysisIndicators.value = normalizeAnalysis(analysisPayload);
    basic.value = normalizeBasic(basicPayload);
    const normalizedSkills = normalizeSkills(skillsPayload);
    skillSummaries.value = normalizedSkills.skills;
    strengths.value = normalizedSkills.strengths;
    weaknesses.value = normalizedSkills.weaknesses;
    trendRecords.value = normalizeTrend(trendPayload);
    activeIndicatorId.value = analysisIndicators.value[0]?.indicatorFirstId;
    await loadActiveIndicatorDetail(activeIndicatorId.value);
    await nextTick();
    renderAllCharts();
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
  radarInst.value?.dispose();
  trendInst.value?.dispose();
  detailInst.value?.dispose();
});
</script>

<template>
  <Page
    auto-content-height
    description="结合已提交任务与评定结果，查看自己在一级、二级指标上的达成情况。"
    title="单项能力评测"
  >
    <Spin :spinning="loading" tip="正在读取能力评测数据…">
      <div class="analysis-page">
        <AnalysisMetricGrid :items="overviewCards" />

        <div class="analysis-row analysis-row--even">
          <AnalysisPanel
            description="展示各一级指标当前的任务达标率，帮助你识别整体能力轮廓。"
            title="一级指标画像"
          >
            <div
              v-if="evaluatedIndicators.length > 0"
              ref="radarChartRef"
              class="analysis-chart"
            ></div>
            <EmptyState
              v-else-if="analysisIndicators.length > 0"
              compact
              description="提交的任务批改后，会按一级指标计算达标率。"
              title="还没有已评定的指标"
            />
            <EmptyState
              v-else
              compact
              description="任务关联能力指标并完成批改后，这里会显示各一级指标的达标率。"
              title="还没有能力指标数据"
            />
          </AnalysisPanel>

          <AnalysisPanel
            description="按达标率从高到低展示当前最稳和最弱的一级指标。"
            title="能力概况"
          >
            <template v-if="averageDimensionRate !== null" #extra>
              <span class="analysis-muted">
                平均维度达标率
                <span class="skill-average analysis-number">
                  {{ formatPercent(averageDimensionRate) }}
                </span>
              </span>
            </template>
            <template v-if="skillSummaries.length > 0">
              <ul class="skill-rates">
                <li
                  v-for="item in skillSummaries"
                  :key="item.indicatorName"
                  class="skill-rate"
                >
                  <div class="skill-rate__head">
                    <span class="skill-rate__name">
                      {{ item.indicatorName }}
                    </span>
                    <span class="skill-rate__value analysis-number">
                      {{
                        item.qualifiedRate === null
                          ? '待评定'
                          : formatPercent(item.qualifiedRate)
                      }}
                    </span>
                  </div>
                  <Progress
                    v-if="item.qualifiedRate !== null"
                    :percent="Math.round(item.qualifiedRate)"
                    :show-info="false"
                    :stroke-color="getRateBarColor(item.qualifiedRate)"
                    size="small"
                  />
                </li>
              </ul>
              <div class="skill-groups">
                <div>
                  <h3 class="analysis-subtitle">优势能力</h3>
                  <div class="skill-groups__tags">
                    <Tag v-for="item in strengths" :key="item">{{ item }}</Tag>
                    <span v-if="strengths.length === 0" class="analysis-muted">
                      暂无明显优势指标
                    </span>
                  </div>
                </div>
                <div>
                  <h3 class="analysis-subtitle">优先补强</h3>
                  <div class="skill-groups__tags">
                    <Tag v-for="item in weaknesses" :key="item">{{ item }}</Tag>
                    <span v-if="weaknesses.length === 0" class="analysis-muted">
                      当前没有明显薄弱指标
                    </span>
                  </div>
                </div>
              </div>
            </template>
            <EmptyState
              v-else
              compact
              description="任务批改后，这里会按达标率列出各一级指标。"
              title="还没有能力概况"
            />
          </AnalysisPanel>
        </div>

        <div class="analysis-row">
          <AnalysisPanel
            description="按记录日期查看综合得分的阶段性变化。"
            title="成长趋势"
          >
            <div
              v-if="trendRecords.length > 0"
              ref="trendChartRef"
              class="analysis-chart"
            ></div>
            <EmptyState
              v-else
              compact
              description="任务批改后会记录综合得分，这里显示它的变化。"
              title="还没有成长记录"
            />
          </AnalysisPanel>

          <AnalysisPanel
            description="以下结论根据你的任务完成与能力达成情况自动提炼。"
            title="学习提示"
          >
            <AnalysisInsightList :items="insightItems" />
          </AnalysisPanel>
        </div>

        <AnalysisPanel
          :title="activeIndicatorName || '一级指标详情'"
          description="切换一级指标后，可查看对应二级指标的达标情况和任务清单。"
        >
          <template v-if="analysisIndicators.length > 0">
            <div
              aria-label="切换一级指标"
              class="indicator-switch"
              role="group"
            >
              <button
                v-for="item in analysisIndicators"
                :key="item.indicatorFirstId"
                :aria-pressed="item.indicatorFirstId === activeIndicatorId"
                :class="{
                  'is-active': item.indicatorFirstId === activeIndicatorId,
                }"
                class="indicator-chip"
                type="button"
                @click="switchIndicator(item.indicatorFirstId)"
              >
                <span>{{ item.indicatorName }}</span>
                <span class="indicator-chip__rate analysis-number">
                  {{
                    item.qualifiedRate === null
                      ? '待评定'
                      : formatPercent(item.qualifiedRate)
                  }}
                </span>
              </button>
            </div>

            <div class="skill-detail">
              <section class="skill-detail__section">
                <h3 class="analysis-subtitle">二级指标达标率</h3>
                <div
                  v-if="evaluatedSecondIndicators.length > 0"
                  ref="detailChartRef"
                  class="analysis-chart analysis-chart--short"
                ></div>
                <EmptyState
                  v-else
                  compact
                  description="这个一级指标下的任务批改后，会显示各二级指标的达标率。"
                  title="还没有已评定的二级指标"
                />
              </section>

              <section class="skill-detail__section">
                <h3 class="analysis-subtitle">相关任务</h3>
                <Table
                  v-if="detailHomeworks.length > 0"
                  :columns="taskColumns"
                  :data-source="detailHomeworks"
                  :pagination="false"
                  row-key="homeworkId"
                  size="small"
                >
                  <template #bodyCell="{ column, record }">
                    <template v-if="column.key === 'status'">
                      <StatusTag
                        v-bind="
                          getSkillTaskStatusMeta({
                            qualified: record.qualified,
                            recordStatus: record.recordStatus,
                          })
                        "
                      />
                    </template>
                    <template v-else-if="column.key === 'score'">
                      <span class="analysis-number">
                        {{ record.score ?? '-' }}
                      </span>
                    </template>
                    <template v-else-if="column.key === 'second'">
                      {{
                        secondIndicatorNames.get(record.indicatorSecondId) ||
                        '-'
                      }}
                    </template>
                  </template>
                </Table>
                <EmptyState
                  v-else
                  compact
                  description="任务关联到这个一级指标后，会列在这里。"
                  title="这个指标下还没有相关任务"
                />
              </section>
            </div>
          </template>
          <EmptyState
            v-else
            compact
            description="任务关联能力指标后，这里会显示各一级指标下的二级指标与相关任务。"
            title="还没有能力指标"
          />
        </AnalysisPanel>
      </div>
    </Spin>
  </Page>
</template>

<style scoped src="./analysis/layout.css"></style>

<style scoped>
/* 一级指标详情：二级指标与相关任务左右两栏，中间用分隔线；1280 宽以下上下排列 */
@media (width < 1280px) {
  .skill-detail__section + .skill-detail__section {
    padding-top: var(--nus-space-4);
    margin-top: var(--nus-space-4);
    border-top: 1px solid var(--nus-border-subtle);
  }
}

@media (width >= 1280px) {
  .skill-detail {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  }

  .skill-detail__section + .skill-detail__section {
    padding-left: var(--nus-card-padding);
    border-left: 1px solid var(--nus-border-subtle);
  }
}

.skill-average {
  margin-left: var(--nus-space-1);
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text);
}

/* 能力概况：每个一级指标一行，名称与达标率在上，进度条在下 */
.skill-rates {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
  padding: 0;
  margin: 0;
  list-style: none;
}

.skill-rate__head {
  display: flex;
  gap: var(--nus-space-3);
  align-items: baseline;
  justify-content: space-between;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
}

.skill-rate__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text);
  white-space: nowrap;
}

.skill-rate__value {
  flex: none;
  color: var(--nus-text-secondary);
}

.skill-rate :deep(.ant-progress) {
  margin: var(--nus-space-1) 0 0;
}

.skill-groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--nus-space-4);
  padding-top: var(--nus-space-4);
  margin-top: var(--nus-space-4);
  border-top: 1px solid var(--nus-border-subtle);
}

.skill-groups__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
}

.skill-groups__tags :deep(.ant-tag) {
  margin: 0;
}

/* 切换一级指标：选中项用选中底与主色文字 */
.indicator-switch {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  margin-bottom: var(--nus-space-5);
}

.indicator-chip {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: center;
  height: 28px;
  padding: 0 var(--nus-space-3);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--nus-border);
  border-radius: var(--nus-radius);
  transition: var(--nus-transition-colors);
}

.indicator-chip:hover {
  color: var(--nus-text);
  background: var(--nus-bg-hover);
}

.indicator-chip:focus-visible {
  outline: 2px solid var(--nus-primary);
  outline-offset: 1px;
}

.indicator-chip.is-active {
  color: var(--nus-primary);
  background: var(--nus-bg-selected);
  border-color: var(--nus-bg-selected);
}

.indicator-chip__rate {
  color: var(--nus-text-tertiary);
}

.indicator-chip.is-active .indicator-chip__rate {
  color: inherit;
}

.skill-detail {
  display: grid;
}

.skill-detail__section {
  min-width: 0;
}
</style>
