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

import { Alert, Button, Card, Progress, Spin } from 'ant-design-vue';
import { BarChart, RadarChart } from 'echarts/charts';
import {
  GridComponent,
  RadarComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

import { getCapacityAnalysis } from '#/api/edu/student/capacity';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import {
  getPendingEvaluationMeta,
  getRateHighlight,
  getSkillHighlightMeta,
} from '#/utils/status-capacity';
import {
  applyCapacityChartOption,
  getChartPalette,
} from '#/views/edu/statistics/chart-kit';

import {
  getCapacityQualifiedMeta,
  getOverallRateView,
  getUnindicatedCount,
  getUnindicatedNote,
  toCapacityChartItems,
} from './capacity-summary';

echarts.use([
  BarChart,
  RadarChart,
  GridComponent,
  TitleComponent,
  TooltipComponent,
  RadarComponent,
  CanvasRenderer,
]);

const router = useRouter();
const { isDark } = usePreferences();
const loading = ref(false);
const analysis = ref<StudentCapacityApi.CapacityAnalysis>();
const radarChartRef = ref<HTMLElement>();
const chartInstance = shallowRef<echarts.ECharts>();

const pendingMeta = getPendingEvaluationMeta();
const qualifiedMeta = computed(() => getCapacityQualifiedMeta(analysis.value));
const overallRateView = computed(() => getOverallRateView(analysis.value));
/** 已评定但未关联能力指标的任务：综合达标率为空时写在卡片里，否则在页面顶部说明一次 */
const unindicatedCount = computed(() => getUnindicatedCount(analysis.value));
const unindicatedAlert = computed(() =>
  overallRateView.value.meta ? '' : getUnindicatedNote(unindicatedCount.value),
);
/** 能力分布图：只画已有达标率的指标 */
const chartItems = computed(() =>
  toCapacityChartItems(analysis.value?.indicators ?? []),
);

/** 各指标一行：达标率 80% 及以上标优势、60% 以下标薄弱 */
const indicatorRows = computed(() =>
  (analysis.value?.indicators ?? []).map((item) => ({
    ...item,
    highlight: getSkillHighlightMeta(getRateHighlight(item.qualifiedRate)),
  })),
);

/** 能力分布图：3 个及以上指标画雷达图，1～2 个画横向条形图 */
function renderRadarChart() {
  if (!radarChartRef.value || chartItems.value.length === 0) return;
  chartInstance.value?.dispose();
  const chart = echarts.init(radarChartRef.value);
  chartInstance.value = chart;
  // 颜色取设计 token（深浅色切换后重画）
  applyCapacityChartOption(chart, chartItems.value, {
    digits: 0,
    palette: getChartPalette(),
  });
}

async function loadData() {
  loading.value = true;
  try {
    analysis.value = await getCapacityAnalysis();
    await nextTick();
    renderRadarChart();
  } finally {
    loading.value = false;
  }
}

function handleResize() {
  chartInstance.value?.resize();
}

function goReport() {
  router.push('/student/capacity/report');
}

watch(isDark, async () => {
  await nextTick();
  renderRadarChart();
});

onMounted(() => {
  loadData();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  chartInstance.value?.dispose();
});
</script>

<template>
  <Page
    auto-content-height
    description="按已评定的实训任务统计各项能力指标的达标情况。"
    title="能力分析"
  >
    <template #extra>
      <Button @click="goReport">查看实训报告</Button>
    </template>

    <Spin :spinning="loading" tip="正在读取能力分析…">
      <div v-if="analysis" class="page-stack">
        <Alert
          v-if="unindicatedAlert"
          :message="unindicatedAlert"
          show-icon
          type="info"
        />

        <!-- 总体概览 -->
        <div class="metric-grid lg:grid-cols-3">
          <div class="metric-card">
            <div class="metric-card__label">综合达标率</div>
            <div class="metric-card__value">{{ overallRateView.value }}</div>
            <div v-if="overallRateView.meta" class="metric-card__meta">
              {{ overallRateView.meta }}
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-card__label">达标状态</div>
            <div class="capacity-status">
              <StatusTag
                :label="qualifiedMeta.label"
                :tone="qualifiedMeta.tone"
                emphasis
              />
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-card__label">评估维度</div>
            <div class="metric-card__value">
              {{ analysis.indicators.length }}
              <span class="capacity-unit">项</span>
            </div>
          </div>
        </div>

        <!-- 雷达图 + 各指标达标率 -->
        <div class="grid gap-4 lg:grid-cols-2">
          <Card title="能力雷达图">
            <div
              v-if="chartItems.length > 0"
              ref="radarChartRef"
              class="capacity-chart"
            ></div>
            <EmptyState
              v-else-if="unindicatedCount > 0"
              compact
              description="这些任务计入任务统计，不计入能力画像。关联了能力指标的任务批改后，这里会显示能力分布。"
              title="已评定的任务都未关联能力指标"
            />
            <EmptyState
              v-else
              compact
              description="教师批改你的实训任务后，这里会按指标显示能力分布。"
              title="还没有已评定的任务"
            />
          </Card>
          <Card title="各指标达标率">
            <ul v-if="indicatorRows.length > 0" class="capacity-rows">
              <li
                v-for="item in indicatorRows"
                :key="item.indicatorName"
                class="capacity-row"
              >
                <span :title="item.indicatorName" class="capacity-row__name">
                  {{ item.indicatorName }}
                </span>
                <div class="capacity-row__value">
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
                <span class="capacity-row__tag">
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
      </div>
      <EmptyState
        v-else-if="!loading"
        description="完成并提交实训任务、经教师批改后，这里会显示你的能力达标情况。"
        title="还没有能力分析数据"
      >
        <template #action>
          <Button @click="router.push('/student/homework')">查看任务</Button>
        </template>
      </EmptyState>
    </Spin>
  </Page>
</template>

<style scoped>
.capacity-status {
  display: flex;
  align-items: center;
  height: var(--nus-lh-display);
  margin-top: 8px;
}

.capacity-unit {
  margin-left: 2px;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-regular);
  color: var(--nus-text-secondary);
}

.capacity-chart {
  width: 100%;
  height: 360px;
}

.capacity-rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.capacity-row {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr) 56px;
  gap: 16px;
  align-items: center;
  min-height: 32px;
}

.capacity-row__name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  white-space: nowrap;
}

.capacity-row__value {
  min-width: 0;
}

.capacity-row__value :deep(.ant-progress) {
  margin: 0;
}

.capacity-row__tag {
  display: flex;
  justify-content: flex-end;
}
</style>
