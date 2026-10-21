import { requestClient } from '#/api/request';

export namespace EduHomeworkCopyApi {
  /** 可以布置任务的班级课程（复制任务时选班级用） */
  export interface Target {
    classCourseId: number;
    classId: number;
    className?: string;
    courseId: number;
    courseName?: string;
    publicCourse?: boolean;
    semesterName?: string;
  }
}

/** 某学期我能布置任务的班级课程；不传学期时取当前学期 */
export function getHomeworkCopyTargets(semesterName?: string) {
  return requestClient.get<EduHomeworkCopyApi.Target[]>(
    '/edu/homework/copy/targets',
    { params: { semesterName } },
  );
}
