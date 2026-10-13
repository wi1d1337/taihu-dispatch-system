<template>
  <div class="app-container">
    <el-row :gutter="10" class="mb8">
      <el-col :span="12">
        <el-radio-group v-model="days" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
      </el-col>
      <el-col :span="12" style="text-align: right">
        <el-button icon="Refresh" @click="load">刷新</el-button>
      </el-col>
    </el-row>

    <!-- 回验汇总 -->
    <el-row :gutter="12" class="mb12">
      <el-col :span="5" v-for="c in kpiCards" :key="c.key">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">{{ c.label }}</div>
            <div class="kpi-value" :style="{ color: c.color }">{{ c.value() }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :span="14">
        <el-card shadow="never" class="mb12">
          <template #header><span>研判等级趋势（预测 vs 次日实际）</span></template>
          <div ref="trendRef" class="chart"></div>
        </el-card>
        <el-card shadow="never">
          <template #header><span>分区 × 日期 等级热力（实际等级）</span></template>
          <div v-if="hasHeat" ref="heatRef" class="chart chart-sm"></div>
          <el-empty v-else description="回验明细无分区维度，热力图不可用" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span>命中矩阵（行=预测等级，列=实际等级）</span></template>
          <el-table :data="matrixRows" border size="small" :cell-class-name="matrixCellClass">
            <el-table-column label="" align="center" width="80">
              <template #default="s">
                <span class="level-badge" :style="{ background: levelColor(s.row.pred) }">{{ levelText(s.row.pred) }}</span>
              </template>
            </el-table-column>
            <el-table-column v-for="a in ['1','2','3']" :key="a" :label="levelText(a) + '(实际)'" align="center">
              <template #default="s">{{ s.row.cells[a] ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="行合计" align="center" width="80">
              <template #default="s">{{ rowTotal(s.row) }}</template>
            </el-table-column>
          </el-table>
          <div class="matrix-note">
            对角线=命中；右上=误报（预测偏高），左下=漏报（预测偏低）。
            等级仅 1/2/3 参与矩阵（0 无需干预不计入）。
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="TaihuAssess">
import { getAssessAccuracy } from '@/api/taihu/assess'
import { pick, levelColor, levelText } from '@/utils/taihu'
import * as echarts from 'echarts'

const days = ref(30)
const summary = ref({})
const records = ref([])
const hasHeat = ref(false)

const LEVELS = ['1', '2', '3']

const kpiCards = computed(() => [
  { key: 'total', label: '回验样本', color: '#303133', value: () => summary.value.total ?? '-' },
  { key: 'hits', label: '命中', color: '#67c23a', value: () => summary.value.hits ?? '-' },
  { key: 'falseAlarms', label: '误报', color: '#e6a23c', value: () => summary.value.falseAlarms ?? '-' },
  { key: 'misses', label: '漏报', color: '#f56c6c', value: () => summary.value.misses ?? '-' },
  { key: 'hitRate', label: '命中率', color: '#409eff', value: () => pct(summary.value.hitRate) }
])

function pct(v) {
  if (v == null || v === '') return '-'
  return (v <= 1 ? (v * 100).toFixed(1) : v) + '%'
}

/** 明细解析（字段名容错） */
function parseRecords(data) {
  let list = data.records ?? data.daily ?? data.details ?? data.items ?? []
  if (!Array.isArray(list)) list = []
  return list.map(r => ({
    date: pick(r, 'date', 'date') ?? pick(r, 'assess_date', 'assessDate') ?? '',
    zone: pick(r, 'zone_name', 'zoneName') ?? pick(r, 'zone', 'zone') ?? '',
    predicted: String(pick(r, 'predicted_level', 'predictedLevel') ?? pick(r, 'predicted', 'predicted') ?? ''),
    actual: String(pick(r, 'actual_level', 'actualLevel') ?? pick(r, 'actual', 'actual') ?? '')
  })).filter(r => LEVELS.includes(r.predicted) || LEVELS.includes(r.actual))
}

/** 命中矩阵：优先后端 matrix，否则由明细累计 */
function buildMatrix(data) {
  const rows = LEVELS.map(p => ({ pred: p, cells: { '1': 0, '2': 0, '3': 0 } }))
  const byIndex = { '1': 0, '2': 1, '3': 2 }
  const raw = data.matrix ?? data.confusion ?? null
  if (raw) {
    const arr = typeof raw === 'string' ? safeJson(raw) : raw
    if (Array.isArray(arr)) {
      arr.forEach((rowArr, i) => {
        if (!rows[i] || !Array.isArray(rowArr)) return
        LEVELS.forEach((a, j) => { rows[i].cells[a] = rowArr[j] ?? 0 })
      })
      return rows
    }
  }
  records.value.forEach(r => {
    const i = byIndex[r.predicted]
    const j = r.actual
    if (i != null && byIndex[j] != null) rows[i].cells[j] += 1
  })
  return rows
}

const matrixRows = ref([])
const rowTotal = row => Object.values(row.cells).reduce((s, v) => s + (v || 0), 0)

function matrixCellClass({ row, columnIndex }) {
  // 前一列为表头（预测等级），第 1-3 列对应实际 1/2/3；对角线高亮
  if (columnIndex >= 1 && columnIndex <= 3) {
    const actual = LEVELS[columnIndex - 1]
    if (row.pred === actual && (row.cells[actual] ?? 0) > 0) return 'hit-cell'
  }
  return ''
}

function safeJson(s) {
  try { return JSON.parse(s) } catch { return {} }
}

/** ---------- 图表 ---------- */
let trendChart = null
let heatChart = null
const trendRef = ref(null)
const heatRef = ref(null)

function renderTrend() {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  // 按日期聚合平均等级（有分区维度时为全分区均值）
  const byDate = {}
  records.value.forEach(r => {
    const b = byDate[r.date] = byDate[r.date] || { p: [], a: [] }
    if (LEVELS.includes(r.predicted)) b.p.push(Number(r.predicted))
    if (LEVELS.includes(r.actual)) b.a.push(Number(r.actual))
  })
  const dates = Object.keys(byDate).sort()
  const pred = dates.map(d => avg(byDate[d].p))
  const act = dates.map(d => avg(byDate[d].a))
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['预测等级', '实际等级'] },
    grid: { left: 48, right: 24, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', min: 0, max: 3, interval: 1, axisLabel: { formatter: v => levelText(String(v)) } },
    series: [
      { name: '预测等级', type: 'line', step: 'end', data: pred, itemStyle: { color: '#409eff' } },
      { name: '实际等级', type: 'line', step: 'end', data: act, itemStyle: { color: '#d93025' } }
    ]
  }, true)
}

function avg(arr) {
  if (!arr || arr.length === 0) return null
  return +(arr.reduce((s, v) => s + v, 0) / arr.length).toFixed(2)
}

function renderHeat() {
  // 分区 × 日期 热力（实际等级）；无分区维度则隐藏
  const withZone = records.value.filter(r => r.zone)
  if (withZone.length === 0 || !heatRef.value) {
    hasHeat.value = false
    return
  }
  hasHeat.value = true
  if (!heatChart) heatChart = echarts.init(heatRef.value)
  const zones = [...new Set(withZone.map(r => r.zone))]
  const dates = [...new Set(withZone.map(r => r.date))].sort()
  const data = []
  withZone.forEach(r => {
    const z = zones.indexOf(r.zone)
    const d = dates.indexOf(r.date)
    if (LEVELS.includes(r.actual)) data.push([d, z, Number(r.actual)])
  })
  heatChart.setOption({
    tooltip: { formatter: p => `${zones[p.value[1]]} ${dates[p.value[0]]}<br/>等级：${levelText(String(p.value[2]))}` },
    grid: { left: 90, right: 24, top: 16, bottom: 48 },
    xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'category', data: zones },
    visualMap: {
      min: 1, max: 3, calculable: false, orient: 'horizontal',
      left: 'center', bottom: 0, itemHeight: 60,
      inRange: { color: ['#f2c14e', '#e6772e', '#d93025'] },
      formatter: v => levelText(String(Math.round(v)))
    },
    series: [{ type: 'heatmap', data, label: { show: true } }]
  }, true)
  // hasHeat 置真后 DOM 才渲染，需手动 resize 一次
  nextTick(() => heatChart.resize())
}

function load() {
  getAssessAccuracy({ days: days.value }).then(res => {
    const data = res.data ?? res
    summary.value = {
      total: pick(data, 'total', 'total') ?? (data.summary && data.summary.total),
      hits: pick(data, 'hits', 'hits') ?? (data.summary && data.summary.hits),
      falseAlarms: data.falseAlarms ?? data.false_alarms ?? (data.summary && data.summary.falseAlarms),
      misses: pick(data, 'misses', 'misses') ?? (data.summary && data.summary.misses),
      hitRate: pick(data, 'hit_rate', 'hitRate') ?? (data.summary && (data.summary.hitRate ?? data.summary.hit_rate))
    }
    records.value = parseRecords(data)
    matrixRows.value = buildMatrix(data)
    renderTrend()
    renderHeat()
  })
}

function handleResize() {
  trendChart?.resize()
  heatChart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  heatChart?.dispose()
})
</script>

<style scoped lang="scss">
.mb12 { margin-bottom: 12px; }
.mb8 { margin-bottom: 8px; }

.kpi-card {
  text-align: center;
  .kpi-label { font-size: 13px; color: #606266; }
  .kpi-value { font-size: 24px; font-weight: 600; margin-top: 4px; }
}

.chart {
  height: 300px;
  width: 100%;
  &.chart-sm { height: 260px; }
}

.level-badge {
  display: inline-block;
  padding: 0 6px;
  border-radius: 3px;
  color: #fff;
  font-size: 12px;
  line-height: 18px;
}

.matrix-note {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
  line-height: 18px;
}

:deep(.hit-cell) {
  background: #f0f9eb;
  font-weight: 600;
}
</style>
