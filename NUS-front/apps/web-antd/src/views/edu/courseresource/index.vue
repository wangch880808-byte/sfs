<script lang="ts" setup>
import type { EduCourseResourceApi } from '#/api/edu/course-resource';

import { computed, onActivated, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Spin } from 'ant-design-vue';

import { getCoursePage } from '#/api/capacity/course';
import { getCourseResourceList } from '#/api/edu/course-resource';
import CourseResourceWorkspace from '#/components/course-resource/workspace.vue';
import { EmptyState } from '#/components/empty-state';

interface CourseItem {
  id: number;
  courseName: string;
}

defineOptions({ name: 'EduCourseResource' });

const loading = ref(false);
const courseList = ref<CourseItem[]>([]);
const selectedCourseId = ref<number>();
const resourceList = ref<EduCourseResourceApi.CourseResource[]>([]);

const selectedCourse = computed(() =>
  courseList.value.find((c) => c.id === selectedCourseId.value),
);

async function loadCourses() {
  const result = await getCoursePage({ pageNo: 1, pageSize: 200 });
  courseList.value = (result.list ?? []).map((item) => ({
    id: Number(item.id),
    courseName: item.courseName,
  }));
  if (courseList.value.length > 0 && !selectedCourseId.value) {
    selectedCourseId.value = courseList.value[0]!.id;
  }
}

async function loadResources() {
  if (!selectedCourseId.value) {
    resourceList.value = [];
    return;
  }
  resourceList.value = await getCourseResourceList(selectedCourseId.value);
}

async function loadData() {
  loading.value = true;
  try {
    await loadCourses();
    await loadResources();
  } finally {
    loading.value = false;
  }
}

async function handleSelectCourse(courseId: number) {
  selectedCourseId.value = courseId;
  await loadResources();
}

onMounted(() => {
  loadData();
});

let hasActivated = false;
onActivated(() => {
  if (hasActivated) {
    loadData();
    return;
  }
  hasActivated = true;
});
</script>

<template>
  <Page
    auto-content-height
    description="按课程上传和管理教学资源，学生在课程详情里查看。"
    title="课程资源"
  >
    <Spin :spinning="loading" tip="正在读取课程与资源…">
      <div class="cr-layout">
        <!-- 左侧课程列表 -->
        <nav aria-label="课程" class="cr-courses surface-panel">
          <div class="cr-courses__head">
            <span class="cr-courses__title">课程</span>
            <span v-if="courseList.length > 0" class="cr-courses__count">
              {{ courseList.length }}
            </span>
          </div>
          <ul v-if="courseList.length > 0" class="cr-courses__list">
            <li v-for="item in courseList" :key="item.id">
              <button
                :aria-current="
                  selectedCourseId === item.id ? 'true' : undefined
                "
                :class="{ 'is-selected': selectedCourseId === item.id }"
                :title="item.courseName"
                class="cr-course"
                type="button"
                @click="handleSelectCourse(item.id)"
              >
                {{ item.courseName }}
              </button>
            </li>
          </ul>
          <EmptyState
            v-else-if="!loading"
            compact
            description="先在实训课程管理里新建课程，再来上传资源。"
            title="还没有课程"
          />
        </nav>

        <!-- 右侧资源列表 -->
        <section class="cr-resources surface-panel">
          <template v-if="selectedCourseId">
            <h2 class="section-title cr-resources__title">
              {{ selectedCourse?.courseName || '课程资源' }}
            </h2>
            <CourseResourceWorkspace
              :course-id="selectedCourseId"
              :editable="true"
              :resources="resourceList"
              empty-description="这门课程还没有资源，上传后会显示在这里。"
              @updated="loadResources"
            />
          </template>
          <EmptyState
            v-else
            description="从左侧选择一门课程后，可以上传和管理它的资源。"
            kind="blocked"
            title="还没有选择课程"
          />
        </section>
      </div>
    </Spin>
  </Page>
</template>

<style scoped>
.cr-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.cr-courses {
  overflow: hidden;
}

.cr-courses__head {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--nus-border-subtle);
}

.cr-courses__title {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.cr-courses__count {
  font-size: var(--nus-fs-caption);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.cr-courses__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  margin: 0;
  list-style: none;
}

.cr-course {
  display: block;
  width: 100%;
  padding: 7px 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text-secondary);
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--nus-radius);
  transition: var(--nus-transition-colors);
}

.cr-course:hover {
  color: var(--nus-text);
  background: var(--nus-bg-hover);
}

.cr-course.is-selected {
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text);
  background: var(--nus-bg-selected);
}

.cr-course:focus-visible {
  outline: 2px solid var(--nus-primary);
  outline-offset: -2px;
}

.cr-resources {
  min-width: 0;
  padding: var(--nus-card-padding);
}

.cr-resources__title {
  margin: 0 0 16px;
}

@media (width <= 768px) {
  .cr-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
