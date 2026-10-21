<script lang="ts" setup>
import type { StudentCourseApi } from '#/api/edu/student/course';

import { onActivated, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, Progress, Spin } from 'ant-design-vue';

import { getStudentCourseList } from '#/api/edu/student/course';
import { EmptyState } from '#/components/empty-state';

interface StudentCourseCard extends StudentCourseApi.CourseItem {
  indicatorId: number;
  teacherNameResolved: string;
}

interface IndicatorLearningCard {
  indicatorId: number;
  indicatorName: string;
  /** 补充说明（仅“未关联一级指标”分组提示完善配置） */
  description: string;
  courses: StudentCourseCard[];
  totalHomework: number;
  completedHomework: number;
  progress: number;
}

const router = useRouter();
const loading = ref(false);
const indicatorCards = ref<IndicatorLearningCard[]>([]);
let mounted = false;

function toNumber(value: unknown) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function buildProgress(totalHomework: number, completedHomework: number) {
  if (totalHomework <= 0) return 0;
  return Math.min(
    100,
    Math.max(0, Math.round((completedHomework / totalHomework) * 100)),
  );
}

/** 未关联一级指标的课程统一归入该分组 */
const UNGROUPED_INDICATOR_ID = -1;

/** 按一级指标 ID 升序排列，未关联指标的分组排在最后 */
function compareIndicatorId(a: number, b: number) {
  if (a === UNGROUPED_INDICATOR_ID || b === UNGROUPED_INDICATOR_ID) {
    return b - a;
  }
  return a - b;
}

function buildCard(
  indicatorId: number,
  courses: StudentCourseCard[],
): IndicatorLearningCard {
  const totalHomework = courses.reduce(
    (sum, item) => sum + toNumber(item.totalHomework),
    0,
  );
  const completedHomework = courses.reduce(
    (sum, item) => sum + toNumber(item.completedHomework),
    0,
  );
  const ungrouped = indicatorId === UNGROUPED_INDICATOR_ID;
  return {
    indicatorId,
    indicatorName: ungrouped
      ? '未关联一级指标'
      : courses[0]?.indicatorName || `一级指标 ${indicatorId}`,
    description: ungrouped ? '请联系教师完善课程指标配置' : '',
    courses,
    totalHomework,
    completedHomework,
    progress: buildProgress(totalHomework, completedHomework),
  };
}

async function loadData() {
  loading.value = true;
  try {
    // 学生课程接口已带出课程所属一级指标，直接按其分组，不再调用管理端指标接口
    const courseList = await getStudentCourseList();
    const grouped = new Map<number, StudentCourseCard[]>();
    for (const course of courseList ?? []) {
      const teacherId = toNumber(course.teacherId);
      const totalHomework = toNumber(course.totalHomework);
      const completedHomework = toNumber(course.completedHomework);
      const indicatorId = toNumber(course.indicatorId);
      const groupId = indicatorId > 0 ? indicatorId : UNGROUPED_INDICATOR_ID;
      if (!grouped.has(groupId)) grouped.set(groupId, []);
      grouped.get(groupId)!.push({
        ...course,
        courseId: toNumber(course.courseId),
        teacherId: teacherId || undefined,
        teacherNameResolved: course.teacherName || '-',
        indicatorId,
        totalHomework,
        completedHomework,
        progress: buildProgress(totalHomework, completedHomework),
      });
    }
    indicatorCards.value = [...grouped.entries()]
      .toSorted(([a], [b]) => compareIndicatorId(a, b))
      .map(([indicatorId, courses]) => buildCard(indicatorId, courses));
  } finally {
    loading.value = false;
  }
}

function handleOpenIndicator(card: IndicatorLearningCard) {
  router.push({
    path: `/student/course/indicator/detail/${card.indicatorId}`,
  });
}

onMounted(() => {
  void loadData().then(() => {
    mounted = true;
  });
});

onActivated(() => {
  if (mounted) void loadData();
});
</script>

<template>
  <Page
    auto-content-height
    description="本学期的课程按一级指标分组，打开分组查看课程与任务进度。"
    title="我的课程"
  >
    <template #extra>
      <Button @click="loadData">
        <template #icon><IconifyIcon icon="lucide:refresh-cw" /></template>
        刷新
      </Button>
    </template>

    <div
      v-if="loading && indicatorCards.length === 0"
      class="flex min-h-60 flex-col items-center justify-center gap-3 text-sm text-muted-foreground"
      role="status"
    >
      <Spin />
      <span>正在读取课程…</span>
    </div>

    <Spin v-else :spinning="loading" tip="正在刷新课程…">
      <div v-if="indicatorCards.length > 0" class="indicator-grid">
        <div
          v-for="card in indicatorCards"
          :key="card.indicatorId"
          :aria-label="`查看${card.indicatorName}课程详情`"
          class="indicator-card surface-panel"
          role="button"
          tabindex="0"
          @click="handleOpenIndicator(card)"
          @keydown.enter="handleOpenIndicator(card)"
          @keydown.space.prevent="handleOpenIndicator(card)"
        >
          <div class="indicator-card__head">
            <div class="indicator-card__title" :title="card.indicatorName">
              {{ card.indicatorName }}
            </div>
            <span class="indicator-card__count">
              {{ card.courses.length }} 门课程
            </span>
          </div>
          <p v-if="card.description" class="indicator-card__desc">
            {{ card.description }}
          </p>

          <ul class="indicator-card__courses">
            <li
              v-for="course in card.courses.slice(0, 2)"
              :key="course.courseId"
              class="indicator-card__course"
            >
              <IconifyIcon
                class="indicator-card__course-icon"
                icon="lucide:book-open"
              />
              <span class="truncate" :title="course.courseName">
                {{ course.courseName }}
              </span>
            </li>
            <li
              v-if="card.courses.length === 0"
              class="indicator-card__course indicator-card__more"
            >
              暂无关联课程
            </li>
            <li
              v-else-if="card.courses.length > 2"
              class="indicator-card__more"
            >
              另有 {{ card.courses.length - 2 }} 门课程
            </li>
          </ul>

          <div class="indicator-card__progress">
            <div class="indicator-card__meta">
              <span>达标进度</span>
              <span>
                {{ card.completedHomework }}/{{ card.totalHomework }}
                已评定达标
              </span>
            </div>
            <Progress
              :percent="card.progress"
              :show-info="false"
              size="small"
            />
            <div class="indicator-card__meta">
              <span>{{ card.progress }}%</span>
              <IconifyIcon
                class="indicator-card__arrow"
                icon="lucide:arrow-right"
              />
            </div>
          </div>
        </div>
      </div>

      <div v-else class="surface-panel">
        <EmptyState
          description="教师为你所在的班级开课后，课程会显示在这里。"
          title="还没有课程"
        />
      </div>
    </Spin>
  </Page>
</template>

<style scoped>
.indicator-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.indicator-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: var(--nus-card-padding);
  cursor: pointer;
  transition: var(--nus-transition-colors);
}

.indicator-card:hover {
  background: var(--nus-bg-hover);
  border-color: var(--nus-border);
}

.indicator-card:focus-visible {
  outline: 2px solid var(--nus-primary);
  outline-offset: 2px;
}

.indicator-card__head {
  display: flex;
  gap: 12px;
  align-items: baseline;
  justify-content: space-between;
  min-width: 0;
}

.indicator-card__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
  white-space: nowrap;
}

.indicator-card__count {
  flex: none;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.indicator-card__desc {
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.indicator-card__courses {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 0 0;
  margin: 0;
  list-style: none;
  border-top: 1px solid var(--nus-border-subtle);
}

.indicator-card__course {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  height: 28px;
  font-size: var(--nus-fs-body);
  color: var(--nus-text);
}

.indicator-card__course-icon {
  flex: none;
  color: var(--nus-text-tertiary);
}

.indicator-card__more {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.indicator-card__progress {
  margin-top: auto;
}

.indicator-card__meta {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
}

.indicator-card__arrow {
  color: var(--nus-text-tertiary);
  transition: var(--nus-transition-colors);
}

.indicator-card:hover .indicator-card__arrow {
  color: var(--nus-text);
}
</style>
