<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { computed, nextTick, onMounted, ref, useSlots, watch } from 'vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';

interface Props {
  columns: NonNullable<VxeTableGridOptions['columns']>;
  data: Record<string, any>[];
  gridClass?: string;
  gridId?: string;
  height?: number | string;
  pageSize?: number;
  pagerEnabled?: boolean;
  rowConfig?: NonNullable<VxeTableGridOptions['rowConfig']>;
  rowKey?: string;
}

const props = withDefaults(defineProps<Props>(), {
  data: () => [],
  gridClass: '',
  gridId: undefined,
  height: undefined,
  pageSize: 8,
  pagerEnabled: false,
  rowConfig: () => ({}),
  rowKey: 'id',
});

const wrapperRef = ref<HTMLElement>();
const slots = useSlots();
const delegatedSlotNames = computed(() => Object.keys(slots));
const mergedGridClass = computed(() =>
  // vben 的表格容器默认带 p-2，cn 合并类名时后写的 p-0 覆盖它，不必再用强制优先级
  ['detail-sheet-grid', 'p-0', props.gridClass].filter(Boolean).join(' '),
);

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: props.columns,
    data: props.data,
    id: props.gridId,
    ...(props.height === undefined ? {} : { height: props.height }),
    autoResize: true,
    keepSource: true,
    pagerConfig: props.pagerEnabled
      ? {
          enabled: true,
          currentPage: 1,
          pageSize: props.pageSize,
        }
      : {
          enabled: false,
        },
    proxyConfig: props.pagerEnabled
      ? {
          ajax: {
            query: async ({
              page,
            }: {
              page: { currentPage: number; pageSize: number };
            }) => {
              const start = (page.currentPage - 1) * page.pageSize;
              return {
                list: props.data.slice(start, start + page.pageSize),
                total: props.data.length,
              };
            },
          },
        }
      : undefined,
    rowConfig: {
      keyField: props.rowKey,
      isHover: true,
      ...props.rowConfig,
    },
  },
});

function getScrollTarget() {
  if (typeof window === 'undefined') {
    return null;
  }
  let current = wrapperRef.value?.parentElement ?? null;
  while (current) {
    const style = window.getComputedStyle(current);
    const overflowY = style.overflowY || style.overflow;
    if (
      /auto|overlay|scroll/.test(overflowY) &&
      current.scrollHeight > current.clientHeight + 1
    ) {
      return current;
    }
    current = current.parentElement;
  }
  return window;
}

async function preserveScroll<T>(action: () => Promise<T> | T) {
  const target = getScrollTarget();
  const left =
    target === window
      ? window.scrollX
      : (target as HTMLElement | null)?.scrollLeft || 0;
  const top =
    target === window
      ? window.scrollY
      : (target as HTMLElement | null)?.scrollTop || 0;
  const result = await action();
  await nextTick();
  if (!target || typeof window === 'undefined') {
    return result;
  }
  const restore = () => {
    if (target === window) {
      window.scrollTo({ left, top, behavior: 'auto' });
      return;
    }
    if (target instanceof HTMLElement) {
      target.scrollLeft = left;
      target.scrollTop = top;
    }
  };
  restore();
  window.requestAnimationFrame(restore);
  return result;
}

async function refreshPagedGrid() {
  gridApi.setGridOptions({
    pagerConfig: {
      enabled: true,
      currentPage: 1,
      pageSize: props.pageSize,
    },
  });
  await nextTick();
  await preserveScroll(() => gridApi.query());
}

watch(
  () => props.columns,
  (columns) => {
    gridApi.setGridOptions({ columns });
  },
  { immediate: true, deep: true },
);

watch(
  () => props.data,
  async (data) => {
    if (props.pagerEnabled) {
      await refreshPagedGrid();
      return;
    }
    gridApi.setGridOptions({ data });
  },
  { immediate: true, deep: true },
);

watch(
  [
    () => props.height,
    () => props.rowConfig,
    () => props.rowKey,
    () => props.gridClass,
    () => props.gridId,
    () => props.pageSize,
    () => props.pagerEnabled,
  ],
  ([height, rowConfig, rowKey, , gridId, pageSize, pagerEnabled]) => {
    gridApi.setState({
      gridClass: mergedGridClass.value,
      gridOptions: {
        autoResize: true,
        ...(height === undefined ? {} : { height }),
        id: gridId,
        pagerConfig: pagerEnabled
          ? {
              enabled: true,
              pageSize,
            }
          : {
              enabled: false,
            },
        proxyConfig: pagerEnabled
          ? {
              ajax: {
                query: async ({
                  page,
                }: {
                  page: { currentPage: number; pageSize: number };
                }) => {
                  const start = (page.currentPage - 1) * page.pageSize;
                  return {
                    list: props.data.slice(start, start + page.pageSize),
                    total: props.data.length,
                  };
                },
              },
            }
          : undefined,
        rowConfig: {
          keyField: rowKey,
          isHover: true,
          ...rowConfig,
        },
      },
    });
  },
  { immediate: true, deep: true },
);

onMounted(async () => {
  if (props.pagerEnabled) {
    await nextTick();
    await preserveScroll(() => gridApi.query());
  }
});

function recalculate() {
  return preserveScroll(() => {
    gridApi.grid?.recalculate?.();
  });
}

function zoom() {
  return gridApi.grid?.zoom?.();
}

function handleCustom() {
  return (gridApi.grid as any)?.handleCustom?.();
}

defineExpose({
  handleCustom,
  recalculate,
  zoom,
});
</script>

<template>
  <div ref="wrapperRef">
    <Grid :grid-class="mergedGridClass">
      <template
        v-for="slotName in delegatedSlotNames"
        :key="slotName"
        #[slotName]="slotProps"
      >
        <slot :name="slotName" v-bind="slotProps"></slot>
      </template>
    </Grid>
  </div>
</template>

<style scoped>
:deep(.detail-sheet-grid .vxe-grid--toolbar-wrapper) {
  display: none;
}

:deep(
  .detail-sheet-grid .vxe-header--row .vxe-header--column:first-child .vxe-cell
),
:deep(
  .detail-sheet-grid .vxe-body--row .vxe-body--column:first-child .vxe-cell
) {
  padding-left: 16px;
}

:deep(
  .detail-sheet-grid .vxe-body--row .vxe-body--column:first-child .vxe-cell
) {
  padding-right: 8px;
}
</style>
