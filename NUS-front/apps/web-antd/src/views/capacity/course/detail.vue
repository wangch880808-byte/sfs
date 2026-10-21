<script lang="ts" setup>
import type { ClaimSelectOption } from './class-claim';
import type { ClassFilterState } from './class-filter';
import type { TaskFilterState } from './task-filter';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type {
  EduCourseManageApi as CourseManageApi,
  EduCourseManageApi,
} from '#/api/edu/course-manage';
import type { EduSemesterApi } from '#/api/edu/semester';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  Drawer,
  Input,
  message,
  Modal,
  Select,
  Space,
  Spin,
  Switch,
  TabPane,
  Tabs,
  Tooltip,
} from 'ant-design-vue';

import { ACTION_ICON, TableAction } from '#/adapter/vxe-table';
import {
  CAPACITY_COURSE_AUDIT_PERMISSION,
  deleteCourse,
  getCourse,
  updateCourse,
} from '#/api/capacity/course';
import { getSimpleClassList } from '#/api/edu/class';
import {
  bindClassCourse,
  claimClassCourse,
  getClassCourseClaimOptions,
  releaseClassCourse,
  restoreClassCourse,
  unbindClassCourse,
} from '#/api/edu/class-course';
import { getCourseManageDetail } from '#/api/edu/course-manage';
import {
  deleteHomework,
  getHomeworkRemoveImpact,
  removeHomework,
  restoreHomework,
} from '#/api/edu/homework';
import { getCurrentSemester, getSemesterOptions } from '#/api/edu/semester';
import CourseResourceWorkspace from '#/components/course-resource/workspace.vue';
import DetailSheetGrid from '#/components/detail-sheet-grid.vue';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import {
  ARCHIVE_COPY,
  archivedMessage,
  restoredMessage,
} from '#/utils/archive';
import { rowConfirm } from '#/utils/confirm-copy';
import {
  applyCourseCoverFallback,
  getCourseCoverUrl,
} from '#/utils/course-cover';
import { resolvePersona } from '#/utils/persona';
import { getClassCourseStatusMeta } from '#/utils/status-course-detail';
import QuestionBankTaskModal from '#/views/capacity/course/modules/question-bank-task-modal.vue';
import HomeworkForm from '#/views/edu/homework/HomeworkForm.vue';
import {
  buildRemoveConfirm,
  REMOVE_CANCEL_TEXT,
  REMOVE_OK_TEXT,
} from '#/views/edu/homework/remove-confirm';

import {
  buildClaimSelectOptions,
  createLatestRequestGuard,
  filterOperableClasses,
  getClaimClassAbsenceCopy,
  getClaimViewNotice,
  getClassRowActions,
  getCourseMetricCopy,
  isClaimMode,
  resolveCanClearOthersClaim,
  resolveClaimAbility,
} from './class-claim';
import {
  buildClassOwnerOptions,
  clearedClassFilter,
  defaultClassFilter,
  filterClassRows,
  getClassOwnerLabel,
  hasHiddenAddedClasses,
  isClassOwnerUnclaimed,
  keepOwnerKey,
} from './class-filter';
import {
  canManageCourseClasses,
  canManageCourseTask,
  CLASS_COURSE_BIND_PERMISSION,
  getClassAbsenceCopy,
  getCourseTaskGradeLabel,
} from './detail-access';
import {
  buildTaskOwnerOptions,
  defaultTaskFilter,
  filterTaskRows,
  getCourseTaskRowActions,
  getCourseTaskStatusMeta,
  getTaskOwnerLabel,
  isTaskSealed,
} from './task-filter';

interface CourseProfile {
  courseId: number;
  courseName: string;
  teacherName: string;
  indicatorName: string;
  trainingCategory: string;
  courseImageUrl: string;
  courseIntroduction: string;
}

defineOptions({ name: 'CapacityCourseDetail' });

const route = useRoute();
const router = useRouter();
const [TaskCreateModal, taskCreateModalApi] = useVbenModal({
  connectedComponent: HomeworkForm,
  destroyOnClose: true,
});

const loading = ref(false);
const activeSheet = ref<'classes' | 'resources' | 'tasks'>('classes');
const detailData = ref<CourseManageApi.CourseDetail>();
const includeArchivedClasses = ref(false);

const { hasAccessByCodes } = useAccess();
const userStore = useUserStore();
/** 当前用户编号；读不到时为 0 */
const currentUserId = computed(() => {
  const userInfo = userStore.userInfo as
    | undefined
    | { id?: number | string; userId?: number | string };
  return Number(userInfo?.userId ?? userInfo?.id ?? 0) || 0;
});
/** 课程只能由持有公共课审批权限的人或课程创建人修改、删除（与后端服务层校验一致） */
const canManageCourse = computed(() => {
  if (hasAccessByCodes([CAPACITY_COURSE_AUDIT_PERMISSION])) {
    return true;
  }
  return (
    currentUserId.value > 0 &&
    Number(detailData.value?.teacherId) === currentUserId.value
  );
});
// 与后端一致：修改/删除课程要求 update/delete 或 create 任一权限
const canUpdateCourse = computed(
  () =>
    canManageCourse.value &&
    hasAccessByCodes([
      'capacity:indicator:update',
      'capacity:indicator:create',
    ]),
);
const canDeleteCourse = computed(
  () =>
    canManageCourse.value &&
    hasAccessByCodes([
      'capacity:indicator:delete',
      'capacity:indicator:create',
    ]),
);
/**
 * 添加班级、封存或恢复班课：后端判定能管理这门课（全局管理员、课程负责人或课程团队成员），
 * 并且有绑定班级的权限码。与上面修改、封存课程的判断不同，团队成员也可以。
 */
const canManageClasses = computed(() =>
  canManageCourseClasses(detailData.value, hasAccessByCodes),
);
/** 公共课认领模式（V1.0.1.10）：班级由教师各自认领；旧版后端没有 claimMode 时按原来的样子 */
const claimMode = computed(() => isClaimMode(detailData.value));
/** 公共课里负责人、审批人、管理员在看全部班级 */
const viewAllReadOnly = computed(
  () => claimMode.value && detailData.value?.viewAllReadOnly === true,
);
const hasBindPermission = computed(() =>
  hasAccessByCodes([CLASS_COURSE_BIND_PERMISSION]),
);
const personaKind = computed(() => resolvePersona(userStore.userRoles).kind);
/**
 * 能清除别人认领的人：管理员、公共课审批人（课程负责人不能）。
 * 别人认领的行上有 canRelease 时以它为准，否则按角色判断；只用于顶部提示和旧版后端的行操作推断。
 */
const canClearOthersClaim = computed(() =>
  resolveCanClearOthersClaim({
    currentUserId: currentUserId.value,
    hasAuditPermission: hasAccessByCodes([CAPACITY_COURSE_AUDIT_PERMISSION]),
    isAdmin: personaKind.value === 'admin',
    rows: detailData.value?.classes || [],
  }),
);
/**
 * 「认领班级」：以课程详情的 canClaim 为准（课程在线、绑定班级的权限码、教师或管理员）；
 * 旧版后端没有 canClaim 时按权限码与角色推断。
 */
const claimAbility = computed(() =>
  resolveClaimAbility({
    canClaim: detailData.value?.canClaim,
    claimMode: claimMode.value,
    hasBindPermission: hasBindPermission.value,
    isTeacherOrAdmin:
      personaKind.value === 'teacher' || personaKind.value === 'admin',
  }),
);
const canClaim = computed(() => claimAbility.value.canClaim);
/** 班级页签右上角的按钮：公共课是「认领班级」，专属课程是「添加班级」 */
const showClassModalButton = computed(() =>
  claimMode.value ? canClaim.value : canManageClasses.value,
);
const classAbsenceCopy = computed(() =>
  claimMode.value
    ? getClaimClassAbsenceCopy(canClaim.value, claimAbility.value.courseOffline)
    : getClassAbsenceCopy(canManageClasses.value),
);
const claimViewNotice = computed(() =>
  getClaimViewNotice({
    canClearOthersClaim: canClearOthersClaim.value,
    claimMode: claimMode.value,
    viewAllReadOnly: viewAllReadOnly.value,
  }),
);

const classOptions = ref<Array<{ label: string; value: number }>>([]);
/** 公共课认领弹窗的班级（已排序，已认领的灰显不可选） */
const claimOptions = ref<ClaimSelectOption[]>([]);
const claimOptionsLoading = ref(false);
/** 认领列表只采用最后一次请求的结果（快速切换学期时，先发的请求后返回不覆盖） */
const claimOptionsGuard = createLatestRequestGuard();
const currentSemester = ref<EduSemesterApi.CurrentSemester | null>(null);

const UNSET_SEMESTER_NAME = '未设置';

const classFilter = ref<ClassFilterState>(defaultClassFilter());
/** 任务页签：默认不显示已封存的任务，可按负责人筛选 */
const taskFilter = ref<TaskFilterState>(defaultTaskFilter());

const classModalOpen = ref(false);
const questionBankTaskModalOpen = ref(false);
const classForm = ref({
  classIds: [] as number[],
  semesterName: undefined as string | undefined,
});
/** 添加班级时可选的学期：当前学期和班级课程里用过的学期 */
const bindSemesterOptions = ref<EduSemesterApi.SemesterOption[]>([]);

const courseEditModalOpen = ref(false);
const courseEditForm = ref({
  courseName: '',
  trainingCategory: '',
  courseIntroduction: '',
});

const taskDrawerOpen = ref(false);
const activeTask = ref<CourseManageApi.CourseTask>();
const classGridRef = ref<{ recalculate?: () => void }>();
const taskGridRef = ref<{
  handleCustom?: () => Promise<void> | void;
  recalculate?: () => void;
  zoom?: () => Promise<boolean> | void;
}>();

const courseId = computed(() => Number(route.params.courseId || 0));

const profile = computed<CourseProfile>(() => {
  const detail = detailData.value;
  return {
    courseId: Number(detail?.courseId || courseId.value || 0),
    courseName: detail?.courseName || '未命名课程',
    teacherName: detail?.teacherName || '未设置',
    indicatorName: detail?.indicatorName || '未关联指标',
    trainingCategory: detail?.trainingCategory || '实训课程',
    courseImageUrl: detail?.courseImageUrl || '',
    courseIntroduction: detail?.courseIntroduction || '暂无课程详情',
  };
});

/** 页头：标题是课程名；说明是课程类型、一级指标与授课教师，读取完成前留空 */
const pageTitle = computed(() =>
  detailData.value ? profile.value.courseName : '课程详情',
);
const pageMeta = computed(() => {
  const detail = detailData.value;
  if (!detail) {
    return '';
  }
  return [
    detail.trainingCategory || '实训课程',
    detail.indicatorName
      ? `一级指标：${detail.indicatorName}`
      : '未关联一级指标',
    detail.teacherName ? `授课教师：${detail.teacherName}` : '',
  ]
    .filter(Boolean)
    .join(' · ');
});
const courseIntroduction = computed(
  () => detailData.value?.courseIntroduction?.trim() || '',
);
const hasAnyClass = computed(
  () => (detailData.value?.classes || []).length > 0,
);

const displayedClassRows = computed(() =>
  (detailData.value?.classes || []).filter(
    (item) => includeArchivedClasses.value || !item.archived,
  ),
);
const classRows = computed(() =>
  displayedClassRows.value.filter((item) => !item.archived),
);
/** 后端返回的全部任务，含已封存（3）与旧版待发布（0） */
const taskRows = computed(() => detailData.value?.tasks || []);
/** 未封存的任务：用于顶部的任务数 */
const activeTaskRows = computed(() =>
  taskRows.value.filter((item) => !isTaskSealed(item)),
);
/** 打开「显示已封存」时含已封存的任务；负责人选项从这里去重 */
const shownTaskRows = computed(() =>
  filterTaskRows(taskRows.value, {
    showSealed: taskFilter.value.showSealed,
  }),
);
const filteredTaskRows = computed(() =>
  filterTaskRows(taskRows.value, taskFilter.value),
);
const taskOwnerOptions = computed(() =>
  buildTaskOwnerOptions(shownTaskRows.value),
);
const resourceRows = computed(() => detailData.value?.resources || []);
const runningClassCount = computed(
  () => classRows.value.filter((item) => item.status === '进行中').length,
);
const courseMetricCards = computed(() => {
  // 公共课普通教师只看自己认领的班级和自己布置的任务（后端已收窄），文案随之改为「我认领的班级」等
  const copy = getCourseMetricCopy({
    claimMode: claimMode.value,
    viewAllReadOnly: viewAllReadOnly.value,
  });
  return [
    {
      label: copy.classLabel,
      value: classRows.value.length,
      meta: copy.classMeta,
    },
    {
      label: copy.runningLabel,
      value: runningClassCount.value,
      meta: copy.runningMeta,
    },
    {
      label: copy.taskLabel,
      value: activeTaskRows.value.length,
      meta: copy.taskMeta,
    },
    {
      label: '课程资源',
      value: resourceRows.value.length,
      meta: '支持在线查看与下载',
    },
  ];
});
const courseFirstIndicatorId = computed(
  () => Number(detailData.value?.indicatorId || 0) || undefined,
);
/** 新建任务、AI 新建任务可选的班级：公共课只含自己能操作（认领）的班级，负责人看全部时也一样 */
const taskClassRows = computed(() =>
  claimMode.value ? filterOperableClasses(classRows.value) : classRows.value,
);
const courseClassRelations = computed(() =>
  taskClassRows.value.map((item) => ({
    classId: Number(item.classId),
    className: item.className,
    semesterName: item.semesterName,
    status: item.status,
  })),
);
const selectedBindSemester = computed(() =>
  bindSemesterOptions.value.find(
    (item) => item.semesterName === classForm.value.semesterName,
  ),
);
const bindSemesterSelectOptions = computed(() =>
  bindSemesterOptions.value.map((item) => ({
    label: item.current ? `${item.semesterName}（当前）` : item.semesterName,
    value: item.semesterName,
  })),
);
const isBindSemesterReady = computed(() => Boolean(selectedBindSemester.value));
const selectableClassOptions = computed(() => {
  // 只排除在所选学期里仍在用的班级；其他学期绑定过的班级可以再添加到所选学期
  const semesterName = classForm.value.semesterName?.trim();
  const boundClassIdSet = new Set(
    (detailData.value?.classes || [])
      .filter(
        (item) =>
          !item.archived && (item.semesterName || '').trim() === semesterName,
      )
      .map((item) => Number(item.classId))
      .filter(Boolean),
  );
  return classOptions.value.filter(
    (item) => !boundClassIdSet.has(Number(item.value)),
  );
});
/** 认领弹窗里可以选的班级（未被认领的） */
const claimableOptions = computed(() =>
  claimOptions.value.filter((item) => !item.disabled),
);
/** 弹窗里能选的班级：公共课是可认领的，专属课程是所选学期里还没添加的 */
const modalSelectableOptions = computed(() =>
  claimMode.value ? claimableOptions.value : selectableClassOptions.value,
);
const canSubmitAddClass = computed(
  () =>
    isBindSemesterReady.value &&
    classForm.value.classIds.length > 0 &&
    modalSelectableOptions.value.length > 0,
);
const classSelectSummary = computed(() =>
  claimMode.value
    ? `可认领班级 ${claimableOptions.value.length} 个，已选择 ${classForm.value.classIds.length} 个`
    : `可选班级 ${selectableClassOptions.value.length} 个，已选择 ${classForm.value.classIds.length} 个`,
);
const classModalCopy = computed(() =>
  claimMode.value
    ? { title: '认领班级', okText: '认领', noun: '认领' }
    : { title: '添加班级', okText: '添加', noun: '添加' },
);

const semesterOptions = computed(() => {
  const set = new Set<string>();
  displayedClassRows.value.forEach((item) => {
    if (item.semesterName) set.add(item.semesterName);
  });
  return [...set].map((value) => ({ label: value, value }));
});

const filteredClassRows = computed(() =>
  filterClassRows(displayedClassRows.value, classFilter.value),
);

/** 负责人下拉：从所选学期的班级里去重，公共课多一个「未认领」 */
const classOwnerOptions = computed(() =>
  buildClassOwnerOptions(displayedClassRows.value, {
    claimMode: claimMode.value,
    semester: classFilter.value.semester,
  }),
);

const classColumns = computed<
  NonNullable<VxeTableGridOptions<CourseManageApi.CourseClass>['columns']>
>(() => [
  {
    field: 'className',
    title: '班级名称',
    minWidth: 200,
    align: 'left',
    slots: { default: 'classNameCell' },
  },
  // 负责人：公共课为认领人（未认领的弱化显示），专属课程为添加这个班的教师或课程负责人
  {
    field: 'ownerName',
    title: '负责人',
    minWidth: 120,
    slots: { default: 'classOwnerCell' },
  },
  {
    field: 'semesterName',
    title: '学期',
    minWidth: 140,
  },
  {
    field: 'status',
    title: '状态',
    width: 110,
    slots: { default: 'classStatusCell' },
  },
  {
    field: 'assistantTeacherNames',
    title: '助教',
    minWidth: 160,
    formatter: ({ cellValue }) => cellValue || '-',
  },
  {
    field: 'createTime',
    title: '添加时间',
    width: 180,
    formatter: ({ cellValue }) => cellValue || '-',
  },
  // 数字列右对齐，放在文字列之后，不与左对齐的文字挤在一起
  {
    field: 'studentCount',
    title: '人数',
    width: 90,
    align: 'right',
    formatter: ({ cellValue }) => Number(cellValue || 0),
  },
  {
    title: '操作',
    // 公共课多了「放弃认领」「清除认领」，操作列放宽一些
    width: claimMode.value ? 220 : 160,
    slots: { default: 'classActions' },
  },
]);

const taskColumns: VxeTableGridOptions<CourseManageApi.CourseTask>['columns'] =
  [
    {
      field: 'homeworkName',
      title: '任务名称',
      minWidth: 180,
      align: 'left',
      slots: { default: 'taskNameCell' },
    },
    {
      field: 'className',
      title: '班级',
      minWidth: 150,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'teacherName',
      title: '负责人',
      minWidth: 110,
      formatter: ({ row }) => getTaskOwnerLabel(row),
    },
    {
      field: 'semesterName',
      title: '学期',
      minWidth: 100,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'deadline',
      title: '截止时间',
      width: 160,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'taskStatusCell' },
    },
    // 数字列右对齐，放在文字列之后，不与左对齐的文字挤在一起
    {
      field: 'progress',
      title: '提交进度',
      width: 90,
      align: 'right',
      formatter: ({ row }) => renderSubmitProgress(row),
    },
    {
      field: 'correctProgress',
      title: '批改进度',
      width: 90,
      align: 'right',
      formatter: ({ row }) => renderCorrectProgress(row),
    },
    {
      title: '操作',
      width: 230,
      fixed: 'right',
      slots: { default: 'taskActions' },
    },
  ];

function renderSubmitProgress(row: Record<string, any>) {
  return `${Number(row.submittedCount || 0)}/${Number(row.studentCount || 0)}`;
}

function renderCorrectProgress(row: Record<string, any>) {
  return `${Number(row.correctedCount || 0)}/${Number(row.submittedCount || 0)}`;
}

function applyCurrentSemester(
  semester?: EduSemesterApi.CurrentSemester | null,
) {
  const semesterName = semester?.semesterName?.trim() || '';
  if (!semesterName || semesterName === UNSET_SEMESTER_NAME) {
    currentSemester.value = null;
    return;
  }
  currentSemester.value = {
    semesterName,
    semesterStartDate: semester?.semesterStartDate,
    semesterEndDate: semester?.semesterEndDate,
  };
}

/** 读取班级与当前学期；返回当前学期是否读取失败（失败原因已由全局请求提示） */
async function loadOptions() {
  let semesterFailed = false;
  const [classList, semester, semesterOptionList] = await Promise.all([
    getSimpleClassList(),
    getCurrentSemester().catch(() => {
      semesterFailed = true;
      return null;
    }),
    getSemesterOptions().catch(() => {
      semesterFailed = true;
      return [];
    }),
  ]);
  bindSemesterOptions.value = semesterOptionList || [];
  classOptions.value = (classList || []).map((item) => ({
    label: item.className,
    value: Number(item.id),
  }));
  applyCurrentSemester(semester);
  return semesterFailed;
}

async function loadDetail() {
  if (!courseId.value) {
    message.warning(
      '课程详情没有打开：链接里缺少课程编号。从课程列表重新进入。',
    );
    return;
  }
  loading.value = true;
  try {
    detailData.value = await getCourseManageDetail(courseId.value);
  } finally {
    loading.value = false;
  }
}

function resetClassForm() {
  const current = bindSemesterOptions.value.find((item) => item.current);
  classForm.value = {
    classIds: [],
    semesterName: (current ?? bindSemesterOptions.value[0])?.semesterName,
  };
}

/**
 * 读取公共课认领弹窗的班级（所选学期，不选时后端取当前学期）。
 * 课程没上线等情况后端返回错误，全局已提示后端的原因；这里返回是否读取成功。
 * 快速切换学期时只采用最后一次请求的结果；被后发请求取代的请求不改列表，按成功返回。
 */
async function loadClaimOptions(semesterName?: string) {
  const seq = claimOptionsGuard.next();
  claimOptionsLoading.value = true;
  try {
    const list = await getClassCourseClaimOptions(
      profile.value.courseId,
      semesterName,
    );
    if (claimOptionsGuard.isLatest(seq)) {
      claimOptions.value = buildClaimSelectOptions(list || []);
    }
    return true;
  } catch {
    if (!claimOptionsGuard.isLatest(seq)) {
      return true;
    }
    claimOptions.value = [];
    return false;
  } finally {
    if (claimOptionsGuard.isLatest(seq)) {
      claimOptionsLoading.value = false;
    }
  }
}

async function handleBindSemesterChange() {
  classForm.value.classIds = [];
  if (claimMode.value) {
    await loadClaimOptions(classForm.value.semesterName);
  }
}

async function handleOpenClassModal() {
  // 读取班级或学期失败时，全局已提示失败原因；这里只在读到了、但学期没配完整时提醒
  let readFailed = false;
  try {
    readFailed = await loadOptions();
  } catch {
    readFailed = true;
    currentSemester.value = null;
  }
  resetClassForm();
  // 公共课：读不到认领列表（如课程未上线）时，全局已提示后端返回的原因，不打开弹窗
  if (
    claimMode.value &&
    !(await loadClaimOptions(classForm.value.semesterName))
  ) {
    return;
  }
  classModalOpen.value = true;
  if (!readFailed && bindSemesterOptions.value.length === 0) {
    message.warning(
      `还没有可选的学期：先在「学期管理」里设置当前学期，再${classModalCopy.value.noun}班级。`,
    );
  }
}

function handleSelectAllClasses() {
  classForm.value.classIds = modalSelectableOptions.value.map((item) =>
    Number(item.value),
  );
}

function handleClearSelectedClasses() {
  classForm.value.classIds = [];
}

async function handleOpenTaskModal() {
  if (!profile.value.courseId) {
    message.warning('这门课程的数据不完整，无法新建任务。刷新后重试。');
    return;
  }
  if (taskClassRows.value.length === 0) {
    message.warning(classAbsenceCopy.value.createTaskHint);
    return;
  }
  const indicatorFirstId = courseFirstIndicatorId.value;
  if (!indicatorFirstId) {
    message.warning(
      '这门课程还没有关联指标，无法新建任务。先在课程列表里编辑课程，选好关联指标。',
    );
    return;
  }
  taskCreateModalApi
    .setData({
      scene: 'course-detail-create',
      claimMode: claimMode.value,
      fixedCourseId: profile.value.courseId,
      fixedIndicatorFirstId: indicatorFirstId,
      courseClassRelations: taskClassRows.value.map((item) => ({
        classCourseId: Number(item.classCourseId),
        classId: Number(item.classId),
        className: item.className,
        semesterName: item.semesterName,
        status: item.status,
      })),
    })
    .open();
}

function handleOpenQuestionBankTaskModal() {
  if (!profile.value.courseId) {
    message.warning('这门课程的数据不完整，无法使用 AI 新建任务。刷新后重试。');
    return;
  }
  if (taskClassRows.value.length === 0) {
    message.warning(classAbsenceCopy.value.createAiTaskHint);
    return;
  }
  if (!courseFirstIndicatorId.value) {
    message.warning(
      '这门课程还没有关联指标，无法从题库抽题。先在课程列表里编辑课程，选好关联指标。',
    );
    return;
  }
  questionBankTaskModalOpen.value = true;
}

async function handleOpenTaskEdit(task: Record<string, any>) {
  const row = task as EduCourseManageApi.CourseTask;
  if (!row.homeworkId) {
    message.warning('这个任务的数据不完整，无法编辑。刷新后重试。');
    return;
  }
  // 带上班级名称：公共课放弃认领后，任务原来的班级不在可选班级里，下拉仍显示班级名称
  taskCreateModalApi
    .setData({
      claimMode: claimMode.value,
      className: row.className,
      id: Number(row.homeworkId),
    })
    .open();
}

/** 任务行的修改类操作：已封存的只能恢复、删除；未封存的能编辑、封存、复制、删除 */
function taskRowActions(row: EduCourseManageApi.CourseTask) {
  return getCourseTaskRowActions(row, canManageCourseTask(row));
}

/** 封存任务（接口仍是原来的 delete）：只读，统计照常计入，可以恢复 */
async function handleSealTask(task: Record<string, any>) {
  const row = task as EduCourseManageApi.CourseTask;
  if (!row.homeworkId) {
    message.warning('这个任务的数据不完整，无法封存。刷新后重试。');
    return;
  }
  await confirm({
    cancelText: '返回',
    confirmText: '封存',
    content:
      '封存后任务只读，不能再编辑或批改，学生的待办里不再出现；历史数据仍计入统计，可随时恢复。',
    icon: 'warning',
    title: `封存任务「${row.homeworkName || '-'}」？`,
  });
  await deleteHomework(row.homeworkId);
  message.success(`已封存「${row.homeworkName || '-'}」`);
  await loadDetail();
}

async function handleRestoreTask(task: Record<string, any>) {
  const row = task as EduCourseManageApi.CourseTask;
  if (!row.homeworkId) {
    message.warning('这个任务的数据不完整，无法恢复。刷新后重试。');
    return;
  }
  await confirm({
    cancelText: '返回',
    confirmText: '恢复',
    content: '恢复后可以继续编辑、批改，学生重新可见。',
    icon: 'question',
    title: `恢复任务「${row.homeworkName || '-'}」？`,
  });
  await restoreHomework(row.homeworkId);
  message.success(`已恢复「${row.homeworkName || '-'}」`);
  await loadDetail();
}

/** 复制：打开任务表单，预先填好原任务内容，老师选班级和截止时间后发布 */
function handleCopyTask(task: Record<string, any>) {
  const row = task as EduCourseManageApi.CourseTask;
  if (!row.homeworkId) {
    message.warning('这个任务的数据不完整，无法复制。刷新后重试。');
    return;
  }
  taskCreateModalApi
    .setData({ scene: 'copy', sourceHomeworkId: Number(row.homeworkId) })
    .open();
}

/** 删除：先查这个任务有多少提交，按影响写确认框；删除后不能恢复，统计不再计入 */
async function handleRemoveTask(task: Record<string, any>) {
  const row = task as EduCourseManageApi.CourseTask;
  if (!row.homeworkId) {
    message.warning('这个任务的数据不完整，无法删除。刷新后重试。');
    return;
  }
  const homeworkId = Number(row.homeworkId);
  const impact = await getHomeworkRemoveImpact([homeworkId]);
  const confirmCopy = buildRemoveConfirm(impact, row.homeworkName);
  Modal.confirm({
    cancelText: REMOVE_CANCEL_TEXT,
    content: confirmCopy.content,
    okButtonProps: { danger: confirmCopy.danger },
    okText: REMOVE_OK_TEXT,
    title: confirmCopy.title,
    async onOk() {
      await removeHomework(homeworkId);
      message.success(`已删除「${row.homeworkName || '-'}」`);
      await loadDetail();
    },
  });
}

/** 任务筛选没有结果时：清掉负责人，并显示已封存的任务 */
function handleClearTaskFilter() {
  taskFilter.value = { ownerKey: undefined, showSealed: true };
}

async function handleRefreshTaskSheet() {
  await loadDetail();
  taskGridRef.value?.recalculate?.();
}

async function handleZoomTaskSheet() {
  await taskGridRef.value?.zoom?.();
}

async function handleTaskSheetCustom() {
  await taskGridRef.value?.handleCustom?.();
}

function handleOpenCourseEdit() {
  const p = profile.value;
  courseEditForm.value = {
    courseName: p.courseName,
    trainingCategory: p.trainingCategory,
    courseIntroduction: p.courseIntroduction,
  };
  courseEditModalOpen.value = true;
}

async function handleSaveCourseEdit() {
  const name = courseEditForm.value.courseName.trim();
  if (!name) {
    message.warning('课程名称不能为空');
    return;
  }
  const latest = await getCourse(profile.value.courseId);
  // 封面沿用原值：只能在课程表单中上传图片或选择默认封面，不再允许手填地址
  await updateCourse({
    ...latest,
    courseName: name,
    courseIntroduction: courseEditForm.value.courseIntroduction.trim(),
  });
  courseEditModalOpen.value = false;
  message.success('课程信息已修改');
  await loadDetail();
}

async function handleDeleteCourse() {
  await confirm({
    cancelText: '返回',
    confirmText: ARCHIVE_COPY.archive,
    content:
      '封存后课程只读，不能再布置任务；历史数据仍计入统计，可在课程列表打开「显示已封存」后恢复。',
    icon: 'warning',
    title: `封存课程「${profile.value.courseName}」？`,
  });
  await deleteCourse(profile.value.courseId);
  message.success('课程已封存');
  router.back();
}

/** 公共课认领班级：只提交可认领的班级，成功后立即重新读取详情 */
async function handleClaimClass() {
  const semester = selectedBindSemester.value;
  if (!semester) {
    message.warning('请先选择学期');
    return;
  }
  const claimableIdSet = new Set(
    claimableOptions.value.map((item) => Number(item.value)),
  );
  const selectedClassIds = [
    ...new Set(
      classForm.value.classIds
        .map(Number)
        .filter((id) => claimableIdSet.has(id)),
    ),
  ];
  if (selectedClassIds.length === 0) {
    message.warning('所选班级已被认领，请重新选择');
    return;
  }
  const semesterName = semester.semesterName;
  try {
    await claimClassCourse({
      courseId: profile.value.courseId,
      classIds: selectedClassIds,
      semesterName,
    });
  } catch {
    // 有班级刚被别人认领时整体失败，全局已提示原因；刷新列表，让被认领的班级灰显
    await loadClaimOptions(semesterName);
    classForm.value.classIds = classForm.value.classIds.filter((id) =>
      claimableOptions.value.some((item) => item.value === Number(id)),
    );
    return;
  }
  classModalOpen.value = false;
  message.success('班级已认领');
  await loadDetail();
  if (
    hasHiddenAddedClasses(detailData.value?.classes || [], classFilter.value, {
      classIds: selectedClassIds,
      semesterName,
    })
  ) {
    classFilter.value = clearedClassFilter();
    message.info('新认领的班级不在当前筛选里，已改为显示全部班级');
  }
}

async function handleAddClass() {
  if (!profile.value.courseId) {
    return;
  }
  if (claimMode.value) {
    await handleClaimClass();
    return;
  }
  if (selectableClassOptions.value.length === 0) {
    message.warning('没有可添加的班级');
    return;
  }
  if (classForm.value.classIds.length === 0) {
    message.warning('请选择班级');
    return;
  }
  const semester = selectedBindSemester.value;
  if (!semester) {
    message.warning('请先选择学期');
    return;
  }
  const semesterName = semester.semesterName;
  const semesterStartDate = semester.semesterStartDate;
  const semesterEndDate = semester.semesterEndDate;
  const selectableIdSet = new Set(
    selectableClassOptions.value.map((item) => Number(item.value)),
  );
  const selectedClassIds = [
    ...new Set(
      classForm.value.classIds
        .map(Number)
        .filter((id) => selectableIdSet.has(id)),
    ),
  ];
  if (selectedClassIds.length === 0) {
    message.warning('所选班级不可用，请重新选择');
    return;
  }
  const duplicatedClassIds = classForm.value.classIds.filter(
    (id) => !selectableIdSet.has(Number(id)),
  );
  if (duplicatedClassIds.length > 0) {
    message.warning('已跳过已绑定的班级，只添加未绑定的班级');
  }
  await bindClassCourse({
    courseId: profile.value.courseId,
    classIds: selectedClassIds,
    semesterName,
    semesterStartDate,
    semesterEndDate,
  });
  classModalOpen.value = false;
  message.success('班级已添加');
  // 下拉浮层挂在弹窗内部（getClassModalPopupContainer），随弹窗一起关闭、销毁，这里直接重新读取不会留下浮层
  await loadDetail();
  // 新班级不在当前筛选（默认只看进行中）里时，改为显示全部班级，免得添加后列表里看不到
  if (
    hasHiddenAddedClasses(detailData.value?.classes || [], classFilter.value, {
      classIds: selectedClassIds,
      semesterName,
    })
  ) {
    classFilter.value = clearedClassFilter();
    message.info('新添加的班级不在当前筛选里，已改为显示全部班级');
  }
}

/** 下拉浮层挂在下拉框的父元素里，随弹窗一起销毁 */
function getClassModalPopupContainer(trigger: HTMLElement) {
  return trigger.parentElement ?? document.body;
}

/** 弹窗完全关闭（动画结束）后重置表单。afterClose 在 antd 4 里是 prop，要用 :after-close 传入 */
function handleClassModalAfterClose() {
  resetClassForm();
}

function handleOpenTaskDetail(task: Record<string, any>) {
  activeTask.value = task as EduCourseManageApi.CourseTask;
  taskDrawerOpen.value = true;
}

function handleOpenHomeworkDetail(task: Record<string, any>) {
  const row = task as EduCourseManageApi.CourseTask;
  if (!row.homeworkId) {
    message.warning('这个任务的数据不完整，无法查看详情。刷新后重试。');
    return;
  }
  router.push({
    path: `/capacity/homework_manager/homework/detail/${row.homeworkId}`,
  });
}

function handleOpenHomeworkCorrect(task: Record<string, any>) {
  const row = task as EduCourseManageApi.CourseTask;
  if (!row.homeworkId) {
    message.warning('这个任务的数据不完整，无法批改。刷新后重试。');
    return;
  }
  router.push({
    path: `/capacity/homework_manager/homework/correct/${row.homeworkId}`,
  });
}

function handleOpenClassDetail(row: Record<string, any>) {
  const classRow = row as EduCourseManageApi.CourseClass;
  if (classRow.archived) return;
  if (!classRow.classCourseId) {
    message.warning('这个班级的数据不完整，无法查看详情。刷新后重试。');
    return;
  }
  router.push({
    path: `/capacity/course/class/detail/${classRow.classCourseId}`,
  });
}

async function handleArchiveClass(row: EduCourseManageApi.CourseClass) {
  await unbindClassCourse({ id: row.classCourseId });
  message.success(archivedMessage(row.className));
  await loadDetail();
}

/** 放弃自己的认领，或（管理员、审批人）清除别人的认领；已布置的任务不动 */
async function handleReleaseClass(
  row: EduCourseManageApi.CourseClass,
  mine: boolean,
) {
  await releaseClassCourse(row.classCourseId);
  message.success(
    mine
      ? `已放弃认领「${row.className}」`
      : `已清除「${row.className}」的认领`,
  );
  await loadDetail();
}

/** 班级行上的修改类操作（专属课程按原来的判断；公共课按 canOperate 与认领人） */
function classRowActions(row: EduCourseManageApi.CourseClass) {
  return getClassRowActions(row, {
    canClearOthersClaim: canClearOthersClaim.value,
    canManageClasses: canManageClasses.value,
    claimMode: claimMode.value,
    currentUserId: currentUserId.value,
    hasBindPermission: hasBindPermission.value,
  });
}

async function handleRestoreClass(row: EduCourseManageApi.CourseClass) {
  await restoreClassCourse(row.classCourseId);
  await loadDetail();
  const current = detailData.value?.classes.find(
    (item) => item.classCourseId === row.classCourseId,
  );
  if (current?.archived) {
    message.warning(
      `「${row.className}」已回到本课程，但班级本身还是已封存状态。到「班级管理」里恢复这个班级后才能使用。`,
    );
  } else {
    message.success(restoredMessage(row.className));
  }
}

function handleArchivedClassSwitch() {
  classFilter.value.status = 'all';
}

function handleResetClassFilter() {
  classFilter.value = defaultClassFilter();
}

/** 筛选没有结果时：清掉关键词、学期、状态与负责人，显示全部班级 */
function handleClearClassFilter() {
  classFilter.value = clearedClassFilter();
}

onMounted(async () => {
  await Promise.all([loadOptions(), loadDetail()]);
});

// 换了学期等导致选中的负责人不在选项里时，清掉负责人筛选，免得列表莫名为空
watch(classOwnerOptions, (options) => {
  const kept = keepOwnerKey(classFilter.value.ownerKey, options);
  if (kept !== classFilter.value.ownerKey) {
    classFilter.value.ownerKey = kept;
  }
});
watch(taskOwnerOptions, (options) => {
  const kept = keepOwnerKey(taskFilter.value.ownerKey, options);
  if (kept !== taskFilter.value.ownerKey) {
    taskFilter.value.ownerKey = kept;
  }
});

watch(activeSheet, async (value) => {
  await nextTick();
  if (value === 'classes') {
    classGridRef.value?.recalculate?.();
  } else if (value === 'tasks') {
    taskGridRef.value?.recalculate?.();
  }
});
</script>

<template>
  <Page :title="pageTitle" auto-content-height>
    <template #description>
      <p class="course-detail__meta">{{ pageMeta }}</p>
    </template>
    <template #extra>
      <Button v-if="canUpdateCourse" @click="handleOpenCourseEdit">修改</Button>
      <Button v-if="canDeleteCourse" @click="handleDeleteCourse">
        {{ ARCHIVE_COPY.archive }}
      </Button>
      <Button @click="router.back()">返回</Button>
    </template>

    <TaskCreateModal @success="loadDetail" />
    <QuestionBankTaskModal
      v-model:open="questionBankTaskModalOpen"
      :class-relations="courseClassRelations"
      :course-id="profile.courseId"
      :indicator-first-id="courseFirstIndicatorId"
      @success="loadDetail"
    />

    <div v-if="!detailData" class="course-detail__state">
      <Spin v-if="loading" tip="正在读取课程信息…" />
      <EmptyState
        v-else
        kind="blocked"
        title="没有读取到课程信息"
        description="可能是网络中断或课程已封存。可以重新读取，或返回课程列表。"
      >
        <template #action>
          <Button @click="loadDetail">重新读取</Button>
        </template>
      </EmptyState>
    </div>

    <Spin v-else :spinning="loading">
      <div class="course-detail">
        <Alert
          v-if="claimViewNotice"
          :message="claimViewNotice"
          show-icon
          type="info"
        />
        <section class="surface-panel course-overview">
          <img
            :alt="`${profile.courseName}的封面`"
            :src="
              getCourseCoverUrl(
                profile.courseImageUrl,
                profile.courseId || profile.courseName,
              )
            "
            class="course-overview__cover"
            @error="
              applyCourseCoverFallback(
                $event,
                profile.courseId || profile.courseName,
              )
            "
          />
          <div class="course-overview__body">
            <h2 class="section-title">课程简介</h2>
            <p v-if="courseIntroduction" class="course-overview__text">
              {{ courseIntroduction }}
            </p>
            <p v-else class="course-overview__text is-empty">
              还没有课程简介。
            </p>
          </div>
        </section>

        <div class="metric-grid">
          <div
            v-for="item in courseMetricCards"
            :key="item.label"
            class="metric-card"
          >
            <div class="metric-card__label">{{ item.label }}</div>
            <div class="metric-card__value">{{ item.value }}</div>
            <div class="metric-card__meta">{{ item.meta }}</div>
          </div>
        </div>

        <Card class="course-detail__sections">
          <Tabs
            v-model:active-key="activeSheet"
            :animated="{ inkBar: true, tabPane: true }"
          >
            <TabPane key="classes" tab="班级">
              <div class="sheet-toolbar course-detail__toolbar">
                <div class="sheet-toolbar__filters">
                  <Input
                    v-model:value="classFilter.keyword"
                    allow-clear
                    class="course-detail__search"
                    placeholder="搜索班级名称"
                  />
                  <Select
                    v-model:value="classFilter.semester"
                    :options="[
                      { label: '全部学期', value: 'all' },
                      ...semesterOptions,
                    ]"
                    class="course-detail__select"
                    placeholder="按学期筛选"
                  />
                  <Select
                    v-model:value="classFilter.status"
                    :options="[
                      { label: '进行中', value: '进行中' },
                      { label: '全部状态', value: 'all' },
                      { label: '未开始', value: '未开始' },
                      { label: '已结束', value: '已结束' },
                    ]"
                    class="course-detail__select course-detail__select--status"
                    placeholder="按状态筛选"
                  />
                  <Select
                    v-model:value="classFilter.ownerKey"
                    :options="classOwnerOptions"
                    allow-clear
                    class="course-detail__select course-detail__select--owner"
                    option-filter-prop="label"
                    placeholder="负责人"
                    show-search
                  />
                  <Button @click="handleResetClassFilter">重置</Button>
                  <label class="course-detail__switch">
                    <Switch
                      v-model:checked="includeArchivedClasses"
                      size="small"
                      @change="handleArchivedClassSwitch"
                    />
                    <span>{{ ARCHIVE_COPY.showArchived }}</span>
                  </label>
                </div>
                <div class="sheet-toolbar__actions">
                  <span class="course-detail__count">
                    共 {{ filteredClassRows.length }} 个班级
                  </span>
                  <Button
                    v-if="showClassModalButton"
                    type="primary"
                    @click="handleOpenClassModal"
                  >
                    {{ classModalCopy.title }}
                  </Button>
                </div>
              </div>

              <DetailSheetGrid
                v-if="filteredClassRows.length > 0"
                ref="classGridRef"
                :columns="classColumns"
                :data="filteredClassRows"
                :pager-enabled="true"
                :page-size="8"
                row-key="classCourseId"
              >
                <template #classNameCell="{ row }">
                  <Button
                    v-if="!row.archived"
                    class="px-0"
                    type="link"
                    @click="handleOpenClassDetail(row)"
                  >
                    {{ row.className || '-' }}
                  </Button>
                  <span v-else>{{ row.className || '-' }}</span>
                </template>
                <template #classOwnerCell="{ row }">
                  <span
                    :class="{
                      'course-detail__muted': isClassOwnerUnclaimed(
                        row,
                        claimMode,
                      ),
                    }"
                  >
                    {{ getClassOwnerLabel(row, claimMode) }}
                  </span>
                </template>
                <template #classStatusCell="{ row }">
                  <StatusTag
                    v-bind="getClassCourseStatusMeta(row.status, row.archived)"
                  />
                </template>
                <template #classActions="{ row }">
                  <TableAction
                    :actions="[
                      {
                        label: '查看详情',
                        type: 'link',
                        icon: ACTION_ICON.VIEW,
                        ifShow: !row.archived,
                        onClick: handleOpenClassDetail.bind(null, row),
                      },
                      {
                        label: '放弃认领',
                        type: 'link',
                        ifShow: classRowActions(row).release,
                        popConfirm: {
                          ...rowConfirm(
                            '放弃认领',
                            '班级',
                            row.className,
                            '放弃后该班级可被其他老师认领；你已布置的任务仍归你查看和批改。',
                          ),
                          confirm: handleReleaseClass.bind(null, row, true),
                        },
                      },
                      {
                        label: '清除认领',
                        type: 'link',
                        ifShow: classRowActions(row).clearClaim,
                        popConfirm: {
                          ...rowConfirm(
                            '清除认领',
                            '班级',
                            row.className,
                            '清除后该班级可被其他老师认领。',
                          ),
                          title: `清除班级「${row.className || '-'}」的认领？`,
                          confirm: handleReleaseClass.bind(null, row, false),
                        },
                      },
                      {
                        label: ARCHIVE_COPY.archive,
                        type: 'link',
                        ifShow: classRowActions(row).archive,
                        popConfirm: {
                          ...rowConfirm(
                            '封存',
                            '班级',
                            row.className,
                            '封存后这个班级在本课程里只读，不能再布置任务；历史数据仍计入统计，可随时恢复。',
                          ),
                          confirm: handleArchiveClass.bind(null, row),
                        },
                      },
                      {
                        label: ARCHIVE_COPY.restore,
                        type: 'link',
                        ifShow: classRowActions(row).restore,
                        popConfirm: {
                          ...rowConfirm(
                            '恢复',
                            '班级',
                            row.className,
                            '恢复后这个班级回到本课程，可以继续布置任务。',
                          ),
                          confirm: handleRestoreClass.bind(null, row),
                        },
                      },
                    ]"
                  />
                </template>
              </DetailSheetGrid>
              <EmptyState
                v-else-if="hasAnyClass"
                kind="filtered"
                title="没有符合条件的班级"
                description="默认只显示进行中的班级。可以清除筛选，或打开「显示已封存」查看。"
              >
                <template #action>
                  <Button @click="handleClearClassFilter">清除筛选</Button>
                </template>
              </EmptyState>
              <EmptyState
                v-else
                :description="classAbsenceCopy.classEmpty"
                title="还没有关联班级"
              />
            </TabPane>

            <TabPane key="tasks" tab="实训任务">
              <div class="sheet-toolbar course-detail__toolbar">
                <div class="sheet-toolbar__filters">
                  <Select
                    v-model:value="taskFilter.ownerKey"
                    :options="taskOwnerOptions"
                    allow-clear
                    class="course-detail__select course-detail__select--owner"
                    option-filter-prop="label"
                    placeholder="负责人"
                    show-search
                  />
                  <label class="course-detail__switch">
                    <Switch
                      v-model:checked="taskFilter.showSealed"
                      size="small"
                    />
                    <span>{{ ARCHIVE_COPY.showArchived }}</span>
                  </label>
                  <span class="course-detail__count">
                    共 {{ filteredTaskRows.length }} 个任务，覆盖
                    {{ classRows.length }} 个班级
                  </span>
                </div>
                <div class="sheet-toolbar__actions">
                  <Tooltip title="刷新">
                    <Button
                      aria-label="刷新任务列表"
                      @click="handleRefreshTaskSheet"
                    >
                      <template #icon>
                        <IconifyIcon icon="lucide:refresh-cw" />
                      </template>
                    </Button>
                  </Tooltip>
                  <Tooltip title="全屏查看">
                    <Button
                      :disabled="filteredTaskRows.length === 0"
                      aria-label="全屏查看任务列表"
                      @click="handleZoomTaskSheet"
                    >
                      <template #icon>
                        <IconifyIcon icon="lucide:maximize-2" />
                      </template>
                    </Button>
                  </Tooltip>
                  <Tooltip title="列设置">
                    <Button
                      :disabled="filteredTaskRows.length === 0"
                      aria-label="设置任务列表的列"
                      @click="handleTaskSheetCustom"
                    >
                      <template #icon>
                        <IconifyIcon icon="lucide:layout-grid" />
                      </template>
                    </Button>
                  </Tooltip>
                  <Button @click="handleOpenQuestionBankTaskModal">
                    AI 新建任务
                  </Button>
                  <Button type="primary" @click="handleOpenTaskModal">
                    新建任务
                  </Button>
                </div>
              </div>
              <DetailSheetGrid
                v-if="filteredTaskRows.length > 0"
                ref="taskGridRef"
                :columns="taskColumns"
                :data="filteredTaskRows"
                :grid-id="`course-detail-task-sheet-${profile.courseId}`"
                :pager-enabled="true"
                :page-size="8"
                row-key="homeworkId"
              >
                <template #taskNameCell="{ row }">
                  <Button
                    class="px-0"
                    type="link"
                    @click="handleOpenHomeworkDetail(row)"
                  >
                    {{ row.homeworkName || '-' }}
                  </Button>
                </template>
                <template #taskStatusCell="{ row }">
                  <StatusTag v-bind="getCourseTaskStatusMeta(row)" />
                </template>
                <template #taskActions="{ row }">
                  <TableAction
                    :actions="[
                      {
                        label: '编辑',
                        type: 'link',
                        icon: ACTION_ICON.EDIT,
                        auth: ['edu:homework:update'],
                        ifShow: taskRowActions(row).edit,
                        onClick: handleOpenTaskEdit.bind(null, row),
                      },
                      {
                        label: getCourseTaskGradeLabel(row),
                        type: 'link',
                        auth: ['edu:homework-record:correct'],
                        onClick: handleOpenHomeworkCorrect.bind(null, row),
                      },
                      {
                        label: '批改情况',
                        type: 'link',
                        onClick: handleOpenTaskDetail.bind(null, row),
                      },
                    ]"
                    :drop-down-actions="[
                      {
                        label: '任务详情',
                        type: 'link',
                        icon: ACTION_ICON.VIEW,
                        onClick: handleOpenHomeworkDetail.bind(null, row),
                      },
                      {
                        label: '复制',
                        type: 'link',
                        icon: ACTION_ICON.COPY,
                        auth: ['edu:homework:copy'],
                        ifShow: taskRowActions(row).copy,
                        onClick: handleCopyTask.bind(null, row),
                      },
                      {
                        label: ARCHIVE_COPY.archive,
                        type: 'link',
                        icon: ACTION_ICON.ARCHIVE,
                        auth: ['edu:homework:delete'],
                        ifShow: taskRowActions(row).seal,
                        onClick: handleSealTask.bind(null, row),
                      },
                      {
                        label: ARCHIVE_COPY.restore,
                        type: 'link',
                        icon: ACTION_ICON.REFRESH,
                        auth: ['edu:homework:delete'],
                        ifShow: taskRowActions(row).restore,
                        onClick: handleRestoreTask.bind(null, row),
                      },
                      {
                        label: '删除',
                        type: 'link',
                        danger: true,
                        icon: ACTION_ICON.DELETE,
                        auth: ['edu:homework:delete'],
                        ifShow: taskRowActions(row).remove,
                        onClick: handleRemoveTask.bind(null, row),
                      },
                    ]"
                  />
                </template>
              </DetailSheetGrid>
              <EmptyState
                v-else-if="taskRows.length > 0"
                kind="filtered"
                title="没有符合条件的任务"
                description="已封存的任务默认不显示。可以清除筛选，或打开「显示已封存」查看。"
              >
                <template #action>
                  <Button @click="handleClearTaskFilter">清除筛选</Button>
                </template>
              </EmptyState>
              <EmptyState
                v-else-if="classRows.length > 0"
                title="还没有实训任务"
                description="新建任务后，可以在这里跟进各班的提交与批改进度。"
              />
              <EmptyState
                v-else
                kind="blocked"
                :description="classAbsenceCopy.taskBlocked"
                title="还不能发布实训任务"
              />
            </TabPane>

            <TabPane key="resources" tab="资源">
              <CourseResourceWorkspace
                :course-id="profile.courseId"
                :editable="true"
                :resources="resourceRows"
                empty-description="还没有课程资源"
                @updated="loadDetail"
              />
            </TabPane>
          </Tabs>
        </Card>
      </div>
    </Spin>

    <Modal
      v-model:open="classModalOpen"
      :ok-button-props="{ disabled: !canSubmitAddClass }"
      :ok-text="classModalCopy.okText"
      :title="classModalCopy.title"
      destroy-on-close
      cancel-text="取消"
      :after-close="handleClassModalAfterClose"
      @ok="handleAddClass"
    >
      <div class="class-modal">
        <Alert
          v-if="bindSemesterOptions.length === 0"
          :message="`还没有可选的学期：先在「学期管理」里设置当前学期，再${classModalCopy.noun}班级。`"
          show-icon
          type="warning"
        />
        <div v-else class="class-modal__semester">
          <span class="class-modal__label">学期</span>
          <Select
            v-model:value="classForm.semesterName"
            :get-popup-container="getClassModalPopupContainer"
            :options="bindSemesterSelectOptions"
            class="w-full"
            placeholder="选择学期"
            @change="handleBindSemesterChange"
          />
        </div>
        <div class="class-modal__head">
          <span class="class-modal__label">选择班级</span>
          <span class="class-modal__summary">{{ classSelectSummary }}</span>
          <Space :size="4" class="class-modal__tools">
            <Button
              :disabled="modalSelectableOptions.length === 0"
              class="px-0"
              size="small"
              type="link"
              @click="handleSelectAllClasses"
            >
              全选可选
            </Button>
            <Button
              :disabled="classForm.classIds.length === 0"
              class="px-0"
              size="small"
              type="link"
              @click="handleClearSelectedClasses"
            >
              清空
            </Button>
          </Space>
        </div>
        <template v-if="claimMode">
          <Spin v-if="claimOptionsLoading" />
          <EmptyState
            v-else-if="claimOptions.length === 0"
            compact
            description="还没有可以认领的班级。"
            title="没有班级"
          />
          <template v-else>
            <p v-if="claimableOptions.length === 0" class="class-modal__hint">
              所选学期的班级都已被认领，可以换一个学期。
            </p>
            <!-- 可认领的在上面；已认领、已被别人认领的排在下面，灰显不可选 -->
            <Select
              v-model:value="classForm.classIds"
              :get-popup-container="getClassModalPopupContainer"
              :options="claimOptions"
              allow-clear
              class="w-full"
              max-tag-count="responsive"
              mode="multiple"
              option-filter-prop="label"
              placeholder="请选择要认领的班级（支持搜索）"
              show-search
            />
            <p class="class-modal__hint">
              灰显的班级不能选；你认领后封存的班级，在班级列表打开「{{
                ARCHIVE_COPY.showArchived
              }}」后恢复。
            </p>
          </template>
        </template>
        <EmptyState
          v-else-if="selectableClassOptions.length === 0"
          compact
          description="已有班级都已添加到本课程。"
          title="没有可添加的班级"
        />
        <Select
          v-else
          v-model:value="classForm.classIds"
          :get-popup-container="getClassModalPopupContainer"
          :options="selectableClassOptions"
          allow-clear
          class="w-full"
          max-tag-count="responsive"
          mode="multiple"
          option-filter-prop="label"
          placeholder="请选择班级（支持搜索）"
          show-search
        />
      </div>
    </Modal>

    <Modal
      v-model:open="courseEditModalOpen"
      cancel-text="取消"
      ok-text="保存"
      title="修改课程"
      @ok="handleSaveCourseEdit"
    >
      <div class="course-edit">
        <label class="course-edit__field">
          <span class="course-edit__label">课程名称</span>
          <Input v-model:value="courseEditForm.courseName" />
        </label>
        <label class="course-edit__field">
          <span class="course-edit__label">课程简介</span>
          <Input.TextArea
            v-model:value="courseEditForm.courseIntroduction"
            :rows="5"
          />
        </label>
      </div>
    </Modal>

    <Drawer v-model:open="taskDrawerOpen" :width="520" title="任务批改情况">
      <div v-if="activeTask" class="task-drawer">
        <dl class="task-drawer__fields">
          <dt>任务名称</dt>
          <dd>{{ activeTask.homeworkName || '-' }}</dd>
          <dt>所属班级</dt>
          <dd>{{ activeTask.className || '-' }}</dd>
          <dt>学期</dt>
          <dd>{{ activeTask.semesterName || '-' }}</dd>
          <dt>截止时间</dt>
          <dd>{{ activeTask.deadline || '-' }}</dd>
          <dt>负责人</dt>
          <dd>{{ getTaskOwnerLabel(activeTask) }}</dd>
          <dt>状态</dt>
          <dd><StatusTag v-bind="getCourseTaskStatusMeta(activeTask)" /></dd>
        </dl>

        <div class="stat-strip">
          <div class="stat-strip__item">
            <div class="stat-strip__label">提交人数</div>
            <div class="stat-strip__value">
              {{ activeTask.submittedCount || 0 }}
            </div>
            <div class="stat-strip__meta">
              共 {{ activeTask.studentCount || 0 }} 名学生
            </div>
          </div>
          <div class="stat-strip__item">
            <div class="stat-strip__label">已批改</div>
            <div class="stat-strip__value">
              {{ activeTask.correctedCount || 0 }}
            </div>
            <div class="stat-strip__meta">已完成批改的提交</div>
          </div>
          <div class="stat-strip__item">
            <div class="stat-strip__label">平均分</div>
            <div class="stat-strip__value">
              {{ Number(activeTask.avgScore || 0).toFixed(1) }}
            </div>
            <div class="stat-strip__meta">按已批改的提交计算</div>
          </div>
        </div>
      </div>
      <EmptyState
        v-else
        compact
        description="在实训任务列表里点「批改情况」查看。"
        title="没有选中任务"
      />
    </Drawer>
  </Page>
</template>

<style scoped>
.course-detail {
  display: flex;
  flex-direction: column;
  gap: var(--nus-section-gap);
}

.course-detail__meta {
  /* 页头说明：与 Page 自带的说明同一字号与颜色；读取完成前留出一行，页头高度不跳 */
  min-height: var(--nus-lh-dense);
  margin: 2px 0 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.course-detail__state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}

.course-overview {
  /* 课程简介：封面缩略图与文字并排，不再做大图英雄区 */
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: var(--nus-space-5);
  align-items: start;
  padding: var(--nus-card-padding);
}

.course-overview__cover {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius);
}

.course-overview__body {
  min-width: 0;
}

.course-overview__text {
  margin: var(--nus-space-2) 0 0;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text-secondary);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.course-overview__text.is-empty {
  color: var(--nus-text-tertiary);
  white-space: normal;
}

.course-detail__toolbar {
  margin-bottom: var(--nus-space-4);
}

.course-detail__search {
  width: 220px;
}

.course-detail__select {
  width: 180px;
}

.course-detail__select--status {
  width: 120px;
}

.course-detail__select--owner {
  width: 140px;
}

.course-detail__muted {
  color: var(--nus-text-tertiary);
}

.course-detail__switch {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: center;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  cursor: pointer;
}

.course-detail__count {
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.class-modal {
  /* 添加班级对话框 */
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
}

.class-modal__semester {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-1);
}

.class-modal__head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  align-items: center;
}

.class-modal__label {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text);
}

.class-modal__summary {
  font-size: var(--nus-fs-caption);
  font-variant-numeric: tabular-nums;
  color: var(--nus-text-tertiary);
}

.class-modal__tools {
  margin-left: auto;
}

.class-modal__hint {
  margin: 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.course-edit {
  /* 修改课程对话框 */
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-4);
}

.course-edit__field {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-1);
}

.course-edit__label {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.task-drawer {
  /* 任务批改情况抽屉：字段用两列列表，数字放进一块凹陷底，用分隔线隔开 */
  display: flex;
  flex-direction: column;
  gap: var(--nus-section-gap);
}

.task-drawer__fields {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: var(--nus-space-3) var(--nus-space-4);
  align-items: baseline;
  margin: 0;
}

.task-drawer__fields dt {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.task-drawer__fields dd {
  margin: 0;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  overflow-wrap: anywhere;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius-lg);
}

.stat-strip__item {
  min-width: 0;
  padding: var(--nus-space-3) var(--nus-space-4);
}

.stat-strip__item + .stat-strip__item {
  border-left: 1px solid var(--nus-border-subtle);
}

.stat-strip__label {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.stat-strip__value {
  margin-top: var(--nus-space-1);
  font-size: var(--nus-fs-display);
  font-weight: var(--nus-fw-semibold);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-display);
  color: var(--nus-text);
}

.stat-strip__meta {
  margin-top: 2px;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

@media (width <= 768px) {
  .course-overview {
    grid-template-columns: minmax(0, 1fr);
  }

  .course-overview__cover {
    max-width: 320px;
  }
}
</style>
