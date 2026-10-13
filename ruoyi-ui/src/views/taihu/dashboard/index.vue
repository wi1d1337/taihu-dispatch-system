<template>
  <div class="app-container">
    <!-- 工具条：统计窗口 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="12">
        <el-radio-group v-model="days" @change="getReport">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button :value="90">近 90 天</el-radio-button>
        </el-radio-group>
      </el-col>
      <el-col :span="12" style="text-align: right">
        <el-button icon="Refresh" @click="getReport">刷新</el-button>
      </el-col>
    </el-row>

    <!-- KPI 三卡片：预报评估 -->
    <el-row :gutter="12" class="mb12">
      <el-col :span="8" v-for="item in kpiCards" :key="item.key">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">{{ item.label }}</div>
            <div class="kpi-value" :style="{ color: item.color }">{{ formatKpi(kpi[item.key]) }}</div>
            <div class="kpi-desc">{{ item.desc }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 计划 vs 实收（含偏差柱） -->
    <el-card shadow="never" class="mb12">
      <template #header><span>计划收藻 vs 实收吨数（吨/日）</span></template>
      <div ref="trendRef" class="chart chart-lg"></div>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never" class="mb12">
          <template #header><span>分区收藻统计（吨）</span></template>
          <div ref="zoneRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="mb12">
          <template #header><span>Chl-a 趋势（μg/L）</span></template>
          <div ref="chlaRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="TaihuDashboard">
import { getDashboard } from '@/api/taihu/dashboard'
import * as echarts from 'echarts'

const days = ref(30)

/** KPI 卡片定义（POD 命中率 / FAR 误报率 / CSI 综合评分） */
const kpiCards = [
  { key: 'POD', label: 'POD 命中率', desc: '实际发生水华且预报命中的比例', color: '#409eff' },
  { key: 'FAR', label: 'FAR 误报率', desc: '预报命中但实际未发生的比例', color: '#e6a23c' },
  { key: 'CSI', label: 'CSI 综合评分', desc: '命中评分，兼顾漏报与误报', color: '#67c23a' }
]

const kpi = ref({})
let trendChart = null
let zoneChart = null
let chlaChart = null

/** KPI 显示：0-1 小数转百分比 */
function formatKpi(v) {
  return v == null ? '-' : (v * 100).toFixed(1) + '%'
}

/** 兼容取值：后端合并结构可能为下划线或驼峰 */
function pick(obj, snakeKey, camelKey) {
  if (obj == null) return undefined
  return obj[snakeKey] ?? obj[camelKey]
}

/** 拉取看板数据并渲染全部图表 */
function getReport() {
  getDashboard({ days: days.value }).then(res => {
    const data = res.data ?? res
    kpi.value = data.kpi || {}
    renderTrend(data)
    renderZone(data)
    renderChla(data)
  })
}

/** 图 1：计划 vs 实收双线 + 偏差柱 */
function renderTrend(data) {
  const daily = data.daily || []
  const dates = daily.map(d => d.date)
  const plan = daily.map(d => pick(d, 'plan_tons', 'planTons') ?? 0)
  const actual = daily.map(d => pick(d, 'actual_tons', 'actualTons') ?? 0)
  const dev = daily.map(d => pick(d, 'abs_dev', 'absDev') ?? 0)

  if (!trendChart) trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['计划', '实收', '偏差'] },
    grid: { left: 48, right: 24, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', name: '吨' },
    series: [
      { name: '计划', type: 'line', smooth: true, data: plan, itemStyle: { color: '#409eff' } },
      { name: '实收', type: 'line', smooth: true, data: actual, itemStyle: { color: '#67c23a' } },
      { name: '偏差', type: 'bar', data: dev, barWidth: '40%', itemStyle: { color: 'rgba(230, 162, 60, 0.5)' } }
    ]
  })
}

/** 图 2：分区收藻柱状（zone_stats 字段契约未细化，做兼容取值） */
function renderZone(data) {
  const zoneStats = data.zone_stats || data.zoneStats || []
  const names = zoneStats.map(z => pick(z, 'zone_name', 'zoneName') ?? '-')
  const plan = zoneStats.map(z => pick(z, 'plan_tons', 'planTons') ?? 0)
  const actual = zoneStats.map(z => pick(z, 'actual_tons', 'actualTons') ?? 0)

  if (!zoneChart) zoneChart = echarts.init(zoneRef.value)
  zoneChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['计划', '实收'] },
    grid: { left: 48, right: 24, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: names },
    yAxis: { type: 'value', name: '吨' },
    series: [
      { name: '计划', type: 'bar', data: plan, itemStyle: { color: '#409eff' } },
      { name: '实收', type: 'bar', data: actual, itemStyle: { color: '#67c23a' } }
    ]
  })
}

/** 图 3：Chl-a 多分区折线（history: {dates, zones: {分区名: 序列}}） */
function renderChla(data) {
  const history = data.history || {}
  const dates = history.dates || data.dates || []
  const zones = history.zones || data.zones || {}
  const series = Object.keys(zones).map(name => ({
    name,
    type: 'line',
    smooth: true,
    showSymbol: false,
    data: zones[name]
  }))

  if (!chlaChart) chlaChart = echarts.init(chlaRef.value)
  chlaChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { type: 'scroll' },
    grid: { left: 48, right: 24, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', name: 'μg/L' },
    series
  })
}

const trendRef = ref(null)
const zoneRef = ref(null)
const chlaRef = ref(null)

/** 窗口尺寸变化时重绘图表 */
function handleResize() {
  trendChart?.resize()
  zoneChart?.resize()
  chlaChart?.resize()
}

onMounted(() => {
  getReport()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  zoneChart?.dispose()
  chlaChart?.dispose()
})
</script>

<style scoped lang="scss">
.mb12 { margin-bottom: 12px; }

.kpi-card {
  text-align: center;

  .kpi-label { font-size: 14px; color: #606266; }
  .kpi-value { font-size: 28px; font-weight: 600; margin: 6px 0; }
  .kpi-desc { font-size: 12px; color: #909399; }
}

.chart {
  height: 300px;
  width: 100%;

  &.chart-lg { height: 340px; }
}
</style>
