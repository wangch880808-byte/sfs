<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { StudentCourseApi } from '#/api/edu/student/course';
import type { CourseResourceLike } from '#/components/course-resource/resource-helpers';
import type { StudentLoadError } from '#/views/edu/student/homework/load-error';

import { computed, nextTick, onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import {
  Alert,
  Button,
  Card,
  Progress,
  Spin,
  TabPane,
  Tabs,
} from 'ant-design-vue';

import { ACTION_ICON, TableAction } from '#/adapter/vxe-table';
import {
  downloadStudentCourseResource,
  getStudentCourseDetail,
  getUnlockStatus,
} from '#/api/edu/student/course';
import CourseResourceWorkspace from '#/components/course-resource/workspace.vue';
import DetailSheetGrid from '#/components/detail-sheet-grid.vue';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import {
  applyCourseCoverFallback,
  getCourseCoverUrl,
} from '#/utils/course-cover';
import { formatDeadlineTime } from '#/utils/homework-deadline';
import { getHomeworkRecordStatusMeta } from '#/utils/status';
import {
  getLevelUnlockMeta,
  isAwaitingSubmissionStatus,
  isSubmittedStatus,
} from '#/utils/status-student-homework';
import { messageLabels } from '#/views/edu/message/labels';
import { resolveStudentLoadError } from '#/views/edu/student/homework/load-error';
import {
  buildTaskRouteQuery,
  getTaskLevelLabel,
  getTaskTypeLabel,
} from '#/views/edu/student/homework/task-display';

import { buildLevelRuleHint, describeLevel } from './level-progress';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
/** 拿到课程后再渲染带页头的页面：页面按页头的实际高度计算内容区 */
const detail = ref<StudentCourseApi.CourseDetail>();
/** 课程没有读取成功时的原因 */
const loadError = ref<null | StudentLoadError>(null);
const unlockLevels = ref<StudentCourseApi.UnlockLevel[]>([]);
const activeSheet = ref<'levels' | 'resources' | 'tasks'>('tasks');
const taskGridRef = ref<{ recalculate?: () => void }>();
let mounted = false;

const courseId = computed(() => Number(route.params.courseId));
const resourceCount = computed(() => detail.value?.resources?.length ?? 0);
const taskCount = computed(() => detail.value?.homeworkList?.length ?? 0);
const submittedTaskCount = computed(
  () =>
    detail.value?.homeworkList?.filter((item) =>
      isSubmittedStatus(Number(item.status)),
    ).length ?? 0,
);
const unlockedLevelCount = computed(
  () => unlockLevels.value.filter((item) => item.unlocked).length,
);
/** 往期课程：课程、班级或班课已封存，只能查看 */
const archived = computed(() => !!detail.value?.archived);
const levelViews = computed(() =>
  unlockLevels.value.map((item) => describeLevel(item)),
);
const levelRuleHint = computed(() => buildLevelRuleHint(unlockLevels.value));

const taskColumns: VxeTableGridOptions<StudentCourseApi.CourseHomework>['columns'] =
  [
    {
      field: 'homeworkName',
      title: '任务名称',
      minWidth: 240,
      slots: { default: 'taskNameCell' },
    },
    {
      field: 'homeworkType',
      title: '任务类型',
      width: 110,
      formatter: ({ cellValue }) => getTaskTypeLabel(cellValue),
    },
    {
      field: 'homeworkLevel',
      title: '任务等级',
      width: 100,
      formatter: ({ cellValue }) => getTaskLevelLabel(cellValue),
    },
    {
      field: 'deadline',
      title: '截止时间',
      width: 170,
      formatter: ({ cellValue }) => formatDeadlineTime(cellValue),
    },
    {
      field: 'status',
      title: '状态',
      width: 110,
      slots: { default: 'taskStatusCell' },
    },
    {
      field: 'score',
      title: '分值',
      width: 90,
      align: 'right',
      formatter: ({ cellValue }) =>
        cellValue === undefined || cellValue === null ? '-' : `${cellValue} 分`,
    },
    {
      title: '操作',
      width: 120,
      slots: { default: 'taskActions' },
    },
  ];

/** 读取失败时的说明：课程不存在或没有分配、其他失败（带上原因） */
const errorState = computed(() => {
  if (!loadError.value) return null;
  return loadError.value.kind === 'unavailable'
    ? {
        title: '没有找到这门课程',
        description: '课程可能已经停开，或没有分配给你。可以回到我的课程查看。',
      }
    : {
        title: '课程没有读取成功',
        description: `${loadError.value.reason}可以重试，或返回我的课程。`,
      };
});

async function loadData() {
  if (!courseId.value) {
    router.back();
    return;
  }
  loading.value = true;
  loadError.value = null;
  try {
    // 读取失败由页面写明原因与下一步，不再弹全局提示
    const [d, unlock] = await Promise.all([
      getStudentCourseDetail(courseId.value, { silentError: true }),
      getUnlockStatus(courseId.value, { silentError: true }),
    ]);
    detail.value = d;
    unlockLevels.value = unlock?.levels ?? [];
  } catch (error) {
    loadError.value = resolveStudentLoadError(error);
  } finally {
    loading.value = false;
  }
}

function handleGoHomework(
  homeworkId: number,
  status: number,
  homeworkLevel?: number,
) {
  if (!homeworkId) {
    return;
  }
  // 往期课程只能查看成绩反馈，不再进入提交页
  if (!archived.value && isAwaitingSubmissionStatus(status)) {
    // 带上课程和等级：任务还没解锁时，提交页据此说明还差什么
    router.push({
      path: `/student/homework/submit/${homeworkId}`,
      query: buildTaskRouteQuery({ courseId: courseId.value, homeworkLevel }),
    });
  } else {
    router.push({ path: `/student/homework/feedback/${homeworkId}` });
  }
}

async function handleDownloadResource(resource: CourseResourceLike) {
  if (!resource.id) return;
  const source = await downloadStudentCourseResource(resource.id);
  downloadFileFromBlobPart({
    fileName: resource.resourceName || '课程资源',
    source,
  });
}

async function fetchCourseResource(resource: CourseResourceLike) {
  if (!resource.id) throw new Error('课程资源不存在');
  return downloadStudentCourseResource(resource.id) as Promise<Blob>;
}

onMounted(() => {
  void loadData().then(() => {
    mounted = true;
  });
});

onActivated(() => {
  if (mounted) void loadData();
});

watch(activeSheet, async (value) => {
  await nextTick();
  if (value === 'tasks') {
    taskGridRef.value?.recalculate?.();
  }
});
</script>

<template>
  <Page
    v-if="detail"
    :description="`授课教师：${detail.teacherName || '-'}`"
    :title="detail.courseName"
    auto-content-height
  >
    <template #extra>
      <Button @click="router.back()">返回</Button>
      <Button
        v-if="!archived"
        v-access:code="['edu:student-message:query']"
        type="primary"
        @click="router.push({ path: '/student/message', query: { courseId } })"
      >
        {{ messageLabels.contactTeacher }}
      </Button>
    </template>

    <Spin :spinning="loading" tip="正在刷新课程…">
      <div class="page-stack">
        <Alert
          v-if="archived"
          message="这门课已封存，只能查看任务、成绩和资源，不能再提交任务。"
          show-icon
          type="info"
        />
        <section class="course-intro surface-panel">
          <div class="course-intro__cover">
            <img
              :alt="detail.courseName"
              :src="
                getCourseCoverUrl(
                  detail.coverUrl,
                  detail.courseId || detail.courseName,
                )
              "
              @error="
                applyCourseCoverFallback(
                  $event,
                  detail.courseId || detail.courseName,
                )
              "
            />
          </div>
          <div class="course-intro__body">
            <h2 class="section-title">课程简介</h2>
            <p
              :class="{ 'is-empty': !detail.description }"
              class="course-intro__text"
            >
              {{ detail.description || '教师还没有填写课程简介。' }}
            </p>
          </div>
        </section>

        <div class="metric-grid">
          <div class="metric-card">
            <div class="metric-card__label">任务总数</div>
            <div class="metric-card__value">{{ taskCount }}</div>
            <div class="metric-card__meta">本课程的全部实训任务</div>
          </div>
          <div class="metric-card">
            <div class="metric-card__label">已提交任务</div>
            <div class="metric-card__value">{{ submittedTaskCount }}</div>
            <div class="metric-card__meta">有提交记录的任务</div>
          </div>
          <div class="metric-card">
            <div class="metric-card__label">课程资源</div>
            <div class="metric-card__value">{{ resourceCount }}</div>
            <div class="metric-card__meta">可在线查看与下载</div>
          </div>
          <div class="metric-card">
            <div class="metric-card__label">已解锁等级</div>
            <div class="metric-card__value">{{ unlockedLevelCount }}</div>
            <div class="metric-card__meta">
              {{
                unlockLevels.length > 0
                  ? `共 ${unlockLevels.length} 个等级`
                  : '暂无等级数据'
              }}
            </div>
          </div>
        </div>

        <Card>
          <Tabs
            v-model:active-key="activeSheet"
            :animated="{ inkBar: true, tabPane: true }"
          >
            <TabPane key="tasks" tab="实训任务">
              <DetailSheetGrid
                v-if="detail.homeworkList?.length"
                ref="taskGridRef"
                :columns="taskColumns"
                :data="detail.homeworkList"
                row-key="homeworkId"
              >
                <template #taskNameCell="{ row }">
                  <Button
                    class="px-0"
                    type="link"
                    @click="
                      handleGoHomework(
                        row.homeworkId || row.id || 0,
                        row.status,
                        row.homeworkLevel,
                      )
                    "
                  >
                    {{ row.homeworkName || '-' }}
                  </Button>
                </template>
                <template #taskStatusCell="{ row }">
                  <StatusTag
                    v-bind="getHomeworkRecordStatusMeta(row.status, 'student')"
                  />
                </template>
                <template #taskActions="{ row }">
                  <TableAction
                    :actions="[
                      {
                        label: '查看任务',
                        type: 'link',
                        icon: ACTION_ICON.VIEW,
                        onClick: handleGoHomework.bind(
                          null,
                          row.homeworkId || row.id || 0,
                          row.status,
                          row.homeworkLevel,
                        ),
                      },
                    ]"
                  />
                </template>
              </DetailSheetGrid>
              <EmptyState
                v-else
                compact
                description="教师发布任务后会显示在这里。"
                title="这门课还没有实训任务"
              />
            </TabPane>

            <TabPane key="resources" tab="资源">
              <CourseResourceWorkspace
                :download-resource="handleDownloadResource"
                :fetch-resource="fetchCourseResource"
                :resources="detail.resources || []"
                empty-description="教师还没有上传课程资源"
              />
            </TabPane>

            <TabPane key="levels" tab="等级闯关">
              <template v-if="levelViews.length > 0">
                <p class="level-hint">{{ levelRuleHint }}</p>
                <ul class="level-list">
                  <li
                    v-for="lv in levelViews"
                    :key="lv.level"
                    class="level-row"
                  >
                    <span class="level-row__name">{{ lv.name }}</span>
                    <StatusTag v-bind="getLevelUnlockMeta(lv.unlocked)" />
                    <Progress
                      :percent="lv.percent"
                      :show-info="false"
                      class="level-row__bar"
                      size="small"
                    />
                    <span class="level-row__meta">{{ lv.countText }}</span>
                    <span
                      v-if="lv.detailText"
                      :class="{ 'is-locked': !lv.unlocked }"
                      class="level-row__detail"
                    >
                      {{ lv.detailText }}
                    </span>
                  </li>
                </ul>
              </template>
              <EmptyState
                v-else
                compact
                description="教师发布分等级的任务后，这里会显示各等级的达标进度。"
                title="还没有等级数据"
              />
            </TabPane>
          </Tabs>
        </Card>
      </div>
    </Spin>
  </Page>

  <Page v-else auto-content-height title="课程详情">
    <div
      v-if="loading && !loadError"
      class="flex min-h-60 flex-col items-center justify-center gap-3 text-sm text-muted-foreground"
      role="status"
    >
      <Spin />
      <span>正在读取课程…</span>
    </div>
    <div v-else-if="errorState" class="surface-panel">
      <EmptyState
        :description="errorState.description"
        :title="errorState.title"
        kind="blocked"
      >
        <template #action>
          <template v-if="loadError?.kind === 'unavailable'">
            <Button type="primary" @click="router.push('/student/course')">
              返回我的课程
            </Button>
          </template>
          <template v-else>
            <Button type="primary" @click="loadData">重试</Button>
            <Button @click="router.push('/student/course')">
              返回我的课程
            </Button>
          </template>
        </template>
      </EmptyState>
    </div>
  </Page>
</template>

<style scoped>
.course-intro {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  padding: var(--nus-card-padding);
}

.course-intro__cover {
  flex: none;
  width: 200px;
  overflow: hidden;
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius);
}

.course-intro__cover img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.course-intro__body {
  min-width: 0;
}

.course-intro__text {
  max-width: 720px;
  margin: 8px 0 0;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text-secondary);
  overflow-wrap: anywhere;
}

.course-intro__text.is-empty {
  color: var(--nus-text-tertiary);
}

.level-hint {
  margin: 0 0 4px;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.level-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.level-row {
  display: grid;
  grid-template-columns: 64px 96px minmax(0, 1fr) 160px;
  gap: 16px;
  align-items: center;
  min-height: 48px;
  border-bottom: 1px solid var(--nus-border-subtle);
}

.level-row:last-child {
  border-bottom: 0;
}

.level-row__detail {
  grid-column: 3 / -1;
  padding-bottom: 8px;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.level-row__detail.is-locked {
  color: var(--nus-text-secondary);
}

.level-row__name {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text);
}

.level-row__bar {
  margin: 0;
}

.level-row__meta {
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  color: var(--nus-text-secondary);
  text-align: right;
}

@media (width < 768px) {
  .course-intro {
    flex-direction: column;
  }

  .course-intro__cover {
    width: 100%;
  }

  .level-row {
    grid-template-columns: 64px minmax(0, 1fr);
    padding: 12px 0;
  }

  .level-row__bar,
  .level-row__meta,
  .level-row__detail {
    grid-column: 1 / -1;
  }

  .level-row__detail {
    padding-bottom: 0;
  }

  .level-row__meta {
    text-align: left;
  }
}
</style>
