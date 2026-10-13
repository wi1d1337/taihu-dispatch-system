<template>
  <div class="app-container">
    <!-- 工具条：统计窗口 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="12">
        <el-radio-group v-model="days" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button :value="90">近 90 天</el-radio-button>
        </el-radio-group>
      </el-col>
      <el-col :span="12" style="text-align: right">
        <el-button icon="Refresh" @click="load">刷新</el-button>
      </el-col>
    </el-row>

    <!-- KPI：办结率 / 日生日清率 / 超时督办次数 / 平均办结时长 -->
    <el-row :gutter="12" class="mb12">
      <el-col :span="6" v-for="item in kpiList" :key="item.label">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">{{ item.label }}</div>
            <div class="kpi-value" :style="{ color: item.color }">{{ item.value }}</div>
            <div class="kpi-desc">{{ item.desc }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 响应时长三段均值 -->
    <el-card shadow="never" class="mb12">
      <template #header><span>响应时长（派单起算，均值 · 小时）</span></template>
      <el-row :gutter="12">
        <el-col :span="8" v-for="seg in respSegments" :key="seg.label">
          <div class="resp-seg">
            <div class="seg-label">{{ seg.label }}</div>
            <div class="seg-value">{{ seg.value }}</div>
            <div class="seg-sub">{{ seg.desc }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never" class="mb12">
          <template #header><span>响应时长分布（直方）</span></template>
          <div v-if="hasHist" ref="histRef" class="chart"></div>
          <el-empty v-else description="暂无响应时长分布数据" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="mb12">
          <template #header><span>各分区打捞吨数（吨）</span></template>
          <div ref="zoneRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="TaihuDashboard">
import { getDashboard } from '@/api/taihu/dashboard'
import { pick } from '@/utils/taihu'
import * as echarts from 'echarts'

const days = ref(30)

/** 汇总原始对象（宽口径解析） */
const evalData = ref({})

const num = v => (v == null || v === '' || Number.isNaN(Number(v)) ? null : Number(v))
const pctText = v => { const n = num(v); return n == null ? '-' : (n <= 1 ? (n * 100).toFixed(1) : n.toFixed(1)) + '%' }
const hText = v => { const n = num(v); return n == null ? '-' : n.toFixed(1) + ' h' }

const kpiList = computed(() => [
  { label: '办结率', value: pctText(evalData.value.finishRate), desc: '已办结 / 全部工单', color: '#409eff' },
  { label: '日生日清率', value: pctText(evalData.value.sameDayRate), desc: '当日工单当日办结占比', color: '#67c23a' },
  { label: '超时督办次数', value: evalData.value.overdueCount ?? '-', desc: '催办与升级督办累计', color: '#e6a23c' },
  { label: '平均办结时长', value: hText(evalData.value.avgFinishH), desc: '派单 → 办结', color: '#909399' }
])

const respSegments = computed(() => [
  { label: '派单 → 接单', value: hText(evalData.value.avgAcceptH), desc: '片长响应速度' },
  { label: '接单 → 到场', value: hText(evalData.value.avgArriveH), desc: '到场打卡口径' },
  { label: '派单 → 办结', value: hText(evalData.value.avgFinishH), desc: '全链路处置时长' }
])

const hasHist = ref(false)
let histChart = null
let zoneChart = null
const histRef = ref(null)
const zoneRef = ref(null)

/** 响应时长分布直方（容器多种命名/形态） */
function parseHist(data) {
  let d = data.respDistribution ?? data.resp_distribution ?? data.distribution ?? data.respHist ?? null
  if (!d) return null
  if (Array.isArray(d)) {
    if (d.length === 0) return null
    if (typeof d[0] === 'object') {
      return {
        labels: d.map(x => x.label ?? x.bucket ?? x.range ?? '-'),
        counts: d.map(x => x.count ?? x.value ?? x.num ?? 0)
      }
    }
    return null
  }
  if (typeof d === 'object') {
    const labels = d.labels ?? d.buckets ?? []
    const counts = d.counts ?? d.values ?? []
    if (labels.length && counts.length) return { labels, counts }
  }
  return null
}

/** 分区吨数（zoneTons / zone_tons / zoneStats） */
function parseZoneTons(data) {
  let list = data.zoneTons ?? data.zone_tons ?? data.zoneStats ?? data.zone_stats ?? []
  if (!Array.isArray(list)) return []
  return list.map(z => ({
    name: pick(z, 'zone_name', 'zoneName') ?? z.name ?? '-',
    tons: num(pick(z, 'tons', 'tons') ?? pick(z, 'actual_tons', 'actualTons') ?? z.actual) ?? 0
  }))
}

function renderHist(hist) {
  if (!hist || !histRef.value) { hasHist.value = false; return }
  hasHist.value = true
  if (!histChart) histChart = echarts.init(histRef.value)
  histChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 24, bottom: 28 },
    xAxis: { type: 'category', data: hist.labels },
    yAxis: { type: 'value', name: '单数' },
    series: [{ type: 'bar', data: hist.counts, barWidth: '55%', itemStyle: { color: '#409eff' } }]
  }, true)
  nextTick(() => histChart.resize())
}

function renderZone(list) {
  if (!zoneRef.value) return
  if (!zoneChart) zoneChart = echarts.init(zoneRef.value)
  zoneChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 24, bottom: 28 },
    xAxis: { type: 'category', data: list.map(z => z.name) },
    yAxis: { type: 'value', name: '吨' },
    series: [{ type: 'bar', data: list.map(z => z.tons), barWidth: '45%', itemStyle: { color: '#67c23a' } }]
  }, true)
}

function load() {
  getDashboard({ days: days.value }).then(res => {
    const data = res.data ?? res
    const s = data.summary ?? data.kpi ?? {}
    // 扁平化汇总：summary 优先，顶层兜底
    evalData.value = {
      finishRate: pick(s, 'finish_rate', 'finishRate') ?? pick(data, 'finish_rate', 'finishRate'),
      sameDayRate: pick(s, 'same_day_rate', 'sameDayRate') ?? pick(data, 'same_day_rate', 'sameDayRate'),
      overdueCount: pick(s, 'overdue_count', 'overdueCount') ?? pick(data, 'overdue_count', 'overdueCount'),
      avgAcceptH: pick(s, 'avg_accept_h', 'avgAcceptH') ?? pick(data, 'avg_accept_h', 'avgAcceptH'),
      avgArriveH: pick(s, 'avg_arrive_h', 'avgArriveH') ?? pick(data, 'avg_arrive_h', 'avgArriveH'),
      avgFinishH: pick(s, 'avg_finish_h', 'avgFinishH') ?? pick(data, 'avg_finish_h', 'avgFinishH')
    }
    renderHist(parseHist(data))
    renderZone(parseZoneTons(data))
  })
}

function handleResize() {
  histChart?.resize()
  zoneChart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  histChart?.dispose()
  zoneChart?.dispose()
})
</script>

<style scoped lang="scss">
.mb12 { margin-bottom: 12px; }
.mb8 { margin-bottom: 8px; }

.kpi-card {
  text-align: center;
  .kpi-label { font-size: 14px; color: #606266; }
  .kpi-value { font-size: 26px; font-weight: 600; margin: 6px 0 2px; }
  .kpi-desc { font-size: 12px; color: #909399; }
}

.resp-seg {
  text-align: center;
  padding: 8px 0;
  background: #f5f7fa;
  border-radius: 4px;

  .seg-label { font-size: 13px; color: #606266; }
  .seg-value { font-size: 24px; font-weight: 600; color: #303133; margin: 4px 0; }
  .seg-sub { font-size: 12px; color: #909399; }
}

.chart {
  height: 300px;
  width: 100%;
}
</style>
