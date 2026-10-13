// 太湖调度 v2 公共口径：等级/状态文案与色阶（0灰/1黄/2橙/3红）

/** 藻情等级文案 */
export const LEVEL_TEXT = {
  0: '无需干预',
  1: '关注',
  2: '较大',
  3: '重大'
}

/** 等级主色（卡片色块/地图填充/图表统一） */
export const LEVEL_COLORS = {
  0: '#b9bfc9',
  1: '#f2c14e',
  2: '#e6772e',
  3: '#d93025'
}

/** 工单状态文案 */
export const ORDER_STATUS_TEXT = {
  0: '待接单',
  1: '已接单',
  2: '进行中',
  3: '待核查',
  4: '已办结',
  5: '已退回',
  6: '已撤销'
}

/** 工单状态 → el-tag type */
export const ORDER_STATUS_TAG = {
  0: 'warning',
  1: 'primary',
  2: 'warning',
  3: 'warning',
  4: 'success',
  5: 'danger',
  6: 'info'
}

/** 人员角色文案 */
export const WORKER_ROLE_TEXT = { 1: '片长', 2: '打捞员' }

/**
 * 兼容取值：snake / camel 双写（后端与 algo 代理字段命名可能不一致）
 */
export function pick(obj, snakeKey, camelKey) {
  if (obj == null) return undefined
  return obj[snakeKey] ?? obj[camelKey]
}

/** 等级颜色（数字/字符串均可，未知回灰色） */
export function levelColor(level) {
  return LEVEL_COLORS[level] ?? LEVEL_COLORS[0]
}

/** 等级文案（未知回原始值） */
export function levelText(level) {
  return LEVEL_TEXT[level] ?? (level == null ? '-' : String(level))
}

/** 数值显示：空值显示 '-' */
export function fmt(v, suffix = '') {
  if (v == null || v === '') return '-'
  return v + suffix
}
