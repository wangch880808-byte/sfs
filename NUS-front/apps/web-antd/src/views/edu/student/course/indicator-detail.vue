<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { StudentCourseApi } from '#/api/edu/student/course';
import type { CourseResourceLike } from '#/components/course-resource/resource-helpers';
import type { StudentLoadError } from '#/views/edu/student/homework/load-error';

import { computed, onActivated, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { downloadFileFromBlobPart } from '@vben/utils';

import {
  Button,
  Card,
  message,
  Progress,
  Spin,
  TabPane,
  Tabs,
} from 'ant-design-vue';

import { ACTION_ICON, TableAction } from '#/adapter/vxe-table';
import {
  downloadStudentCourseResource,
  getStudentCourseList,
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
import { isAwaitingSubmissionStatus } from '#/utils/status-student-homework';
import { resolveStudentLoadError } from '#/views/edu/student/homework/load-error';
import {
  getTaskLevelLabel,
  getTaskTypeLabel,
} from '#/views/edu/student/homework/task-display';

interface StudentCourseCard extends StudentCourseApi.CourseItem {
  indicatorId: number;
  teacherNameResolved: string;
}

interface IndicatorDetailData {
  indicatorId: number;
  indicatorName: string;
  /** 补充说明（仅“未关联一级指标”分组提示完善配置） */
  description: string;
  courses: StudentCourseCard[];
  totalHomework: number;
  completedHomework: number;
  progress: number;
}

interface CoursePanelData {
  homeworks: StudentCourseApi.CourseHomework[];
  resources: StudentCourseApi.CourseResource[];
}

const route = useRoute();
const router = useRouter();
const loading = ref(false);
/** 拿到课程后再渲染带页头的页面：页面按页头的实际高度计算内容区 */
const detailData = ref<IndicatorDetailData>();
/** 课程没有读取成功时的原因 */
const loadError = ref<null | StudentLoadError>(null);
/** 读取失败的说明：后端给了原因就用它；断网、超时没有原因时，页面自己补一句 */
const loadErrorDescription = computed(() => {
  const error = loadError.value;
  if (!error) return '';
  const reason =
    error.reason ||
    (error.kind === 'unavailable'
      ? '课程不存在，或没有分配给你。'
      : '网络或服务暂时不可用。');
  return `${reason}可以重试，或返回我的课程。`;
});
const coursePanelDataMap = reactive<Record<number, CoursePanelData>>({});
const coursePanelLoadingMap = reactive<Record<number, boolean>>({});
let mounted = false;

const indicatorId = computed(() => Number(route.params.indicatorId || 0));

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

function getCourseHomeworks(courseId: number) {
  return coursePanelDataMap[courseId]?.homeworks ?? [];
}

function getCourseResources(courseId: number) {
  return coursePanelDataMap[courseId]?.resources ?? [];
}

const homeworkColumns: VxeTableGridOptions<StudentCourseApi.CourseHomework>['columns'] =
  [
    {
      field: 'homeworkName',
      title: '任务名称',
      minWidth: 220,
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
      title: '操作',
      width: 120,
      slots: { default: 'taskActions' },
    },
  ];

function isCoursePanelLoading(courseId: number) {
  return !!coursePanelLoadingMap[courseId];
}

function clearCoursePanelCache() {
  for (const key of Object.keys(coursePanelDataMap)) {
    delete coursePanelDataMap[Number(key)];
  }
  for (const key of Object.keys(coursePanelLoadingMap)) {
    delete coursePanelLoadingMap[Number(key)];
  }
}

async function loadData() {
  if (!indicatorId.value) {
    message.warning('没有读到要查看的指标，已回到「我的课程」，请重新选择。');
    router.replace('/student/course');
    return;
  }
  loading.value = true;
  loadError.value = null;
  clearCoursePanelCache();
  try {
    // 学生课程接口已带出课程所属一级指标，直接按其筛选与取名，不再调用管理端指标接口
    // 加载失败由本页的错误态说明原因与下一步，不再弹全局提示
    const courseList = await getStudentCourseList({ silentError: true });

    const courses: StudentCourseCard[] = (courseList ?? []).map((course) => {
      const totalHomework = toNumber(course.totalHomework);
      const completedHomework = toNumber(course.completedHomework);
      const courseId = toNumber(course.courseId);
      coursePanelDataMap[courseId] = {
        homeworks: (course.homeworkList ?? []).map((item) => ({
          ...item,
          homeworkId: Number(item.homeworkId || item.id),
        })),
        resources: course.resources ?? [],
      };
      return {
        ...course,
        courseId,
        teacherNameResolved: course.teacherName || '-',
        indicatorId: toNumber(course.indicatorId),
        totalHomework,
        completedHomework,
        progress: buildProgress(totalHomework, completedHomework),
      };
    });

    let filteredCourses: StudentCourseCard[] = [];
    let indicatorName = '指标课程';
    let description = '';

    if (indicatorId.value === -1) {
      filteredCourses = courses.filter(
        (course) => toNumber(course.indicatorId) <= 0,
      );
      indicatorName = '未关联一级指标';
      description = '请联系教师完善课程指标配置';
    } else if (indicatorId.value > 0) {
      filteredCourses = courses.filter(
        (course) => toNumber(course.indicatorId) === indicatorId.value,
      );
      indicatorName =
        filteredCourses[0]?.indicatorName || `一级指标 ${indicatorId.value}`;
    } else {
      filteredCourses = courses;
    }

    const totalHomework = filteredCourses.reduce(
      (sum, item) => sum + toNumber(item.totalHomework),
      0,
    );
    const completedHomework = filteredCourses.reduce(
      (sum, item) => sum + toNumber(item.completedHomework),
      0,
    );

    detailData.value = {
      indicatorId: indicatorId.value,
      indicatorName,
      description,
      courses: filteredCourses,
      totalHomework,
      completedHomework,
      progress: buildProgress(totalHomework, completedHomework),
    };
  } catch (error) {
    // 这次读取不弹全局提示（silentError）；页面写明原因与下一步，不会同时出现两条
    loadError.value = resolveStudentLoadError(error);
  } finally {
    loading.value = false;
  }
}

function handleGoHomework(homeworkId: number, status: number) {
  if (isAwaitingSubmissionStatus(toNumber(status))) {
    router.push({
      path: `/student/homework/submit/${homeworkId}`,
    });
    return;
  }
  router.push({
    path: `/student/homework/feedback/${homeworkId}`,
  });
}

function handleGoCourseDetail(courseId: number) {
  router.push({
    path: `/student/course/detail/${courseId}`,
  });
}

function handleGoCourses() {
  router.push('/student/course');
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
</script>

<template>
  <Page
    v-if="detailData"
    :description="
      detailData.description || '这个一级指标下你参与的课程、任务与资源。'
    "
    :title="detailData.indicatorName"
    auto-content-height
  >
    <template #extra>
      <Button @click="router.back()">
        <template #icon>
          <IconifyIcon icon="lucide:arrow-left" />
        </template>
        返回
      </Button>
    </template>

    <Spin :spinning="loading" tip="正在刷新课程…">
      <div class="page-stack">
        <div class="metric-grid xl:grid-cols-3">
          <div class="metric-card">
            <div class="metric-card__label">参与课程</div>
            <div class="metric-card__value">
              {{ detailData.courses.length }}
            </div>
            <div class="metric-card__meta">这个指标下你参与的课程</div>
          </div>
          <div class="metric-card">
            <div class="metric-card__label">已评定达标</div>
            <div class="metric-card__value">
              {{ detailData.completedHomework }}
              <span class="metric-total">/ {{ detailData.totalHomework }}</span>
            </div>
            <div class="metric-card__meta">按任务计</div>
          </div>
          <div class="metric-card">
            <div class="metric-card__label">达标进度</div>
            <div class="metric-card__value">{{ detailData.progress }}%</div>
            <Progress
              :percent="detailData.progress"
              :show-info="false"
              class="metric-progress"
              size="small"
            />
          </div>
        </div>

        <Card v-for="course in detailData.courses" :key="course.courseId">
          <div class="course-head">
            <div class="course-head__cover">
              <img
                :alt="course.courseName"
                :src="
                  getCourseCoverUrl(
                    course.coverUrl,
                    course.courseId || course.courseName,
                  )
                "
                @error="
                  applyCourseCoverFallback(
                    $event,
                    course.courseId || course.courseName,
                  )
                "
              />
            </div>
            <div class="course-head__body">
              <div class="course-head__name" :title="course.courseName">
                {{ course.courseName }}
              </div>
              <div class="course-head__meta">
                授课教师：{{ course.teacherNameResolved || '-' }}
                <span class="course-head__sep">·</span>
                达标进度：{{ course.completedHomework }}/{{
                  course.totalHomework
                }}
              </div>
            </div>
            <Button
              size="small"
              type="link"
              @click="handleGoCourseDetail(course.courseId)"
            >
              课程详情
            </Button>
          </div>

          <Tabs class="course-tabs" size="small">
            <TabPane
              :key="`task-${course.courseId}`"
              :tab="`实训任务（${getCourseHomeworks(course.courseId).length}）`"
            >
              <Spin :spinning="isCoursePanelLoading(course.courseId)">
                <DetailSheetGrid
                  v-if="getCourseHomeworks(course.courseId).length > 0"
                  :columns="homeworkColumns"
                  :data="getCourseHomeworks(course.courseId)"
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
                        )
                      "
                    >
                      {{ row.homeworkName }}
                    </Button>
                  </template>
                  <template #taskStatusCell="{ row }">
                    <StatusTag
                      v-bind="
                        getHomeworkRecordStatusMeta(row.status, 'student')
                      "
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
              </Spin>
            </TabPane>
            <TabPane
              :key="`resource-${course.courseId}`"
              :tab="`资源（${getCourseResources(course.courseId).length}）`"
            >
              <Spin :spinning="isCoursePanelLoading(course.courseId)">
                <CourseResourceWorkspace
                  :download-resource="handleDownloadResource"
                  :fetch-resource="fetchCourseResource"
                  :resources="getCourseResources(course.courseId)"
                  empty-description="教师还没有上传资源"
                />
              </Spin>
            </TabPane>
          </Tabs>
        </Card>

        <div v-if="detailData.courses.length === 0" class="surface-panel">
          <EmptyState
            description="教师把课程关联到这个指标后，课程会显示在这里。"
            title="这个指标下还没有你参与的课程"
          >
            <template #action>
              <Button @click="handleGoCourses">返回我的课程</Button>
            </template>
          </EmptyState>
        </div>
      </div>
    </Spin>
  </Page>

  <Page v-else auto-content-height title="指标课程详情">
    <div
      v-if="loading && !loadError"
      class="flex min-h-60 flex-col items-center justify-center gap-3 text-sm text-muted-foreground"
      role="status"
    >
      <Spin />
      <span>正在读取课程…</span>
    </div>
    <div v-else-if="loadError" class="surface-panel">
      <EmptyState
        :description="loadErrorDescription"
        kind="blocked"
        title="课程没有读取成功"
      >
        <template #action>
          <Button type="primary" @click="loadData">重试</Button>
          <Button @click="handleGoCourses">返回我的课程</Button>
        </template>
      </EmptyState>
    </div>
  </Page>
</template>

<style scoped>
.metric-total {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-regular);
  color: var(--nus-text-tertiary);
}

.metric-progress {
  margin: 4px 0 0;
}

.course-head {
  display: flex;
  gap: 16px;
  align-items: center;
}

.course-head__cover {
  flex: none;
  width: 112px;
  overflow: hidden;
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius);
}

.course-head__cover img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.course-head__body {
  flex: 1;
  min-width: 0;
}

.course-head__name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
  white-space: nowrap;
}

.course-head__meta {
  margin-top: 4px;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.course-head__sep {
  margin: 0 6px;
  color: var(--nus-text-tertiary);
}

.course-tabs {
  margin-top: 12px;
}
</style>
