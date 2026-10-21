import type { StatusTone } from '#/styles/theme/palette';
import type { StatusMeta } from '#/utils/status';
import type { InsightTone } from '#/utils/status-statistics';

/** 指标卡：数值用正文色；status 显示在标签右侧，tone 在数值前加一个状态圆点（数值本身就是状态时用） */
export interface AnalysisMetric {
  label: string;
  meta?: string;
  status?: StatusMeta;
  tone?: StatusTone;
  value: string;
}

/** 分析结论：圆点颜色表示语气，标题后面一句说明 */
export interface AnalysisInsight {
  detail: string;
  title: string;
  tone: InsightTone;
}
