import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { CommonStatusEnum, DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { z } from '#/adapter/form';

const REMOVED_SOCIAL_TYPES = new Set([30, 31, 32, 34]);

function getAvailableSocialOptions() {
  return getDictOptions(DICT_TYPE.SYSTEM_SOCIAL_TYPE, 'number').filter(
    (item) => !REMOVED_SOCIAL_TYPES.has(Number(item.value)),
  );
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
      fieldName: 'name',
      label: '应用名',
      component: 'Input',
      componentProps: {
        placeholder: '请输入应用名',
      },
      rules: 'required',
    },
    {
      fieldName: 'socialType',
      label: '社交平台',
      component: 'Select',
      componentProps: {
        options: getAvailableSocialOptions(),
        placeholder: '请选择社交平台',
      },
      rules: 'required',
    },
    {
      fieldName: 'clientId',
      label: '客户端编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入客户端编号，即平台分配给应用的标识',
      },
      rules: 'required',
    },
    {
      fieldName: 'clientSecret',
      label: '客户端密钥',
      component: 'InputPassword',
      componentProps: {
        autocomplete: 'new-password',
        placeholder: '新建时必填；修改时留空表示保持原密钥',
      },
      dependencies: {
        triggerFields: ['id'],
        rules: (values) =>
          values.id
            ? z.string().optional()
            : z.string().min(1, '客户端密钥不能为空'),
      },
    },
    {
      fieldName: 'config.qrPage',
      label: '小程序页面',
      component: 'Input',
      componentProps: {
        placeholder: 'pages/auth/scan/index',
      },
      rules: z.string().default('pages/auth/scan/index'),
      dependencies: {
        triggerFields: ['socialType'],
        show: (values) => values.socialType === 50,
      },
    },
    {
      fieldName: 'config.envVersion',
      label: '发布版本',
      component: 'Select',
      componentProps: {
        options: [
          { label: '开发版', value: 'develop' },
          { label: '体验版', value: 'trial' },
          { label: '正式版', value: 'release' },
        ],
        placeholder: '请选择生成二维码时打开的小程序版本',
      },
      rules: z.string().default('develop'),
      dependencies: {
        triggerFields: ['socialType'],
        show: (values) => values.socialType === 50,
      },
    },
    {
      fieldName: 'publicKey',
      label: '公钥',
      component: 'Input',
      componentProps: {
        placeholder: '请输入公钥',
      },
      dependencies: {
        triggerFields: ['socialType'],
        show: (values) => values.socialType === 40,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'RadioGroup',
      componentProps: {
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
        buttonStyle: 'solid',
        optionType: 'button',
      },
      rules: z.number().default(CommonStatusEnum.ENABLE),
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '应用名',
      component: 'Input',
      componentProps: {
        placeholder: '请输入应用名',
        allowClear: true,
      },
    },
    {
      fieldName: 'socialType',
      label: '社交平台',
      component: 'Select',
      componentProps: {
        options: getAvailableSocialOptions(),
        placeholder: '请选择社交平台',
        allowClear: true,
      },
    },
    {
      fieldName: 'userType',
      label: '用户类型',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.USER_TYPE, 'number'),
        placeholder: '请选择用户类型',
        allowClear: true,
      },
    },
    {
      fieldName: 'clientId',
      label: '客户端编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入客户端编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
        placeholder: '请选择状态',
        allowClear: true,
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { type: 'checkbox', width: 40 },
    {
      field: 'id',
      title: '编号',
      minWidth: 100,
    },
    {
      field: 'name',
      title: '应用名',
      minWidth: 120,
    },
    {
      field: 'socialType',
      title: '社交平台',
      minWidth: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.SYSTEM_SOCIAL_TYPE },
      },
    },
    {
      field: 'userType',
      title: '用户类型',
      minWidth: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.USER_TYPE },
      },
    },
    {
      field: 'clientId',
      title: '客户端编号',
      minWidth: 180,
    },
    {
      field: 'clientSecretConfigured',
      title: '密钥状态',
      minWidth: 100,
      slots: { default: 'clientSecretConfigured' },
    },
    {
      field: 'status',
      title: '状态',
      minWidth: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.COMMON_STATUS },
      },
    },
    {
      field: 'createTime',
      title: '创建时间',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
