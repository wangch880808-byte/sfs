<script lang="ts" setup>
import type { CapacityIndicatorApi } from '#/api/capacity/indicator';
import type { EduQuestionBankApi } from '#/api/edu/questionbank';

import { computed, onMounted, ref, watch } from 'vue';

import { confirm, Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Input,
  message,
  Pagination,
  Popconfirm,
  Popover,
  Select,
  Table,
} from 'ant-design-vue';

import { getIndicatorList } from '#/api/capacity/indicator';
import {
  clearQuestionBank,
  deleteQuestionBank,
  getQuestionBankPage,
} from '#/api/edu/questionbank';
import ImportWizard from '#/components/data-exchange/import-wizard.vue';
import { EmptyState } from '#/components/empty-state';
import { getPageNoAfterEmptyPage } from '#/utils/pagination';

const loading = ref(false);
const dataList = ref<EduQuestionBankApi.QuestionBank[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);

// 筛选条件
const filterFirstId = ref<number | undefined>();
const filterSecondId = ref<number | undefined>();
const filterLevel = ref<number | undefined>();
const filterTitle = ref('');

// 指标数据
const indicatorTree = ref<CapacityIndicatorApi.Indicator[]>([]);

const firstIndicatorOptions = computed(() =>
  indicatorTree.value
    .filter((i) => i.indicatorLevel === 1)
    .map((i) => ({ label: i.indicatorName, value: i.id })),
);

const secondIndicatorOptions = computed(() => {
  if (!filterFirstId.value) return [];
  return indicatorTree.value
    .filter(
      (i) =>
        i.indicatorLevel === 2 && i.parentIndicatorId === filterFirstId.value,
    )
    .map((i) => ({ label: i.indicatorName, value: i.id }));
});

const levelOptions = [
  { label: '专业', value: 0 },
  { label: '初级', value: 1 },
  { label: '中级', value: 2 },
  { label: '高级', value: 3 },
];

/** 是否带着筛选条件：决定表格空状态是“筛选没有结果”还是“还没有数据” */
const hasFilter = computed(
  () =>
    filterFirstId.value !== undefined ||
    filterSecondId.value !== undefined ||
    filterLevel.value !== undefined ||
    Boolean(filterTitle.value),
);

watch(filterFirstId, () => {
  filterSecondId.value = undefined;
});

const columns = [
  { dataIndex: 'title', title: '标题', width: 200 },
  { dataIndex: 'indicatorFirstName', title: '一级指标', width: 140 },
  { dataIndex: 'indicatorSecondName', title: '二级指标', width: 140 },
  {
    dataIndex: 'homeworkLevel',
    title: '等级',
    width: 100,
    customRender: ({ text }: { text: number }) =>
      levelOptions.find((o) => o.value === text)?.label ?? '-',
  },
  { dataIndex: 'requirement', title: '任务要求', width: 420 },
  { dataIndex: 'action', title: '操作', width: 80, fixed: 'right' as const },
];

function normalizeRequirement(value?: string) {
  return String(value ?? '').trim();
}

function buildRequirementPreview(value?: string) {
  const text = normalizeRequirement(value);
  if (!text) {
    return '-';
  }
  return text.replaceAll(/\s+/g, ' ');
}

function shouldShowRequirementDetail(value?: string) {
  return normalizeRequirement(value).length > 48;
}

async function loadData() {
  loading.value = true;
  try {
    const { list, total: t } = await getQuestionBankPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      indicatorFirstId: filterFirstId.value,
      indicatorSecondId: filterSecondId.value,
      homeworkLevel: filterLevel.value,
      title: filterTitle.value || undefined,
    });
    dataList.value = list ?? [];
    total.value = Number(t || 0);
  } finally {
    loading.value = false;
  }
  // 删除或清空后当前页没有数据了：回退到还有数据的页重新读取
  const fallbackPageNo = getPageNoAfterEmptyPage({
    listLength: dataList.value.length,
    pageNo: pageNo.value,
    pageSize: pageSize.value,
    total: total.value,
  });
  if (fallbackPageNo !== undefined) {
    pageNo.value = fallbackPageNo;
    await loadData();
  }
}

function handleSearch() {
  pageNo.value = 1;
  loadData();
}

function handleReset() {
  filterFirstId.value = undefined;
  filterSecondId.value = undefined;
  filterLevel.value = undefined;
  filterTitle.value = '';
  handleSearch();
}

async function handleDelete(id: number) {
  await deleteQuestionBank(id);
  message.success('题目已删除');
  loadData();
}

async function handleClear() {
  try {
    await confirm({
      cancelText: '返回',
      confirmText: '清空题库',
      content:
        '题库中的全部题目都会被删除，且无法恢复。需要时可以用原来的 Excel 文件重新导入。',
      icon: 'warning',
      title: '清空题库？',
    });
  } catch {
    return;
  }
  await clearQuestionBank();
  message.success('题库已清空');
  loadData();
}

const importModalVisible = ref(false);

function openImportModal() {
  importModalVisible.value = true;
}

async function loadIndicators() {
  const list = await getIndicatorList();
  indicatorTree.value = list ?? [];
}

onMounted(async () => {
  await loadIndicators();
  loadData();
});
</script>

<template>
  <Page
    auto-content-height
    description="按能力指标与等级管理实训任务题目，可以从 Excel 批量导入。"
    title="题库管理"
  >
    <template #extra>
      <Button danger @click="handleClear">清空题库</Button>
      <Button type="primary" @click="openImportModal">导入题库</Button>
    </template>

    <Card>
      <!-- 筛选区域 -->
      <div class="qb-toolbar">
        <Select
          v-model:value="filterFirstId"
          :options="firstIndicatorOptions"
          allow-clear
          class="w-40"
          placeholder="一级指标"
        />
        <Select
          v-model:value="filterSecondId"
          :options="secondIndicatorOptions"
          allow-clear
          class="w-40"
          placeholder="二级指标"
        />
        <Select
          v-model:value="filterLevel"
          :options="levelOptions"
          allow-clear
          class="w-32"
          placeholder="任务等级"
        />
        <Input
          v-model:value="filterTitle"
          allow-clear
          class="w-48"
          placeholder="搜索标题"
          @press-enter="handleSearch"
        />
        <Button type="primary" @click="handleSearch">查询</Button>
        <Button @click="handleReset">重置</Button>
      </div>

      <!-- 表格 -->
      <Table
        :columns="columns"
        :data-source="dataList"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 800 }"
        row-key="id"
        size="middle"
      >
        <template #emptyText>
          <EmptyState
            v-if="hasFilter"
            compact
            description="换个指标、等级或标题再试，也可以重置筛选查看全部题目。"
            kind="filtered"
            title="没有符合条件的题目"
          >
            <template #action>
              <Button size="small" @click="handleReset">重置筛选</Button>
            </template>
          </EmptyState>
          <EmptyState
            v-else
            compact
            description="点击右上角的「导入题库」，按模板从 Excel 批量导入题目。"
            title="题库里还没有题目"
          />
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'requirement'">
            <div class="qb-req">
              <div class="qb-req__preview">
                {{ buildRequirementPreview(record.requirement) }}
              </div>
              <Popover
                v-if="shouldShowRequirementDetail(record.requirement)"
                placement="leftTop"
              >
                <template #content>
                  <div class="qb-req__popover">
                    <div class="qb-req__popover-title">任务要求全文</div>
                    <div class="qb-req__popover-content">
                      {{ normalizeRequirement(record.requirement) }}
                    </div>
                  </div>
                </template>
                <Button class="qb-req__trigger" size="small" type="link">
                  查看全文
                </Button>
              </Popover>
            </div>
          </template>
          <template v-if="column.dataIndex === 'action'">
            <Popconfirm
              :title="`删除题目「${record.title}」？`"
              cancel-text="返回"
              description="删除后不能恢复，需要时重新导入。"
              ok-text="删除"
              @confirm="handleDelete(record.id)"
            >
              <Button danger size="small" type="link">删除</Button>
            </Popconfirm>
          </template>
        </template>
      </Table>
      <div class="mt-4 flex justify-end">
        <Pagination
          v-model:current="pageNo"
          v-model:page-size="pageSize"
          :show-total="(t: number) => `共 ${t} 条`"
          :total="total"
          show-size-changer
          @change="loadData"
        />
      </div>
    </Card>

    <ImportWizard
      v-model:open="importModalVisible"
      biz-type="QUESTION_BANK"
      template-file-name="题库导入模板.xlsx"
      title="导入题库"
      @success="loadData"
    />
  </Page>
</template>

<style scoped>
.qb-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
}

.qb-req {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.qb-req__preview {
  display: -webkit-box;
  overflow: hidden;
  text-overflow: ellipsis;
  -webkit-line-clamp: 2;
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
}

.qb-req__trigger {
  align-self: flex-start;
  height: auto;
  padding: 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
}

.qb-req__popover {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.qb-req__popover-title {
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text);
}

.qb-req__popover-content {
  max-width: 440px;
  max-height: 320px;
  padding-right: 4px;
  overflow: auto;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
</style>
