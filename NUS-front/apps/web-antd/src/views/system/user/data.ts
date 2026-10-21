import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemUserApi } from '#/api/system/user';

import { useAccess } from '@vben/access';
import { CommonStatusEnum, DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { $t } from '@vben/locales';
import { handleTree } from '@vben/utils';

import { z } from '#/adapter/form';
import { getSimpleClassList } from '#/api/edu/class';
import { getDeptList } from '#/api/system/dept';
import { getSimpleRoleList } from '#/api/system/role';
import { getRangePickerDefaultProps } from '#/utils';

import { INITIAL_PASSWORD_PLACEHOLDER } from './form-copy';

interface DeptTreeNode {
  [key: string]: any;
  children?: DeptTreeNode[];
}

const TEACHER_USER_TYPE = 1;
const STUDENT_USER_TYPE = 2;
const TEACHER_VISIBLE_DEPT_LEVEL = 2;
const TEACHER_TITLE_DICT = 'edu_teacher_title';
const currentYear = new Date().getFullYear();
const gradeYearOptions = Array.from({ length: 7 }, (_, index) => {
  const year = currentYear - index;
  return {
    label: `${year} 级`,
    value: year,
  };
});

function resolveCurrentUserType(
  values: Record<string, any>,
  fixedUserType?: number,
) {
  if (fixedUserType !== undefined && fixedUserType !== null) {
    return fixedUserType;
  }
  return values?.userType;
}

function resolveUserTypeTriggerFields(fixedUserType?: number) {
  return fixedUserType === undefined ? ['userType'] : [''];
}

function trimDeptTreeByLevel(
  nodes: DeptTreeNode[] = [],
  maxLevel: number,
  level = 1,
): DeptTreeNode[] {
  return nodes.map((node) => {
    const nextNode: DeptTreeNode = { ...node };
    if (level >= maxLevel) {
      delete nextNode.children;
      return nextNode;
    }
    if (nextNode.children?.length) {
      nextNode.children = trimDeptTreeByLevel(
        nextNode.children,
        maxLevel,
        level + 1,
      );
    }
    return nextNode;
  });
}

async function getDeptTreeByUserType(userType?: number) {
  const data = await getDeptList();
  const deptTree = handleTree(data) as DeptTreeNode[];
  if (userType === TEACHER_USER_TYPE) {
    return trimDeptTreeByLevel(deptTree, TEACHER_VISIBLE_DEPT_LEVEL);
  }
  return deptTree;
}

/** 新增/修改的表单 */
export function useFormSchema(options?: {
  accountLabel?: string;
  fixedUserType?: number;
}): VbenFormSchema[] {
  const accountLabel = options?.accountLabel || '用户账号';
  const fixedUserType = options?.fixedUserType;
  const userTypeTriggerFields = resolveUserTypeTriggerFields(fixedUserType);
  return [
    {
      component: 'Input',
      fieldName: 'id',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'username',
      label: accountLabel,
      component: 'Input',
      componentProps: {
        placeholder: `请输入${accountLabel}`,
      },
      rules: 'required',
    },
    {
      label: '用户密码',
      fieldName: 'password',
      component: 'InputPassword',
      componentProps: {
        placeholder: INITIAL_PASSWORD_PLACEHOLDER,
      },
      rules: 'required',
      dependencies: {
        triggerFields: ['id'],
        show: (values) => !values.id,
      },
    },
    {
      fieldName: 'nickname',
      label: '用户名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入用户名称',
      },
      rules: 'required',
    },
    {
      fieldName: 'userType',
      label: '用户类型',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.SYSTEM_USER_TYPE, 'number'),
        placeholder: '请选择用户类型',
      },
      rules: 'selectRequired',
    },
    {
      fieldName: 'profile.professionalTitle',
      label: '职称',
      component: 'Select',
      componentProps: {
        options: getDictOptions(TEACHER_TITLE_DICT, 'number'),
        placeholder: '请选择职称',
      },
      dependencies: {
        triggerFields: userTypeTriggerFields,
        show: (values) =>
          resolveCurrentUserType(values, fixedUserType) === TEACHER_USER_TYPE,
      },
    },
    {
      fieldName: 'deptId',
      label: '归属组织',
      component: 'ApiTreeSelect',
      componentProps: {
        api: async (params?: { userType?: number }) =>
          await getDeptTreeByUserType(params?.userType),
        params: {},
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        placeholder: '请选择归属组织',
        treeDefaultExpandAll: true,
      },
      dependencies: {
        triggerFields: userTypeTriggerFields,
        rules: (values) =>
          resolveCurrentUserType(values, fixedUserType) === STUDENT_USER_TYPE
            ? z.number({ message: '请选择归属组织' })
            : z.number().optional(),
        componentProps: async (values) => ({
          params: {
            userType: resolveCurrentUserType(values, fixedUserType),
          },
        }),
      },
    },
    {
      fieldName: 'profile.gradeYear',
      label: '所属年级',
      component: 'Select',
      componentProps: {
        options: gradeYearOptions,
        placeholder: '请选择所属年级',
        allowClear: true,
      },
      dependencies: {
        triggerFields: userTypeTriggerFields,
        rules: (values) =>
          resolveCurrentUserType(values, fixedUserType) === STUDENT_USER_TYPE
            ? z.number({ message: '请选择所属年级' })
            : z.number().optional(),
        show: (values) =>
          resolveCurrentUserType(values, fixedUserType) === STUDENT_USER_TYPE,
      },
    },
    {
      fieldName: 'profile.classIds',
      label: '班级',
      component: 'ApiSelect',
      componentProps: {
        api: getSimpleClassList,
        params: {},
        labelField: 'className',
        valueField: 'id',
        mode: 'multiple',
        placeholder: '请选择班级',
      },
      dependencies: {
        triggerFields: [
          ...userTypeTriggerFields,
          'profile.gradeYear',
          'deptId',
        ],
        show: (values) =>
          resolveCurrentUserType(values, fixedUserType) === STUDENT_USER_TYPE,
        componentProps: (values) => ({
          params: {
            deptId: values?.deptId,
            gradeYear: values?.profile?.gradeYear,
          },
          disabled: !values?.deptId,
        }),
      },
    },
    {
      fieldName: 'roleIds',
      label: '角色',
      component: 'ApiSelect',
      componentProps: {
        api: getSimpleRoleList,
        labelField: 'name',
        valueField: 'id',
        mode: 'multiple',
        placeholder: '请选择角色',
      },
    },
    {
      fieldName: 'email',
      label: '邮箱',
      component: 'Input',
      rules: z.string().email('邮箱格式不正确').or(z.literal('')).optional(),
      componentProps: {
        placeholder: '请输入邮箱',
      },
    },
    {
      fieldName: 'mobile',
      label: '手机号码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入手机号码',
      },
    },
    {
      fieldName: 'sex',
      label: '用户性别',
      component: 'RadioGroup',
      componentProps: {
        options: getDictOptions(DICT_TYPE.SYSTEM_USER_SEX, 'number'),
        buttonStyle: 'solid',
        optionType: 'button',
      },
      rules: z.number().default(1),
    },
    {
      fieldName: 'status',
      label: '用户状态',
      component: 'RadioGroup',
      componentProps: {
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
        buttonStyle: 'solid',
        optionType: 'button',
      },
      rules: z.number().default(CommonStatusEnum.ENABLE),
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入备注',
      },
    },
  ];
}

/** 重置密码的表单 */
export function useResetPasswordFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'id',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: '请输入新密码',
      },
      dependencies: {
        rules(values) {
          return z
            .string({ message: '请输入新密码' })
            .min(5, '密码长度不能少于 5 个字符')
            .max(20, '密码长度不能超过 20 个字符')
            .refine(
              (value) => value !== values.oldPassword,
              '新旧密码不能相同',
            );
        },
        triggerFields: ['newPassword', 'oldPassword'],
      },
      fieldName: 'newPassword',
      label: '新密码',
      rules: 'required',
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: $t('authentication.confirmPassword'),
      },
      dependencies: {
        rules(values) {
          return z
            .string({ message: '请输入确认密码' })
            .min(5, '密码长度不能少于 5 个字符')
            .max(20, '密码长度不能超过 20 个字符')
            .refine(
              (value) => value === values.newPassword,
              '新密码和确认密码不一致',
            );
        },
        triggerFields: ['newPassword', 'confirmPassword'],
      },
      fieldName: 'confirmPassword',
      label: '确认密码',
      rules: 'required',
    },
  ];
}

/** 分配角色的表单 */
export function useAssignRoleFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'id',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'username',
      label: '用户账号',
      component: 'Input',
      componentProps: {
        disabled: true,
      },
    },
    {
      fieldName: 'nickname',
      label: '用户名称',
      component: 'Input',
      componentProps: {
        disabled: true,
      },
    },
    {
      fieldName: 'roleIds',
      label: '角色',
      component: 'ApiSelect',
      componentProps: {
        api: getSimpleRoleList,
        labelField: 'name',
        valueField: 'id',
        mode: 'multiple',
        placeholder: '请选择角色',
      },
    },
  ];
}

/** 用户导入的表单 */
/** 列表的搜索表单 */
export function useGridFormSchema(options?: {
  accountLabel?: string;
}): VbenFormSchema[] {
  const accountLabel = options?.accountLabel || '用户账号';
  return [
    {
      fieldName: 'username',
      label: accountLabel,
      component: 'Input',
      componentProps: {
        placeholder: `请输入${accountLabel}`,
        allowClear: true,
      },
    },
    {
      fieldName: 'nickname',
      label: '用户名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入用户名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'mobile',
      label: '手机号码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入手机号码',
        allowClear: true,
      },
    },
    {
      fieldName: 'userType',
      label: '用户类型',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.SYSTEM_USER_TYPE, 'number'),
        placeholder: '请选择用户类型',
        allowClear: true,
      },
    },
    {
      fieldName: 'createTime',
      label: '创建时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(
  onStatusChange?: (
    newStatus: number,
    row: SystemUserApi.User,
  ) => PromiseLike<boolean | undefined>,
  options?: {
    accountLabel?: string;
    showClassNames?: boolean;
    showProfessionalTitle?: boolean;
  },
): VxeTableGridOptions['columns'] {
  const accountLabel = options?.accountLabel || '用户账号';
  // 修改状态需要 system:user:update，无权限时禁用开关，避免发起必然被拒的请求
  const { hasAccessByCodes } = useAccess();
  const canUpdateStatus = hasAccessByCodes(['system:user:update']);
  return [
    { type: 'checkbox', width: 40 },
    {
      field: 'id',
      title: '用户编号',
      minWidth: 100,
    },
    {
      field: 'username',
      title: accountLabel,
      minWidth: 120,
    },
    {
      field: 'nickname',
      title: '用户名称',
      minWidth: 120,
    },
    {
      field: 'userType',
      title: '用户类型',
      minWidth: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.SYSTEM_USER_TYPE },
      },
    },
    ...(options?.showProfessionalTitle
      ? [
          {
            field: 'professionalTitle',
            title: '职称',
            minWidth: 120,
            cellRender: {
              name: 'CellDict',
              props: { type: TEACHER_TITLE_DICT },
            },
          },
        ]
      : []),
    ...(options?.showClassNames
      ? [
          {
            field: 'classNames',
            title: '班级',
            minWidth: 220,
          },
        ]
      : []),
    {
      field: 'deptName',
      title: '组织',
      minWidth: 120,
    },
    {
      field: 'mobile',
      title: '手机号码',
      minWidth: 120,
    },
    {
      field: 'email',
      title: '邮箱',
      minWidth: 180,
    },
    {
      field: 'status',
      title: '状态',
      minWidth: 100,
      align: 'center',
      cellRender: {
        attrs: { beforeChange: onStatusChange },
        name: 'CellSwitch',
        props: {
          checkedValue: CommonStatusEnum.ENABLE,
          disabled: !canUpdateStatus,
          unCheckedValue: CommonStatusEnum.DISABLE,
        },
      },
    },
    {
      field: 'createTime',
      title: '创建时间',
      minWidth: 160,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 180,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
