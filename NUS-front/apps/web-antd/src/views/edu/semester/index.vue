<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import { computed, onDeactivated, onMounted, ref } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  DatePicker,
  Form,
  FormItem,
  InputNumber,
  message,
  Select,
  Spin,
  Switch,
  Tag,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  getCurrentSemester,
  saveCurrentSemester,
  setSemesterAutoMode,
} from '#/api/edu/semester';

import {
  resolveWrapUpLeave,
  WRAP_UP_LEAVE_BLOCKED,
} from './components/semester-wrap-up';
import SemesterWrapUpModal from './components/semester-wrap-up-modal.vue';

defineOptions({ name: 'EduSemester' });

const wrapUpOpen = ref(false);
const wrapUpModalRef = ref<InstanceType<typeof SemesterWrapUpModal>>();

const loading = ref(false);
const saving = ref(false);
const modeSaving = ref(false);
const autoEnabled = ref(true);

type SemesterTerm = '上学期' | '下学期';

const academicStartYear = ref<number>();
const semesterTerm = ref<SemesterTerm>('下学期');
const semesterRange = ref<[Dayjs, Dayjs] | undefined>(undefined);
const activeSemesterName = ref('未设置');
const activeSemesterStart = ref('');
const activeSemesterEnd = ref('');

const academicYearText = computed(() =>
  academicStartYear.value
    ? `${academicStartYear.value}-${academicStartYear.value + 1}`
    : '未设置',
);
const semesterNameText = computed(
  () => `${academicYearText.value} ${semesterTerm.value}`,
);
const termOptions = [
  { label: '上学期', value: '上学期' },
  { label: '下学期', value: '下学期' },
];

function parseSemesterName(rawName?: string) {
  const name = (rawName || '').trim();
  if (!name || name === '未设置') return;

  let matched = name.match(/^(\d{4})-(\d{4})\s*(上学期|下学期)$/);
  if (matched) {
    academicStartYear.value = Number(matched[1]!);
    semesterTerm.value = matched[3] as SemesterTerm;
    return;
  }

  matched = name.match(/^(\d{4})-(\d{4})-(1|2)$/);
  if (matched) {
    academicStartYear.value = Number(matched[1]!);
    semesterTerm.value = matched[3] === '1' ? '上学期' : '下学期';
    return;
  }

  matched = name.match(/^(\d{4})-(\d{4})$/);
  if (matched) {
    academicStartYear.value = Number(matched[1]!);
    return;
  }

  const yearMatched = name.match(/(\d{4})-(\d{4})/);
  if (yearMatched) {
    academicStartYear.value = Number(yearMatched[1]!);
  }
  if (name.includes('上')) {
    semesterTerm.value = '上学期';
  } else if (name.includes('下')) {
    semesterTerm.value = '下学期';
  }
}

async function loadCurrentSemester() {
  loading.value = true;
  try {
    const data = await getCurrentSemester();
    parseSemesterName(data?.semesterName);
    activeSemesterName.value = data?.semesterName || '未设置';
    activeSemesterStart.value = data?.semesterStartDate || '';
    activeSemesterEnd.value = data?.semesterEndDate || '';
    autoEnabled.value = data?.autoEnabled !== false;
    semesterRange.value =
      data?.semesterStartDate && data?.semesterEndDate
        ? [dayjs(data.semesterStartDate), dayjs(data.semesterEndDate)]
        : undefined;
  } finally {
    loading.value = false;
  }
}

async function handleAutoModeChange(checked: boolean | number | string) {
  const enabled = checked === true;
  modeSaving.value = true;
  try {
    await setSemesterAutoMode(enabled);
    window.dispatchEvent(new CustomEvent('edu-semester-updated'));
    message.success(enabled ? '已开启学期自动更新' : '已切换为手动设置');
    await loadCurrentSemester();
  } finally {
    modeSaving.value = false;
  }
}

async function handleSave() {
  if (
    !academicStartYear.value ||
    academicStartYear.value < 2000 ||
    academicStartYear.value > 2100
  ) {
    message.warning('请输入正确的学年起始年份');
    return;
  }
  if (!semesterRange.value || semesterRange.value.length !== 2) {
    message.warning('请选择学期起止日期');
    return;
  }
  saving.value = true;
  try {
    await saveCurrentSemester({
      semesterName: semesterNameText.value,
      semesterStartDate: semesterRange.value[0].format('YYYY-MM-DD'),
      semesterEndDate: semesterRange.value[1].format('YYYY-MM-DD'),
    });
    window.dispatchEvent(new CustomEvent('edu-semester-updated'));
    message.success('当前学期已保存');
    await loadCurrentSemester();
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  loadCurrentSemester();
});

/*
 * 页面被 KeepAlive 缓存，离开时只是失活；弹窗挂在 body 上，不关就会浮在别的页面上。
 * 正在封存时不让离开，免得封存结果没人看到。
 */
onBeforeRouteLeave(() => {
  const { allowLeave, open } = resolveWrapUpLeave({
    open: wrapUpOpen.value,
    submitting: !!wrapUpModalRef.value?.submitting,
  });
  if (!allowLeave) {
    message.warning(WRAP_UP_LEAVE_BLOCKED);
    return false;
  }
  wrapUpOpen.value = open;
});

onDeactivated(() => {
  wrapUpOpen.value = false;
});
</script>

<template>
  <Page
    auto-content-height
    description="配置平台的当前学期，保存后显示在系统顶部栏。"
    title="学期管理"
  >
    <template #extra>
      <Button
        v-access:code="['edu:semester:wrap-up']"
        @click="wrapUpOpen = true"
      >
        学期收尾
      </Button>
    </template>
    <SemesterWrapUpModal
      ref="wrapUpModalRef"
      v-model:open="wrapUpOpen"
      @done="loadCurrentSemester"
    />
    <Card class="sem-card">
      <Spin :spinning="loading" tip="正在读取当前学期…">
        <!-- 当前已生效学期 -->
        <div class="sem-current">
          <div class="sem-current__label">当前已生效学期</div>
          <div class="sem-current__name">
            <span>{{ activeSemesterName || '未设置' }}</span>
            <Tag class="sem-current__mode">
              {{ autoEnabled ? '自动' : '手动' }}
            </Tag>
          </div>
          <div class="sem-current__range">
            {{ activeSemesterStart || '-' }} ~ {{ activeSemesterEnd || '-' }}
          </div>
        </div>

        <!-- 自动更新 -->
        <div class="sem-row">
          <div class="min-w-0">
            <div class="sem-row__title">自动更新</div>
            <div class="sem-row__desc">
              每年 2 月 1 日切换下学期，8 月 1 日切换新学年上学期。
            </div>
          </div>
          <Switch
            :checked="autoEnabled"
            :loading="modeSaving"
            @change="handleAutoModeChange"
          />
        </div>

        <!-- 手动设置 -->
        <div class="sem-manual">
          <div class="sem-row__title">手动设置</div>
          <div class="sem-row__desc">
            {{
              autoEnabled
                ? '开启自动更新时不能手动修改，关闭后可以在这里设置。'
                : '保存后立即生效，并显示在系统顶部栏。'
            }}
          </div>

          <Form class="sem-form" layout="vertical">
            <FormItem
              :extra="`当前将保存为：${semesterNameText}`"
              label="学年与学期"
            >
              <div class="sem-term">
                <InputNumber
                  v-model:value="academicStartYear"
                  :disabled="autoEnabled"
                  :max="2100"
                  :min="2000"
                  :precision="0"
                  class="sem-term__year"
                  placeholder="起始年"
                />
                <span class="sem-term__text">{{ academicYearText }} 学年</span>
                <Select
                  v-model:value="semesterTerm"
                  :disabled="autoEnabled"
                  :options="termOptions"
                  class="sem-term__select"
                />
              </div>
            </FormItem>

            <FormItem label="学期起止时间">
              <DatePicker.RangePicker
                v-model:value="semesterRange"
                :disabled="autoEnabled"
                class="w-full"
              />
            </FormItem>

            <FormItem class="sem-actions">
              <Button
                :disabled="autoEnabled"
                :loading="saving"
                type="primary"
                @click="handleSave"
              >
                保存手动设置
              </Button>
            </FormItem>
          </Form>
        </div>
      </Spin>
    </Card>
  </Page>
</template>

<style scoped>
.sem-card {
  max-width: 640px;
}

.sem-current {
  padding: 12px 16px;
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius);
}

.sem-current__label {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.sem-current__name {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 4px;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.sem-current__mode {
  margin: 0;
  font-weight: var(--nus-fw-regular);
}

.sem-current__range {
  margin-top: 2px;
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.sem-row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0;
  border-bottom: 1px solid var(--nus-border-subtle);
}

.sem-row__title {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.sem-row__desc {
  margin-top: 2px;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.sem-manual {
  padding-top: 20px;
}

.sem-form {
  margin-top: 16px;
}

.sem-term {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.sem-term__year {
  width: 140px;
}

.sem-term__text {
  font-size: var(--nus-fs-body);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-body);
  color: var(--nus-text-secondary);
}

.sem-term__select {
  width: 140px;
}

.sem-actions {
  margin-bottom: 0;
}
</style>
