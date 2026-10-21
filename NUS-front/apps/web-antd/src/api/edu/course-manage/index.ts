import { requestClient } from '#/api/request';

export namespace EduCourseManageApi {
  export interface CourseDetail {
    courseId: number;
    courseName: string;
    courseIntroduction?: string;
    courseImageUrl?: string;
    trainingCategory?: string;
    indicatorId?: number;
    indicatorName?: string;
    teacherId?: number;
    teacherName?: string;
    /**
     * 当前用户能否管理这门课（添加班级、封存或恢复班课）：全局管理员、课程负责人或课程团队成员。
     * 由后端按课程归属计算；旧版后端没有这个字段，按不能管理处理。
     */
    canManage?: boolean;
    /**
     * 是否公共课认领模式（V1.0.1.10）：班级由教师各自认领，只能操作自己认领的班级。
     * 旧版后端没有这个字段，按原来的样子处理。
     */
    claimMode?: boolean;
    /** 公共课里负责人、审批人、管理员在看全部班级（别人认领的只能查看） */
    viewAllReadOnly?: boolean;
    /**
     * 当前用户能否认领班级：课程为在线公共课，且有绑定班级的权限码、是教师或管理员。
     * 旧版后端没有这个字段，按权限码与角色推断。
     */
    canClaim?: boolean;
    classes: CourseClass[];
    tasks: CourseTask[];
    resources: CourseResource[];
  }

  export interface CourseClass {
    archived: boolean;
    classCourseId: number;
    classId: number;
    className: string;
    semesterName?: string;
    semesterStartDate?: string;
    semesterEndDate?: string;
    status?: string;
    studentCount?: number;
    assistantTeacherIds?: string;
    assistantTeacherNames?: string;
    createTime?: string;
    /** 公共课认领人（V1.0.1.10）；未认领时为空 */
    claimTeacherId?: null | number;
    claimTeacherName?: null | string;
    /**
     * 负责人（V1.0.1.10）：公共课为认领人，未认领时 ownerId 为空、ownerName 为「未认领」；
     * 专属课程为添加这个班的教师（课程负责人或团队成员），否则为课程负责人。旧版后端没有这两个字段。
     */
    ownerId?: null | number;
    ownerName?: string;
    /**
     * 当前用户能否给这个班布置任务、放弃认领、封存（公共课按认领人判断，管理员也为 true）。
     * 旧版后端没有这个字段，按原来的样子处理。
     */
    canOperate?: boolean;
    /**
     * 该行已被认领，且当前用户能放弃（认领人本人）或清除（管理员、审批人）认领。
     * 旧版后端没有这个字段，按 canOperate 与角色推断。
     */
    canRelease?: boolean;
  }

  export interface CourseTask {
    homeworkId: number;
    homeworkName: string;
    classId: number;
    className?: string;
    semesterName?: string;
    deadline?: string;
    studentCount?: number;
    submittedCount?: number;
    correctedCount?: number;
    avgScore?: number;
    /**
     * 当前用户能否修改、封存或恢复这个任务（布置者）。
     * 旧版后端没有这个字段，保持原来的样子（按能管理处理）。
     */
    canManage?: boolean;
    /**
     * 当前用户能否批改这个任务；不能批改时批改页为只读。
     * 旧版后端没有这个字段，保持原来的样子（按能批改处理）。
     */
    canGrade?: boolean;
    /** 负责人：任务布置者（V1.0.1.10）；查不到时 teacherName 为「-」 */
    teacherId?: number;
    teacherName?: string;
    /** 任务状态（V1.0.1.10）：0 待发布（旧版草稿）、1 已发布、2 已截止、3 已封存 */
    status?: number;
  }

  export interface CourseResource {
    id: number;
    resourceName: string;
    fileUrl?: string;
    fileType?: string;
    fileSize?: number;
    createTime?: string;
  }

  export interface ClassDetail {
    classCourseId: number;
    courseId: number;
    courseName: string;
    indicatorName?: string;
    /** 授课教师：公共课为班级认领人，未认领时为课程负责人 */
    teacherName?: string;
    classId: number;
    className: string;
    semesterName?: string;
    semesterStartDate?: string;
    semesterEndDate?: string;
    classStatus?: string;
    studentCount?: number;
    assistantTeacherNames?: string;
    /**
     * 当前用户能否操作这个班（发送留言等）。公共课里负责人、管理员只读查看别人认领的班级时为 false；
     * 旧版后端没有这个字段，按能操作处理。
     */
    canOperate?: boolean;
    tasks: ClassTask[];
    members: ClassMember[];
    scoreAnalysis: ScoreAnalysis;
    messages: ClassMessage[];
  }

  export interface ClassTask {
    homeworkId: number;
    homeworkName: string;
    publishTime?: string;
    deadline?: string;
    submittedCount?: number;
    correctedCount?: number;
    avgScore?: number;
  }

  export interface ClassMember {
    studentId: number;
    studentNo?: string;
    studentName?: string;
    avatar?: string;
    finishedTaskCount?: number;
    totalTaskCount?: number;
    avgScore?: number;
  }

  export interface ScoreRange {
    range: string;
    count: number;
    percent: number;
  }

  export interface ScoreAnalysis {
    avgScore?: number;
    highestScore?: number;
    lowestScore?: number;
    passRate?: number;
    scoreRanges: ScoreRange[];
  }

  export interface ClassMessage {
    id: number;
    studentId: number;
    senderId?: number;
    senderName?: string;
    senderAvatar?: string;
    type: number;
    content: string;
    createTime?: string;
  }
}

export function getCourseManageDetail(courseId: number) {
  return requestClient.get<EduCourseManageApi.CourseDetail>(
    '/edu/course-manage/detail',
    { params: { courseId } },
  );
}

export function getCourseManageClassDetail(classCourseId: number) {
  return requestClient.get<EduCourseManageApi.ClassDetail>(
    '/edu/course-manage/class-detail',
    { params: { classCourseId } },
  );
}

export function sendTeacherClassMessage(data: {
  classId: number;
  content: string;
  courseId: number;
  studentId: number;
}) {
  return requestClient.post('/edu/course-manage/message/send-teacher', data);
}
