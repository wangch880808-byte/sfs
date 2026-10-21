<script lang="ts" setup>
import type { TableColumnsType } from 'ant-design-vue';

import type { CallLogFilter } from './data';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiCallLogApi } from '#/api/ai/call-log';

import { computed, ref } from 'vue';

import { Page, VbenLoading } from '@vben/common-ui';

import { Alert, Button, Spin, Table } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getCallLogDailySummary, getCallLogPage } from '#/api/ai/call-log';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { hasActiveFilters } from '#/utils/confirm-copy';
import {
  formatAiTokens,
  getAiCallErrorLabel,
  getAiCallResultMeta,
  sumAiCallDaily,
} from '#/utils/status-ai-governance';

import {
  businessLabelOf,
  summaryRangeText,
  toCallLogQuery,
  useGridColumns,
  useGridFormSchema,
} from './data';

/** 当前是否带筛选条件：决定空状态写“还没有”还是“没有符合条件的” */
const isFiltered = ref(false);

const summaryRows = ref<AiCallLogApi.DailySummary[]>([]);
const summaryLoading = ref(false);
const summaryError = ref('');
const summaryFilter = ref<AiCallLogApi.DailySummaryReq>({});
let summaryRequestId = 0;

const totals = computed(() => sumAiCallDaily(summaryRows.value));
const failedRate = computed(() => {
  const { callCount, failedCount } = totals.value;
  if (callCount === 0) return '';
  return `失败率 ${((failedCount / callCount) * 100).toFixed(1)}%`;
});
const summaryScope = computed(() => {
  const { businessCode, createTime } = summaryFilter.value;
  const business = businessLabelOf(businessCode);
  const range = summaryRangeText(createTime);
  return business ? `${range} · ${business}` : range;
});
const sortedSummaryRows = computed(() =>
  summaryRows.value.toSorted(
    (a, b) =>
      b.date.localeCompare(a.date) ||
      a.businessName.localeCompare(b.businessName, 'zh-CN'),
  ),
);

const summaryColumns: TableColumnsType<AiCallLogApi.DailySummary> = [
  { dataIndex: 'date', title: '日期', width: 120 },
  { dataIndex: 'businessName', key: 'businessName', title: '业务' },
  { align: 'right', dataIndex: 'callCount', title: '调用次数', width: 110 },
  { align: 'right', dataIndex: 'failedCount', title: '失败次数', width: 110 },
  {
    align: 'right',
    dataIndex: 'totalTokens',
    key: 'totalTokens',
    title: '令牌合计',
    width: 140,
  },
];

function summaryRowKey(row: AiCallLogApi.DailySummary) {
  return `${row.date}-${row.businessCode}`;
}

/** 读不到时写明原因，原因来自后端提示，没有就写未知 */
function reasonOf(error: unknown) {
  const data = (error as { response?: { data?: { msg?: string } } })?.response
    ?.data;
  const reason = data?.msg || (error as Error)?.message;
  return reason ? reason.replace(/[。.]$/, '') : '原因未知';
}

/** 汇总只按调用时间与业务筛选；读不到时只让汇总这一块降级，下面的列表照常显示 */
async function loadSummary(filter: AiCallLogApi.DailySummaryReq) {
  const id = ++summaryRequestId;
  summaryFilter.value = filter;
  summaryLoading.value = true;
  summaryError.value = '';
  try {
    const rows = await getCallLogDailySummary(filter, { silentError: true });
    if (id !== summaryRequestId) return;
    summaryRows.value = rows ?? [];
  } catch (error) {
    if (id !== summaryRequestId) return;
    summaryRows.value = [];
    summaryError.value = `读取按天汇总没有成功：${reasonOf(error)}。调整调用时间后点「重试」。`;
  } finally {
    if (id === summaryRequestId) summaryLoading.value = false;
  }
}

function retrySummary() {
  void loadSummary(summaryFilter.value);
}

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues: CallLogFilter) => {
          isFiltered.value = hasActiveFilters(
            formValues as Record<string, unknown>,
          );
          const query = toCallLogQuery(formValues);
          void loadSummary({
            businessCode: query.businessCode,
            createTime: query.createTime,
          });
          return await getCallLogPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...query,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<AiCallLogApi.CallLog>,
});
</script>

<template>
  <Page
    auto-content-height
    description="查看每次 AI 调用的业务、模型、令牌用量和结果，用来排查失败、估算用量。"
    title="调用日志"
  >
    <div class="call-log-page">
      <section class="call-summary surface-panel">
        <header class="call-summary__header">
          <h2 class="section-title">按天汇总</h2>
          <p class="call-summary__scope">
            {{ summaryScope }}。汇总只看调用时间和业务两项筛选。
          </p>
        </header>
        <Alert
          v-if="summaryError"
          :message="summaryError"
          show-icon
          type="error"
        >
          <template #action>
            <Button size="small" @click="retrySummary">重试</Button>
          </template>
        </Alert>
        <Spin v-else :spinning="summaryLoading" tip="正在读取按天汇总…">
          <div class="metric-grid">
            <div class="metric-card">
              <div class="metric-card__label">调用次数</div>
              <div class="metric-card__value">
                {{ formatAiTokens(totals.callCount) }}
              </div>
            </div>
            <div class="metric-card">
              <div class="metric-card__label">失败次数</div>
              <div class="metric-card__value">
                {{ formatAiTokens(totals.failedCount) }}
              </div>
              <div v-if="failedRate" class="metric-card__meta">
                {{ failedRate }}
              </div>
            </div>
            <div class="metric-card">
              <div class="metric-card__label">令牌合计</div>
              <div class="metric-card__value">
                {{ formatAiTokens(totals.totalTokens) }}
              </div>
              <div class="metric-card__meta">
                服务商没有返回用量的调用不计入
              </div>
            </div>
          </div>
          <Table
            v-if="sortedSummaryRows.length > 0"
            :columns="summaryColumns"
            :data-source="sortedSummaryRows"
            :pagination="false"
            :row-key="summaryRowKey"
            :scroll="{ y: 200 }"
            class="call-summary__table"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'businessName'">
                {{
                  record.businessName ||
                  businessLabelOf(record.businessCode) ||
                  '—'
                }}
              </template>
              <template v-else-if="column.key === 'totalTokens'">
                {{ formatAiTokens(record.totalTokens) }}
              </template>
            </template>
          </Table>
          <EmptyState
            v-else-if="!summaryLoading"
            compact
            description="换一段调用时间或其他业务再看。"
            title="这段时间没有 AI 调用"
          />
        </Spin>
      </section>

      <div class="call-log-page__grid">
        <Grid table-title="调用记录">
          <template #operator="{ row }">
            <span class="call-operator">
              <span v-if="row.operatorName">{{ row.operatorName }}</span>
              <span v-else-if="!row.background">—</span>
              <span v-if="row.background" class="call-operator__tag">
                后台任务
              </span>
            </span>
          </template>
          <template #result="{ row }">
            <span class="call-result">
              <StatusTag v-bind="getAiCallResultMeta(row.success)" />
              <span
                v-if="!row.success && getAiCallErrorLabel(row.errorType)"
                class="call-result__type"
              >
                {{ getAiCallErrorLabel(row.errorType) }}
              </span>
            </span>
          </template>
          <template #loading>
            <VbenLoading spinning text="正在读取调用日志…" />
          </template>
          <template #empty>
            <EmptyState
              v-if="isFiltered"
              compact
              description="调整或清空筛选条件后再试。"
              kind="filtered"
              title="没有符合条件的调用记录"
            />
            <EmptyState
              v-else
              compact
              description="教师使用 AI 初评、学情分析等功能，或系统在后台调用 AI 之后，记录会出现在这里。"
              title="还没有 AI 调用记录"
            />
          </template>
        </Grid>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.call-log-page {
  display: flex;
  flex-direction: column;
  gap: var(--nus-section-gap);
  height: 100%;
}

.call-summary {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: var(--nus-space-4);
  padding: var(--nus-card-padding);
}

.call-summary__header {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-1) var(--nus-space-3);
  align-items: baseline;
}

.call-summary__scope {
  margin: 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
}

.call-summary__table {
  margin-top: var(--nus-space-4);
}

.call-log-page__grid {
  flex: 1 1 auto;
  min-height: 520px;
}

.call-operator,
.call-result {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: center;
  max-width: 100%;
}

.call-operator__tag,
.call-result__type {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
  white-space: nowrap;
}
</style>
