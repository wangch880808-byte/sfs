/**
 * 课程详情里班级的管理权限与对应文案（V1.0.1.8 B2）。
 * 添加班级、封存或恢复班课，要同时满足：后端判定能管理这门课（canManage：全局管理员、课程负责人或课程团队成员），
 * 并且有绑定班级的权限码。不能管理的人看到的空状态改为说明找谁添加班级。
 */

/** 添加班级、封存与恢复班课的权限码（后端绑定、解绑、恢复接口按它校验） */
export const CLASS_COURSE_BIND_PERMISSION = 'edu:class-course:bind';

export function canManageCourseClasses(
  detail: undefined | { canManage?: boolean },
  hasAccessByCodes: (codes: string[]) => boolean,
): boolean {
  return (
    detail?.canManage === true &&
    hasAccessByCodes([CLASS_COURSE_BIND_PERMISSION])
  );
}

/** 不能管理班级时，提示找谁添加 */
const ASK_FOR_CLASS = '请联系课程负责人或管理员添加班级';

export interface ClassAbsenceCopy {
  /** 班级页签：还没有关联班级 */
  classEmpty: string;
  /** 任务页签：还没有班级，不能发布任务 */
  taskBlocked: string;
  /** 点「新建任务」时还没有班级 */
  createTaskHint: string;
  /** 点「AI 新建任务」时还没有班级 */
  createAiTaskHint: string;
}

/** 课程还没有班级时的文案：能管理的人去「班级」里添加，不能管理的人联系课程负责人或管理员 */
export function getClassAbsenceCopy(canManage: boolean): ClassAbsenceCopy {
  if (canManage) {
    return {
      classEmpty: '把班级添加到本课程后，才能给班级发布实训任务。',
      taskBlocked: '实训任务要发布到班级。先在「班级」里把班级添加到本课程。',
      createTaskHint: '先在「班级」里把班级添加到本课程，再新建任务。',
      createAiTaskHint:
        '先在「班级」里把班级添加到本课程，再使用 AI 新建任务。',
    };
  }
  return {
    classEmpty: `班级添加到本课程后，才能给班级发布实训任务。${ASK_FOR_CLASS}。`,
    taskBlocked: `实训任务要发布到班级，这门课还没有班级。${ASK_FOR_CLASS}。`,
    createTaskHint: `这门课还没有班级，不能新建任务。${ASK_FOR_CLASS}。`,
    createAiTaskHint: `这门课还没有班级，不能使用 AI 新建任务。${ASK_FOR_CLASS}。`,
  };
}

/**
 * 课程详情「实训任务」里任务行的操作（V1.0.1.9）。
 * 管理员、课程负责人、课程团队成员能看到别人布置的任务，但修改、封存、恢复与批改只有布置者能做。
 * 后端在任务行里返回 canManage、canGrade；旧版后端没有这两个字段时保持原来的样子。
 * 功能权限码仍由操作列的 auth 判断，两者都满足才显示。
 */
export interface CourseTaskAccess {
  canGrade?: boolean;
  canManage?: boolean;
  /** 任务状态（V1.0.1.10）：3 为已封存，只读 */
  status?: number;
}

/** 能否显示编辑、封存、恢复等修改类操作：只有后端明确说不能管理（false）时隐藏 */
export function canManageCourseTask(task?: CourseTaskAccess): boolean {
  return task?.canManage !== false;
}

/**
 * 批改按钮的文字：后端明确说不能批改（false），或任务已封存（只读）时改为「查看提交」，批改页会自动只读
 */
export function getCourseTaskGradeLabel(task?: CourseTaskAccess): string {
  return task?.canGrade === false || Number(task?.status) === 3
    ? '查看提交'
    : '批改';
}
