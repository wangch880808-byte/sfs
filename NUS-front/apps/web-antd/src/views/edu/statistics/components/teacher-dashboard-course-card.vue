<script lang="ts" setup>
import type { TeacherCourseItem } from '#/views/dashboard/shared/teacher';

import { EmptyState } from '#/components/empty-state';
import {
  applyCourseCoverFallback,
  getCourseCoverUrl,
} from '#/utils/course-cover';
import { DashboardPanel, DashboardRow } from '#/views/dashboard/components';
import {
  TEACHER_LINKS,
  useAvailableLink,
} from '#/views/dashboard/shared/links';

/** 教师工作台：我的课程，带班级、学生、任务数与待批改份数，整行进入课程详情 */
withDefaults(
  defineProps<{
    courses: TeacherCourseItem[];
    error?: boolean;
    loading?: boolean;
  }>(),
  { error: false, loading: false },
);

defineEmits<{ retry: [] }>();

const linkOf = useAvailableLink();

function metaOf(course: TeacherCourseItem) {
  if (course.classCount === undefined) {
    return '';
  }
  return [
    `${course.classCount} 个班级`,
    `${course.studentCount ?? 0} 名学生`,
    `${course.taskCount ?? 0} 项任务`,
  ].join(' · ');
}
</script>

<template>
  <DashboardPanel
    :count="courses.length > 0 ? `${courses.length} 门` : ''"
    :empty="courses.length === 0"
    :error="error"
    :loading="loading"
    :more-to="linkOf(TEACHER_LINKS.courseList)"
    error-title="课程没有读到"
    loading-text="正在读取你的课程…"
    more-text="全部课程"
    title="我的课程"
    @retry="$emit('retry')"
  >
    <template #empty>
      <EmptyState
        compact
        description="在课程管理里新建课程、绑定班级后，课程会出现在这里。"
        title="还没有课程"
      />
    </template>
    <ul>
      <li v-for="course in courses" :key="course.courseId">
        <DashboardRow :to="linkOf(TEACHER_LINKS.courseDetail(course.courseId))">
          <template #leading>
            <img
              :src="getCourseCoverUrl(course.coverUrl, course.courseId)"
              alt=""
              class="course-cover"
              @error="applyCourseCoverFallback($event, course.courseId)"
            />
          </template>
          {{ course.courseName }}
          <template #meta>{{ metaOf(course) }}</template>
          <template v-if="course.pendingCount" #aside>
            <span class="course-pending">
              {{ course.pendingCount }} 份待批改
            </span>
          </template>
        </DashboardRow>
      </li>
    </ul>
  </DashboardPanel>
</template>

<style scoped>
.course-pending {
  color: var(--nus-text);
}

.course-cover {
  width: 56px;
  height: 32px;
  object-fit: cover;
  background-color: var(--nus-bg-sunken);
  border-radius: var(--nus-radius-sm);
}
</style>
