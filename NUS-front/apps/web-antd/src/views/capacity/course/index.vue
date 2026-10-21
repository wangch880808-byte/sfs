<script lang="ts" setup>
import type { CourseRow } from './data';

import type { CapacityCourseApi } from '#/api/capacity/course';
import type { StatusMeta } from '#/utils/status';

import { computed, nextTick, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { Button, Input, message, Spin, Switch } from 'ant-design-vue';

import {
  auditPublicCourse,
  CAPACITY_COURSE_AUDIT_PERMISSION,
  deleteCourse,
  getCoursePage,
  restoreCourse,
} from '#/api/capacity/course';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import {
  ARCHIVE_COPY,
  archivedMessage,
  archivingMessage,
  isArchived,
  restoredMessage,
} from '#/utils/archive';
import {
  applyCourseCoverFallback,
  getCourseCoverUrl,
} from '#/utils/course-cover';
import { getCourseStatusMeta } from '#/utils/status-course';

import { getCourseTypeLabel, isPublicCourse } from './course-type';
import { getIndicatorNameMap, getUserNameMap, parseAssistantIds } from './data';
import Form from './modules/form.vue';

defineOptions({ name: 'CapacityCourse' });

type CourseSectionKey =
  | 'archived'
  | 'created'
  | 'joined'
  | 'pending'
  | 'public';

interface CourseSection {
  key: CourseSectionKey;
  title: string;
  /** 分组为空时标题旁的一句说明：为什么空、课程怎么出现在这里 */
  emptyHint: string;
  courses: CourseRow[];
}

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const userStore = useUserStore();
const router = useRouter();
const { hasAccessByCodes } = useAccess();

const loading = ref(false);
/** 第一次读取完成前不显示分组与空状态，只显示加载 */
const loaded = ref(false);
const loadFailed = ref(false);
const searchText = ref('');
const includeArchived = ref(false);
const courseList = ref<CourseRow[]>([]);
const COURSE_PAGE_SIZE = 200;
const sectionScrollRef = ref<HTMLDivElement>();
const sectionRefs = ref<Record<CourseSectionKey, HTMLElement | null>>({
  public: null,
  pending: null,
  created: null,
  joined: null,
  archived: null,
});

const currentUserId = computed(() => {
  const userInfo = userStore.userInfo as
    | undefined
    | { id?: number | string; userId?: number | string };
  return Number(userInfo?.userId ?? userInfo?.id ?? 0) || 0;
});
const canCreate = computed(() =>
  hasAccessByCodes(['capacity:indicator:create']),
);
// 与后端一致：修改/删除课程要求 update/delete 或 create 任一权限
const canUpdate = computed(() =>
  hasAccessByCodes(['capacity:indicator:update', 'capacity:indicator:create']),
);
const canDelete = computed(() =>
  hasAccessByCodes(['capacity:indicator:delete', 'capacity:indicator:create']),
);
/** 公共课审批权限（角色管理中按需授予）；持有者同时可查看、管理全部课程，与后端一致 */
const canApprove = computed(() =>
  hasAccessByCodes([CAPACITY_COURSE_AUDIT_PERMISSION]),
);

const pageDescription = computed(() =>
  canApprove.value
    ? '审批教师的公共课申请，管理公共课和你创建、参与的课程。'
    : '公共课和你创建、参与的课程都在这里；进入课程可以绑定班级、发布实训任务。',
);

function isMyCreatedCourse(course: CapacityCourseApi.Course) {
  return (
    currentUserId.value > 0 && Number(course.teacherId) === currentUserId.value
  );
}

/** 课程只能由管理员或课程创建人修改、删除（与后端服务层校验一致） */
function canManageCourse(course: CapacityCourseApi.Course) {
  return canApprove.value || isMyCreatedCourse(course);
}

function isMyJoinedCourse(course: CapacityCourseApi.Course) {
  return (
    currentUserId.value > 0 &&
    !isMyCreatedCourse(course) &&
    parseAssistantIds(course.teachAssistantIds).includes(currentUserId.value)
  );
}

function isVisiblePublicCourse(course: CapacityCourseApi.Course) {
  return (
    !isArchived(course.status) &&
    isPublicCourse(course) &&
    Number(course.publicAuditStatus) === 2
  );
}

function getKeywordFields(course: CourseRow) {
  return [
    course.courseName,
    course.courseIntroduction,
    course.indicatorName,
    course.teacherName,
    course.teachAssistantNames,
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
}

function filterCourses(source: CourseRow[]) {
  const keyword = searchText.value.trim().toLowerCase();
  return keyword
    ? source.filter((c) => getKeywordFields(c).includes(keyword))
    : source;
}

const searchKeyword = computed(() => searchText.value.trim());

const publicCourses = computed(() =>
  filterCourses(
    courseList.value.filter((course) => isVisiblePublicCourse(course)),
  ),
);
const pendingAuditCourses = computed(() =>
  filterCourses(
    courseList.value.filter(
      (course) =>
        !isArchived(course.status) && Number(course.publicAuditStatus) === 1,
    ),
  ),
);
const createdCourses = computed(() =>
  filterCourses(
    courseList.value.filter(
      (course) =>
        !isArchived(course.status) &&
        isMyCreatedCourse(course) &&
        !isVisiblePublicCourse(course),
    ),
  ),
);
const joinedCourses = computed(() =>
  filterCourses(
    courseList.value.filter(
      (course) =>
        !isArchived(course.status) &&
        isMyJoinedCourse(course) &&
        !isVisiblePublicCourse(course),
    ),
  ),
);
const archivedCourses = computed(() =>
  filterCourses(courseList.value.filter((course) => isArchived(course.status))),
);

const courseSections = computed<CourseSection[]>(() => {
  const sections: CourseSection[] = [
    {
      key: 'public',
      title: '公共课',
      emptyHint: '还没有公共课。教师申请公共课、管理员审批通过后会出现在这里。',
      courses: publicCourses.value,
    },
  ];
  if (canApprove.value) {
    sections.push({
      key: 'pending',
      title: '待我审批',
      emptyHint: '没有待审批的申请。',
      courses: pendingAuditCourses.value,
    });
  }
  sections.push(
    {
      key: 'created',
      title: '我创建的',
      emptyHint: canCreate.value
        ? '你还没有新建课程，新建后会出现在这里。'
        : '你还没有新建课程。',
      courses: createdCourses.value,
    },
    {
      key: 'joined',
      title: '我参与的',
      emptyHint:
        '你还没有参与的课程。课程创建人把你添加到助教团队后会出现在这里。',
      courses: joinedCourses.value,
    },
  );
  if (includeArchived.value) {
    sections.push({
      key: 'archived',
      title: ARCHIVE_COPY.archived,
      emptyHint: '没有已封存的课程。',
      courses: archivedCourses.value,
    });
  }
  return sections;
});

const totalVisibleCourses = computed(() =>
  courseSections.value.reduce(
    (sum, section) => sum + section.courses.length,
    0,
  ),
);

/** 分组只在读到课程后显示；一门都没有时整页显示一个空状态 */
const showSections = computed(
  () => loaded.value && !loadFailed.value && totalVisibleCourses.value > 0,
);

const emptyDescription = computed(() => {
  if (canApprove.value) {
    return '公共课、待你审批的申请，以及你创建或参与的课程会列在这里。';
  }
  return canCreate.value
    ? '新建课程后，可以进入课程绑定班级、发布实训任务。'
    : '公共课和你参与的课程会列在这里。';
});

function getCardStatus(course: CourseRow): null | StatusMeta {
  return getCourseStatusMeta(
    course,
    canApprove.value ? 'approver' : 'applicant',
  );
}

async function getAllCourseList() {
  let pageNo = 1;
  let total = 0;
  const allCourses: CapacityCourseApi.Course[] = [];
  do {
    const pageResult = await getCoursePage({
      pageNo,
      pageSize: COURSE_PAGE_SIZE,
      includeArchived: includeArchived.value,
    });
    const currentList = pageResult.list ?? [];
    total = Number(pageResult.total || 0);
    allCourses.push(...currentList);
    if (currentList.length === 0) break;
    pageNo += 1;
  } while (allCourses.length < total);
  return allCourses;
}

async function loadCourses() {
  loading.value = true;
  try {
    const [indicatorNameMap, userNameMap, allCourses] = await Promise.all([
      getIndicatorNameMap(),
      getUserNameMap(),
      getAllCourseList(),
    ]);
    courseList.value = allCourses.map((item) => ({
      ...item,
      indicatorName:
        item.indicatorName ??
        indicatorNameMap.get(Number(item.indicatorId)) ??
        '-',
      teacherName: userNameMap.get(Number(item.teacherId)) ?? '-',
      teachAssistantNames:
        parseAssistantIds(item.teachAssistantIds)
          .map((id) => userNameMap.get(id))
          .filter(Boolean)
          .join('、') || '',
    }));
    loadFailed.value = false;
  } catch (error) {
    loadFailed.value = true;
    throw error;
  } finally {
    loading.value = false;
    loaded.value = true;
  }
}

async function handleRefresh() {
  await loadCourses();
  await nextTick();
}
function handleCreate() {
  formModalApi.setData(null).open();
}

function handleClearSearch() {
  searchText.value = '';
}

function handleEdit(course: CapacityCourseApi.Course) {
  const courseId = Number(course.id || 0);
  if (!courseId) {
    message.warning('这门课程的数据不完整，无法编辑。请刷新后重试。');
    return;
  }
  formModalApi.setData({ id: courseId }).open();
}

function handleViewDetail(course: CourseRow) {
  const courseId = Number(course.id || 0);
  if (!courseId) {
    message.warning('这门课程的数据不完整，无法查看详情。请刷新后重试。');
    return;
  }
  router.push({
    path: `/capacity/course/detail/${courseId}`,
  });
}

async function handleDelete(course: CourseRow) {
  if (!canDelete.value || !canManageCourse(course) || !course.id) return;
  await confirm({
    cancelText: '返回',
    confirmText: ARCHIVE_COPY.archive,
    content: `${ARCHIVE_COPY.archiveHint}：打开「${ARCHIVE_COPY.showArchived}」，在课程上点「${ARCHIVE_COPY.restore}」。`,
    icon: 'warning',
    title: `${ARCHIVE_COPY.archive}课程「${course.courseName}」？`,
  });
  const hideLoading = message.loading({
    content: archivingMessage(course.courseName),
    duration: 0,
  });
  try {
    await deleteCourse(course.id);
    message.success(archivedMessage(course.courseName));
    await loadCourses();
  } finally {
    hideLoading();
  }
}

async function handleRestore(course: CourseRow) {
  if (!canDelete.value || !canManageCourse(course) || !course.id) return;
  await confirm({
    cancelText: '返回',
    confirmText: ARCHIVE_COPY.restore,
    content: '恢复后，这门课回到原来的分组，学生可以重新看到。',
    title: `${ARCHIVE_COPY.restore}课程「${course.courseName}」？`,
  });
  await restoreCourse(course.id);
  message.success(restoredMessage(course.courseName));
  await loadCourses();
}

async function handleAudit(course: CourseRow, approved: boolean) {
  if (!canApprove.value || !course.id) return;
  const actionName = approved ? '通过' : '驳回';
  await confirm({
    cancelText: '返回',
    confirmText: actionName,
    content: approved
      ? '通过后，这门课作为公共课向全体教师开放。'
      : '驳回后，这门课不会作为公共课开放；教师修改课程后可以重新申请。',
    icon: approved ? undefined : 'warning',
    title: `${actionName}「${course.courseName}」的公共课申请？`,
  });
  const hideLoading = message.loading({
    content: `正在${actionName}申请…`,
    duration: 0,
  });
  try {
    await auditPublicCourse({
      id: Number(course.id),
      approved,
    });
    message.success(`已${actionName}公共课申请`);
    await loadCourses();
  } finally {
    hideLoading();
  }
}

function setSectionRef(key: CourseSectionKey) {
  return (el: any) => {
    sectionRefs.value[key] = el as HTMLElement | null;
  };
}

/** 工具栏里的分组导航：滚到对应分组的标题（滚动容器设了定位，offsetTop 相对它计算） */
function scrollToSection(key: CourseSectionKey) {
  const container = sectionScrollRef.value;
  const target = sectionRefs.value[key];
  if (!container || !target) return;
  // 系统开了“减少动态效果”时直接跳过去
  const reduceMotion = window.matchMedia?.(
    '(prefers-reduced-motion: reduce)',
  ).matches;
  container.scrollTo({
    top: Math.max(target.offsetTop, 0),
    behavior: reduceMotion ? 'auto' : 'smooth',
  });
}

onMounted(async () => {
  await loadCourses();
});
</script>

<template>
  <Page :description="pageDescription" auto-content-height title="实训课程管理">
    <template #extra>
      <Button :loading="loading" @click="handleRefresh">
        <template #icon>
          <IconifyIcon icon="lucide:refresh-cw" />
        </template>
        刷新
      </Button>
      <Button v-if="canCreate" type="primary" @click="handleCreate">
        <template #icon>
          <IconifyIcon icon="lucide:plus" />
        </template>
        新建课程
      </Button>
    </template>

    <FormModal @success="handleRefresh" />

    <div class="course-board">
      <div class="course-toolbar">
        <Input
          v-model:value="searchText"
          allow-clear
          class="course-toolbar__search"
          placeholder="搜索课程名称、教师、助教或指标"
        >
          <template #prefix>
            <IconifyIcon
              class="course-toolbar__search-icon"
              icon="lucide:search"
            />
          </template>
        </Input>
        <!-- 分组导航：点一下滚到对应分组；整页是空状态时不显示 -->
        <nav aria-label="课程分组" class="course-jump">
          <button
            v-for="section in showSections ? courseSections : []"
            :key="section.key"
            class="course-jump__item"
            type="button"
            @click="scrollToSection(section.key)"
          >
            {{ section.title }}
            <span class="course-jump__count">
              {{ section.courses.length }}
            </span>
          </button>
        </nav>
        <label class="course-toolbar__archived">
          <Switch
            v-model:checked="includeArchived"
            size="small"
            @change="handleRefresh"
          />
          {{ ARCHIVE_COPY.showArchived }}
        </label>
      </div>

      <div class="course-board__body">
        <Spin :spinning="loading" tip="正在读取课程…">
          <div ref="sectionScrollRef" class="course-board__scroll">
            <template v-if="loaded">
              <EmptyState
                v-if="loadFailed"
                description="网络或服务暂时不可用，已有的筛选条件会保留。"
                kind="blocked"
                title="课程没有读取成功"
              >
                <template #action>
                  <Button @click="handleRefresh">重新读取</Button>
                </template>
              </EmptyState>
              <EmptyState
                v-else-if="totalVisibleCourses === 0 && searchKeyword"
                :title="`没有与「${searchKeyword}」匹配的课程`"
                description="换个关键词，或清空搜索查看全部课程。"
                kind="filtered"
              >
                <template #action>
                  <Button @click="handleClearSearch">清空搜索</Button>
                </template>
              </EmptyState>
              <EmptyState
                v-else-if="totalVisibleCourses === 0"
                :description="emptyDescription"
                title="还没有课程"
              >
                <template v-if="canCreate" #action>
                  <Button @click="handleCreate">新建课程</Button>
                </template>
              </EmptyState>
            </template>

            <template v-if="showSections">
              <section
                v-for="section in courseSections"
                :key="section.key"
                :ref="setSectionRef(section.key)"
                class="course-section"
              >
                <header class="course-section__head">
                  <h2 class="course-section__title">{{ section.title }}</h2>
                  <span class="course-section__count">
                    {{ section.courses.length }} 门
                  </span>
                  <template v-if="section.courses.length === 0">
                    <span aria-hidden="true" class="course-section__hint">
                      ·
                    </span>
                    <span class="course-section__hint">
                      {{
                        searchKeyword ? '没有匹配的课程。' : section.emptyHint
                      }}
                    </span>
                  </template>
                </header>

                <div v-if="section.courses.length > 0" class="course-grid">
                  <article
                    v-for="course in section.courses"
                    :key="`${section.key}-${course.id}`"
                    :class="{ 'is-archived': isArchived(course.status) }"
                    class="course-card"
                    @click="
                      !isArchived(course.status) && handleViewDetail(course)
                    "
                  >
                    <div class="course-card__cover">
                      <img
                        :alt="course.courseName"
                        :src="
                          getCourseCoverUrl(
                            course.courseImageUrl,
                            course.id || course.courseName,
                          )
                        "
                        @error="
                          applyCourseCoverFallback(
                            $event,
                            course.id || course.courseName,
                          )
                        "
                      />
                    </div>

                    <div class="course-card__body">
                      <div class="course-card__heading">
                        <h3
                          :title="course.courseName"
                          class="course-card__name"
                        >
                          {{ course.courseName || '-' }}
                        </h3>
                        <StatusTag
                          v-if="getCardStatus(course)"
                          :label="getCardStatus(course)?.label"
                          :tone="getCardStatus(course)?.tone"
                        />
                      </div>
                      <p class="course-card__intro">
                        {{ course.courseIntroduction || '暂无课程简介' }}
                      </p>
                      <p class="course-card__meta">
                        <span>{{ course.teacherName || '-' }}</span>
                        <span aria-hidden="true">·</span>
                        <span class="course-card__meta-fill">
                          助教 {{ course.teachAssistantNames || '暂无' }}
                        </span>
                      </p>
                      <p class="course-card__meta">
                        <span class="course-card__meta-fill">
                          {{ course.indicatorName || '未关联指标' }}
                        </span>
                        <span aria-hidden="true">·</span>
                        <span>{{ getCourseTypeLabel(course) }}</span>
                      </p>
                      <p
                        v-if="
                          course.publicAuditRemark &&
                          Number(course.publicAuditStatus) === 3
                        "
                        class="course-card__remark"
                      >
                        审批意见：{{ course.publicAuditRemark }}
                      </p>
                    </div>

                    <footer class="course-card__actions" @click.stop>
                      <Button
                        v-if="!isArchived(course.status)"
                        size="small"
                        type="link"
                        @click.stop="handleViewDetail(course)"
                      >
                        查看详情
                      </Button>
                      <Button
                        v-if="
                          !isArchived(course.status) &&
                          canApprove &&
                          Number(course.publicAuditStatus) === 1
                        "
                        size="small"
                        type="link"
                        @click.stop="handleAudit(course, true)"
                      >
                        审批通过
                      </Button>
                      <Button
                        v-if="
                          !isArchived(course.status) &&
                          canApprove &&
                          Number(course.publicAuditStatus) === 1
                        "
                        size="small"
                        type="link"
                        @click.stop="handleAudit(course, false)"
                      >
                        驳回
                      </Button>
                      <Button
                        v-if="
                          !isArchived(course.status) &&
                          canUpdate &&
                          canManageCourse(course)
                        "
                        size="small"
                        type="link"
                        @click.stop="handleEdit(course)"
                      >
                        编辑
                      </Button>
                      <Button
                        v-if="
                          !isArchived(course.status) &&
                          canDelete &&
                          canManageCourse(course)
                        "
                        class="course-card__action-end"
                        size="small"
                        type="link"
                        @click.stop="handleDelete(course)"
                      >
                        {{ ARCHIVE_COPY.archive }}
                      </Button>
                      <Button
                        v-if="
                          isArchived(course.status) &&
                          canDelete &&
                          canManageCourse(course)
                        "
                        size="small"
                        type="link"
                        @click.stop="handleRestore(course)"
                      >
                        {{ ARCHIVE_COPY.restore }}
                      </Button>
                    </footer>
                  </article>
                </div>
              </section>
            </template>
          </div>
        </Spin>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.course-board {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-4);
  height: 100%;
  min-height: 0;
}

/* ---- 工具栏：搜索、分组导航、显示已封存 ---- */
.course-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-3);
  align-items: center;
}

.course-toolbar__search {
  width: 280px;
  max-width: 100%;
}

.course-toolbar__search-icon {
  color: var(--nus-text-tertiary);
}

.course-jump {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: var(--nus-space-1);
  align-items: center;
  min-width: 0;
}

.course-jump__item {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 28px;
  padding: 0 var(--nus-space-2);
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--nus-radius);
  transition: var(--nus-transition-colors);
}

.course-jump__item:hover {
  color: var(--nus-text);
  background: var(--nus-bg-hover);
}

.course-jump__item:focus-visible {
  outline: 2px solid var(--nus-focus-ring);
  outline-offset: 1px;
}

.course-jump__count {
  font-weight: var(--nus-fw-regular);
  font-variant-numeric: tabular-nums;
  color: var(--nus-text-tertiary);
}

.course-toolbar__archived {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: center;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  white-space: nowrap;
  cursor: pointer;
}

/* ---- 列表区：加载遮罩铺满，内容在里面滚动 ---- */
.course-board__body {
  flex: 1;
  min-height: 0;
}

.course-board__body :deep(.ant-spin-nested-loading),
.course-board__body :deep(.ant-spin-container) {
  height: 100%;
}

.course-board__scroll {
  position: relative;
  height: 100%;
  padding-bottom: var(--nus-space-6);
  overflow-y: auto;
}

/* ---- 分组 ---- */
.course-section + .course-section {
  margin-top: var(--nus-section-gap);
}

.course-section__head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  align-items: baseline;
}

.course-section__head + .course-grid {
  margin-top: var(--nus-space-3);
}

.course-section__title {
  margin: 0;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.course-section__count {
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

/* 空分组只占一行：在标题旁说明为什么空、课程怎么出现在这里 */
.course-section__hint {
  min-width: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

/* ---- 课程卡片：淡边框，悬停换边框与底色，不抬起、不加阴影 ---- */
.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--nus-space-4);
}

.course-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  cursor: pointer;
  background: var(--nus-bg-surface);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
  transition: var(--nus-transition-colors);
}

.course-card:hover {
  background: var(--nus-bg-hover);
  border-color: var(--nus-border);
}

.course-card.is-archived {
  cursor: default;
}

.course-card.is-archived:hover {
  background: var(--nus-bg-surface);
}

.course-card__cover {
  aspect-ratio: 16 / 9;
  margin: var(--nus-space-3) var(--nus-space-3) 0;
  overflow: hidden;
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius-lg);
}

.course-card__cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-card.is-archived .course-card__cover img {
  opacity: 0.6;
  filter: grayscale(1);
}

.course-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--nus-space-1);
  min-width: 0;
  padding: var(--nus-space-3);
}

.course-card__heading {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.course-card__name {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
  white-space: nowrap;
}

.course-card__intro {
  display: -webkit-box;
  margin: 0 0 var(--nus-space-1);
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  -webkit-box-orient: vertical;
}

/* 元信息：一行几段，中间用“·”隔开；放不下时省略填充的那一段 */
.course-card__meta {
  display: flex;
  gap: 6px;
  min-width: 0;
  margin: 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
  white-space: nowrap;
}

.course-card__meta-fill {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.course-card__remark {
  margin: var(--nus-space-1) 0 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
}

.course-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-1);
  align-items: center;
  padding: var(--nus-space-1) var(--nus-space-2);
  cursor: default;
  border-top: 1px solid var(--nus-border-subtle);
}

.course-card__action-end {
  margin-left: auto;
}
</style>
