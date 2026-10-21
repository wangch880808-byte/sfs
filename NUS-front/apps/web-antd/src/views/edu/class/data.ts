import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemDeptApi } from '#/api/system/dept';

import { CommonStatusEnum } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { handleTree } from '@vben/utils';

import { z } from '#/adapter/form';
import { getDeptList } from '#/api/system/dept';
import { getRangePickerDefaultProps } from '#/utils';

const EDU_EDUCATION_LEVEL_DICT = 'edu_education_level';

const currentYear = new Date().getFullYear();
export const gradeYearOptions = Array.from({ length: 7 }, (_, index) => {
  const year = currentYear - index;
  return {
    label: `${year} 级`,
    value: year,
  };
});

const schoolingYearOptions = [
  { label: '2 年', value: 2 },
  { label: '3 年', value: 3 },
  { label: '4 年', value: 4 },
  { label: '5 年', value: 5 },
];

let deptTreeCache: null | SystemDeptApi.Dept[] = null;
let deptNameMapCache: Map<number, string> = new Map();
let deptTreeLoading: null | Promise<SystemDeptApi.Dept[]> = null;

function buildDeptNameMap(nodes: SystemDeptApi.Dept[]) {
  const map = new Map<number, string>();
  const walk = (depts: SystemDeptApi.Dept[]) => {
    depts.forEach((dept) => {
      if (dept?.id !== undefined) {
        map.set(Number(dept.id), dept.name);
      }
      if (dept?.children?.length) {
        walk(dept.children);
      }
    });
  };
  walk(nodes);
  return map;
}

export async function getDeptTree() {
  if (deptTreeCache) {
    return deptTreeCache;
  }
  if (deptTreeLoading) {
    return deptTreeLoading;
  }
  deptTreeLoading = (async () => {
    const data = (await getDeptList()) as SystemDeptApi.Dept[];
    const tree = handleTree(data) as SystemDeptApi.Dept[];
    deptTreeCache = tree;
    deptNameMapCache = buildDeptNameMap(tree);
    return tree;
  })().finally(() => {
    deptTreeLoading = null;
  });
  return deptTreeLoading;
}

export async function getDeptNameMap() {
  await getDeptTree();
  return deptNameMapCache;
}

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'className',
      label: '班级名称',
      component: 'Input',
      componentProps: {
        placeholder: '默认自动生成，可点右侧编辑按钮手动修改',
      },
      rules: 'required',
    },
    {
      fieldName: 'gradeYear',
      label: '所属年级',
      component: 'Select',
      componentProps: {
        options: gradeYearOptions,
        placeholder: '请选择所属年级',
      },
      rules: 'selectRequired',
    },
    {
      fieldName: 'deptId',
      label: '归属组织',
      component: 'ApiTreeSelect',
      componentProps: {
        api: getDeptTree,
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        placeholder: '请选择归属组织',
        treeDefaultExpandAll: true,
      },
      rules: 'selectRequired',
    },
    {
      fieldName: 'educationLevel',
      label: '教育层次',
      component: 'Select',
      componentProps: {
        options: getDictOptions(EDU_EDUCATION_LEVEL_DICT, 'number'),
        placeholder: '请选择教育层次',
      },
      rules: 'selectRequired',
    },
    {
      fieldName: 'schoolingYear',
      label: '学制',
      component: 'Select',
      componentProps: {
        options: schoolingYearOptions,
        placeholder: '请选择学制',
      },
      rules: 'selectRequired',
    },
    {
      fieldName: 'status',
      label: '班级状态',
      component: 'Input',
      dependencies: { triggerFields: [''], show: () => false },
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

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'className',
      label: '班级名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入班级名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'gradeYear',
      label: '所属年级',
      component: 'Select',
      componentProps: {
        options: gradeYearOptions,
        placeholder: '请选择所属年级',
        allowClear: true,
      },
    },
    {
      fieldName: 'deptId',
      label: '归属组织',
      component: 'ApiTreeSelect',
      componentProps: {
        api: getDeptTree,
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        placeholder: '请选择归属组织',
        allowClear: true,
        treeDefaultExpandAll: true,
      },
    },
    {
      fieldName: 'educationLevel',
      label: '教育层次',
      component: 'Select',
      componentProps: {
        options: getDictOptions(EDU_EDUCATION_LEVEL_DICT, 'number'),
        placeholder: '请选择教育层次',
        allowClear: true,
      },
    },
    {
      fieldName: 'schoolingYear',
      label: '学制',
      component: 'Select',
      componentProps: {
        options: schoolingYearOptions,
        placeholder: '请选择学制',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '班级状态',
      component: 'Select',
      componentProps: {
        options: [
          { label: '正常', value: 0 },
          { label: '已封存', value: 1 },
        ],
        placeholder: '请选择班级状态',
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

/** 班级列表操作列宽度 */
export const CLASS_ACTION_COLUMN_WIDTH = 210;

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { type: 'checkbox', width: 40 },
    {
      // 组织数据导入按班级编号更新已有班级，所以保留，但只给最小宽度
      field: 'id',
      title: '班级编号',
      minWidth: 80,
    },
    {
      field: 'className',
      title: '班级名称',
      minWidth: 180,
    },
    {
      field: 'gradeYear',
      title: '所属年级',
      minWidth: 100,
    },
    {
      field: 'deptName',
      title: '归属组织',
      minWidth: 120,
    },
    {
      field: 'educationLevel',
      title: '教育层次',
      minWidth: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: EDU_EDUCATION_LEVEL_DICT },
      },
    },
    {
      field: 'schoolingYear',
      title: '学制',
      minWidth: 80,
      formatter: ({ cellValue }) => `${cellValue} 年`,
    },
    {
      field: 'status',
      title: '班级状态',
      minWidth: 100,
      slots: { default: 'status' },
    },
    {
      field: 'createTime',
      title: '创建时间',
      minWidth: 160,
      formatter: 'formatDateTime',
    },
    {
      // 三个小号链接按钮（学生管理、编辑、封存）正好放下；1440 宽时其余列靠横向滚动，固定列左侧有阴影提示
      title: '操作',
      width: CLASS_ACTION_COLUMN_WIDTH,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
