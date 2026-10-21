import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DescriptionItemSchema } from '#/components/description';

import { h } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { Image } from 'ant-design-vue';

import { DictTag } from '#/components/dict-tag';
import { getRangePickerDefaultProps } from '#/utils';

const REMOVED_SOCIAL_TYPES = new Set([30, 31, 32, 34]);

function getAvailableSocialOptions() {
  return getDictOptions(DICT_TYPE.SYSTEM_SOCIAL_TYPE, 'number').filter(
    (item) => !REMOVED_SOCIAL_TYPES.has(Number(item.value)),
  );
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'type',
      label: '社交平台',
      component: 'Select',
      componentProps: {
        options: getAvailableSocialOptions(),
        placeholder: '请选择社交平台',
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
      fieldName: 'openid',
      label: 'OpenID',
      component: 'Input',
      componentProps: {
        placeholder: '请输入 OpenID',
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
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'type',
      title: '社交平台',
      minWidth: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.SYSTEM_SOCIAL_TYPE },
      },
    },
    {
      field: 'openid',
      title: 'OpenID',
      minWidth: 180,
    },
    {
      field: 'nickname',
      title: '用户名称',
      minWidth: 120,
    },
    {
      field: 'userAccount',
      title: '关联账号',
      minWidth: 140,
      formatter: ({ cellValue }) => cellValue || '—',
    },
    {
      field: 'userNickname',
      title: '关联用户',
      minWidth: 140,
      formatter: ({ cellValue }) => cellValue || '—',
    },
    {
      field: 'accountUserType',
      title: '账号类型',
      minWidth: 110,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.SYSTEM_USER_TYPE },
      },
    },
    {
      field: 'avatar',
      title: '用户头像',
      minWidth: 100,
      cellRender: {
        name: 'CellImage',
      },
    },
    {
      field: 'createTime',
      title: '创建时间',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'updateTime',
      title: '更新时间',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 120,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

/** 详情页的字段 */
export function useDetailSchema(): DescriptionItemSchema[] {
  return [
    {
      field: 'type',
      label: '社交平台',
      render: (val) => {
        return h(DictTag, {
          type: DICT_TYPE.SYSTEM_SOCIAL_TYPE,
          value: val,
        });
      },
    },
    {
      field: 'nickname',
      label: '用户名称',
    },
    {
      field: 'userAccount',
      label: '关联账号',
    },
    {
      field: 'userNickname',
      label: '关联用户',
    },
    {
      field: 'accountUserType',
      label: '账号类型',
      render: (val) =>
        val === undefined || val === null
          ? '—'
          : h(DictTag, {
              type: DICT_TYPE.SYSTEM_USER_TYPE,
              value: val,
            }),
    },
    {
      field: 'avatar',
      label: '用户头像',
      render: (val) => (val ? h(Image, { src: val }) : '无'),
    },
    {
      field: 'token',
      label: '社交令牌',
    },
    {
      field: 'rawTokenInfo',
      label: '原始令牌数据',
    },
    {
      field: 'rawUserInfo',
      label: '原始用户数据',
    },
    {
      field: 'code',
      label: '最近一次授权码',
    },
    {
      field: 'state',
      label: '最近一次授权的状态参数',
    },
  ];
}
