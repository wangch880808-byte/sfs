<script lang="ts" setup>
import type { StudentCourseApi } from '#/api/edu/student/course';

import { computed } from 'vue';

import { EmptyState } from '#/components/empty-state';
import {
  applyCourseCoverFallback,
  getCourseCoverUrl,
} from '#/utils/course-cover';
import { DashboardPanel, DashboardRow } from '#/views/dashboard/components';
import {
  STUDENT_LINKS,
  useAvailableLink,
} from '#/views/dashboard/shared/links';
import { getCourseProgress } from '#/views/dashboard/shared/student';

/** 学生首页：我的课程与完成进度（已完成任务数 / 任务总数），整行进入课程详情 */
const props = withDefaults(
  defineProps<{
    courses: StudentCourseApi.CourseItem[];
    error?: boolean;
    loading?: boolean;
  }>(),
  { error: false, loading: false },
);

defineEmits<{ retry: [] }>();

const VISIBLE = 6;
const linkOf = useAvailableLink();
const visibleCourses = computed(() => props.courses.slice(0, VISIBLE));
const identityOf = (course: StudentCourseApi.CourseItem) =>
  course.courseId || course.courseName;
</script>

<template>
  <DashboardPanel
    :count="courses.length > 0 ? `${courses.length} 门` : ''"
    :empty="courses.length === 0"
    :error="error"
    :loading="loading"
    :more-to="linkOf(STUDENT_LINKS.courseList)"
    error-title="课程没有读到"
    loading-text="正在读取你的课程…"
    more-text="全部课程"
    title="我的课程"
    @retry="$emit('retry')"
  >
    <template #empty>
      <EmptyState
        compact
        description="加入班级、老师分配课程后，课程会出现在这里。"
        title="还没有课程"
      />
    </template>
    <ul>
      <li v-for="course in visibleCourses" :key="course.courseId">
        <DashboardRow :to="linkOf(STUDENT_LINKS.courseDetail(course.courseId))">
          <template #leading>
            <img
              :src="getCourseCoverUrl(course.coverUrl, identityOf(course))"
              alt=""
              class="course-cover"
              @error="applyCourseCoverFallback($event, identityOf(course))"
            />
          </template>
          {{ course.courseName }}
          <template v-if="course.teacherName" #meta>
            {{ course.teacherName }}
          </template>
          <template #aside>
            <span class="course-progress">
              <span aria-hidden="true" class="course-progress__bar">
                <span
                  :style="{ width: `${getCourseProgress(course).percent}%` }"
                  class="course-progress__fill"
                ></span>
              </span>
              已完成 {{ getCourseProgress(course).completed }}/{{
                getCourseProgress(course).total
              }}
            </span>
          </template>
        </DashboardRow>
      </li>
    </ul>
  </DashboardPanel>
</template>

<style scoped>
.course-cover {
  width: 56px;
  height: 32px;
  object-fit: cover;
  background-color: var(--nus-bg-sunken);
  border-radius: var(--nus-radius-sm);
}

.course-progress {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: center;
}

.course-progress__bar {
  position: relative;
  width: 64px;
  height: 4px;
  overflow: hidden;
  background-color: var(--nus-border);
  border-radius: var(--nus-radius-sm);
}

.course-progress__fill {
  position: absolute;
  inset: 0 auto 0 0;
  background-color: var(--nus-primary);
  border-radius: var(--nus-radius-sm);
  transition: width var(--nus-dur) var(--nus-ease);
}
</style>
