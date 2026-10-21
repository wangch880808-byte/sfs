<script lang="ts" setup>
import type { StatisticsApi } from '#/api/edu/statistics';

import { computed } from 'vue';

import { DashboardPanel } from '#/views/dashboard/components';
import { buildGradeBands } from '#/views/dashboard/shared/admin';
import { ADMIN_LINKS, useAvailableLink } from '#/views/dashboard/shared/links';

/**
 * 管理员看板：提交与批改（本周提交、待批改、AI 批改）与全校成绩分布。
 * 成绩分布用图表分类色画一条占比条，下面的图例写出每档的占比；没有权限读成绩时不显示这一段。
 */
const props = withDefaults(
  defineProps<{
    error?: boolean;
    loading?: boolean;
    /** 全校成绩分布；undefined 表示没读（没有权限）或还没读到 */
    passRate?: Partial<StatisticsApi.PassRateRespVO>;
    passRateError?: boolean;
    passRateLoading?: boolean;
    /** 没有权限读成绩分布时不显示这一段 */
    showGrades?: boolean;
    summary?: StatisticsApi.AdminDashboardRespVO;
  }>(),
  {
    error: false,
    loading: false,
    passRate: undefined,
    passRateError: false,
    passRateLoading: false,
    showGrades: true,
    summary: undefined,
  },
);

defineEmits<{ retry: []; retryGrades: [] }>();

const linkOf = useAvailableLink();

const stats = computed(() => [
  {
    key: 'week',
    label: '本周提交',
    meta: '从本周一算起',
    value: props.summary?.weekSubmissions,
  },
  {
    key: 'pending',
    label: '待批改',
    meta: '学生已提交、教师还没批改',
    value: props.summary?.pendingCorrections,
  },
  {
    key: 'ai',
    label: 'AI 批改',
    meta: '经过 AI 初评的批改，累计',
    value: props.summary?.aiCorrections,
  },
]);

const gradedTotal = computed(() => Number(props.passRate?.totalCount ?? 0));
const bands = computed(() => buildGradeBands(props.passRate));
const visibleBands = computed(() =>
  bands.value.filter((band) => band.rate > 0),
);
const bandsLabel = computed(() =>
  bands.value.map((band) => `${band.label} ${band.rate}%`).join('，'),
);
</script>

<template>
  <DashboardPanel
    :error="error"
    :loading="loading"
    error-title="提交与批改的统计没有读到"
    loading-text="正在读取全校的提交与批改…"
    title="提交与批改"
    @retry="$emit('retry')"
  >
    <div class="activity-stats">
      <div v-for="stat in stats" :key="stat.key" class="activity-stat">
        <span class="activity-stat__label">{{ stat.label }}</span>
        <span class="activity-stat__value">
          {{ stat.value ?? '—'
          }}<small v-if="stat.value !== undefined">份</small>
        </span>
        <span class="activity-stat__meta">{{ stat.meta }}</span>
      </div>
    </div>

    <div v-if="showGrades" class="grade-section">
      <div class="grade-section__head">
        <h3 class="grade-section__title">成绩分布</h3>
        <span v-if="gradedTotal > 0" class="grade-section__caption">
          已批改 {{ gradedTotal }} 份
        </span>
        <RouterLink
          v-if="linkOf(ADMIN_LINKS.teachingProcess)"
          :to="ADMIN_LINKS.teachingProcess"
          class="grade-section__link"
        >
          教学过程分析
        </RouterLink>
      </div>
      <p v-if="passRateLoading && !passRate" class="grade-section__note">
        正在读取成绩分布…
      </p>
      <p v-else-if="passRateError && !passRate" class="grade-section__note">
        成绩分布没有读到，可能是网络或服务暂时不可用。
        <button
          class="grade-section__retry"
          type="button"
          @click="$emit('retryGrades')"
        >
          重试
        </button>
      </p>
      <p v-else-if="gradedTotal === 0" class="grade-section__note">
        还没有已批改的提交，批改后会按优秀、良好、合格、不合格显示占比。
      </p>
      <template v-else>
        <div :aria-label="bandsLabel" class="grade-bar" role="img">
          <span
            v-for="band in visibleBands"
            :key="band.key"
            :style="{ width: `${band.rate}%`, backgroundColor: band.color }"
            class="grade-bar__segment"
          ></span>
        </div>
        <ul class="grade-legend">
          <li v-for="band in bands" :key="band.key" class="grade-legend__item">
            <span
              :style="{ backgroundColor: band.color }"
              aria-hidden="true"
              class="grade-legend__dot"
            ></span>
            {{ band.label }}
            <span class="grade-legend__value">{{ band.rate }}%</span>
          </li>
        </ul>
      </template>
    </div>
  </DashboardPanel>
</template>

<style scoped>
.activity-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid var(--nus-border-subtle);
}

.activity-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: var(--nus-space-4) var(--nus-card-padding);
}

.activity-stat + .activity-stat {
  border-left: 1px solid var(--nus-border-subtle);
}

.activity-stat__label {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.activity-stat__value {
  font-size: var(--nus-fs-title);
  font-weight: var(--nus-fw-semibold);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-title);
  color: var(--nus-text);
}

.activity-stat__value small {
  margin-left: var(--nus-space-1);
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-regular);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.activity-stat__meta {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
  white-space: nowrap;
}

.grade-section {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
  padding: var(--nus-space-4) var(--nus-card-padding) var(--nus-space-5);
  border-top: 1px solid var(--nus-border-subtle);
}

.grade-section__head {
  display: flex;
  gap: var(--nus-space-2);
  align-items: baseline;
}

.grade-section__title {
  margin: 0;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.grade-section__caption {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.grade-section__link {
  margin-left: auto;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  transition: var(--nus-transition-colors);
}

.grade-section__link:hover {
  color: var(--nus-primary);
}

.grade-section__note {
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.grade-section__retry {
  padding: 0;
  color: var(--nus-primary);
  cursor: pointer;
  background: none;
  border: 0;
  transition: var(--nus-transition-colors);
}

.grade-section__retry:hover {
  color: var(--nus-primary-hover);
}

.grade-bar {
  display: flex;
  gap: 2px;
  height: 8px;
  overflow: hidden;
  background-color: var(--nus-border);
  border-radius: var(--nus-radius-sm);
}

.grade-bar__segment {
  min-width: 4px;
  height: 100%;
}

.grade-legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2) var(--nus-space-6);
}

.grade-legend__item {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.grade-legend__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.grade-legend__value {
  font-weight: var(--nus-fw-medium);
  font-variant-numeric: tabular-nums;
  color: var(--nus-text);
}
</style>
