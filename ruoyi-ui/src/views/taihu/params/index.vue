<template>
  <div class="app-container">
    <!-- 说明 -->
    <el-alert type="info" :closable="false" class="mb12"
      title="算法参数透明化：此处为研判引擎的参数注册表，修改即时生效于下一轮研判；全部变更（人工与自动调参）留痕可溯。" />

    <!-- 自动调参区块 -->
    <el-card shadow="never" class="mb12">
      <template #header>
        <div class="card-head">
          <span>自动调参</span>
          <div>
            <el-tag size="small" :type="autoEnabled ? 'success' : 'info'" class="mr8">
              {{ autoEnabled ? '自动调参已开启' : '自动调参已关闭' }}
            </el-tag>
            <el-tag size="small" type="warning" effect="plain">模式：{{ autoModeText }}</el-tag>
          </div>
        </div>
      </template>
      <el-row :gutter="12" align="middle">
        <el-col :span="16">
          <div class="auto-desc">
            每日研判管线末段自动回测近 {{ autoParams['autotune.window_days'] ?? '-' }} 天研判 vs 实际，
            对同比权重与分级阈值做有界候选搜索；单步幅度 ≤ {{ autoParams['autotune.max_step_pct'] ?? '-' }}%，
            改进 ≥ {{ autoParams['autotune.min_gain'] ?? '-' }} 个百分点才生效（当前模式：
            {{ autoModeText }}）。五个 autotune.* 参数同在下方参数表内可调。
          </div>
        </el-col>
        <el-col :span="8" style="text-align: right">
          <el-button type="primary" :loading="tuning" v-hasPermi="['taihu:param:edit']" @click="handleRunAutotune">
            立即调参
          </el-button>
          <el-button icon="Refresh" @click="loadAll">刷新</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 参数表 -->
    <el-card shadow="never" class="mb12">
      <template #header><span>算法参数注册表</span></template>
      <el-table v-loading="loading" :data="paramList" size="small">
        <el-table-column label="参数 Key" align="left" prop="paramKey" min-width="170">
          <template #default="scope">
            <span>{{ pick(scope.row, 'param_key', 'paramKey') }}</span>
            <el-tag v-if="isAutoKey(pick(scope.row, 'param_key', 'paramKey'))" size="small" type="warning" class="ml8">调参</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前值" align="center" width="100">
          <template #default="scope"><b>{{ pick(scope.row, 'param_value', 'paramValue') }}</b></template>
        </el-table-column>
        <el-table-column label="区间" align="center" width="120">
          <template #default="scope">
            [{{ pick(scope.row, 'min_value', 'minValue') ?? '-' }}, {{ pick(scope.row, 'max_value', 'maxValue') ?? '-' }}]
          </template>
        </el-table-column>
        <el-table-column label="单位" align="center" width="70">
          <template #default="scope">{{ pick(scope.row, 'unit', 'unit') ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="业务含义" align="left" min-width="200" show-overflow-tooltip>
          <template #default="scope">{{ pick(scope.row, 'meaning', 'meaning') ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="更新人" align="center" width="90">
          <template #default="scope">{{ pick(scope.row, 'updated_by', 'updatedBy') ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="版本" align="center" width="70">
          <template #default="scope">v{{ pick(scope.row, 'version', 'version') ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="90">
          <template #default="scope">
            <el-button link type="primary" v-hasPermi="['taihu:param:edit']" @click="handleEdit(scope.row)">调整</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 调参历史 -->
    <el-card shadow="never">
      <template #header><span>调参历史（参数值与命中率随时间）</span></template>
      <div ref="chartRef" class="history-chart"></div>

      <div class="section-title">调整证据（人工与自动同列）</div>
      <el-table :data="historyLogs" size="small" border>
        <el-table-column label="时间" align="center" width="150">
          <template #default="s">{{ pick(s.row, 'created_at', 'createdAt') ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="参数" align="left" min-width="150">
          <template #default="s">{{ pick(s.row, 'param_key', 'paramKey') ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="旧值 → 新值" align="center" width="130">
          <template #default="s">{{ pick(s.row, 'old_value', 'oldValue') ?? '-' }} → <b>{{ pick(s.row, 'new_value', 'newValue') ?? '-' }}</b></template>
        </el-table-column>
        <el-table-column label="触发方式" align="center" width="110">
          <template #default="s">
            <el-tag size="small" :type="triggerTag(s.row)">{{ triggerText(s.row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="窗口命中率（前→后）" align="center" width="150">
          <template #default="s">{{ hitBefore(s.row) }} → {{ hitAfter(s.row) }}</template>
        </el-table-column>
        <el-table-column label="理由 / 证据" align="left" min-width="240" show-overflow-tooltip>
          <template #default="s">{{ evidenceText(s.row) }}</template>
        </el-table-column>
        <el-table-column label="操作人" align="center" width="90">
          <template #default="s">{{ pick(s.row, 'operator', 'operator') ?? '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 行内调整弹窗 -->
    <el-dialog v-model="editVisible" title="调整参数" width="440px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="参数 Key">
          <span class="mono">{{ editingKey }}</span>
        </el-form-item>
        <el-form-item label="业务含义">
          <span class="meaning">{{ editingMeaning }}</span>
        </el-form-item>
        <el-form-item label="新值">
          <el-input-number
            v-model="editForm.value"
            :min="editForm.min" :max="editForm.max" :step="editForm.step"
            style="width: 200px"
          />
          <span class="range-tip">允许区间 [{{ editForm.min ?? '-∞' }}, {{ editForm.max ?? '+∞' }}]{{ editingUnit }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存（版本递增）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TaihuParams">
import { listParams, updateParam, runAutotune, getAutotuneHistory } from '@/api/taihu/params'
import { pick } from '@/utils/taihu'
import { ElMessage, ElMessageBox } from 'element-plus'
import useUserStore from '@/store/modules/user'
import * as echarts from 'echarts'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const paramList = ref([])
const tuning = ref(false)

/** autotune.* 参数快照（区块展示用） */
const autoParams = ref({})
const autoEnabled = computed(() => String(autoParams.value['autotune.enabled'] ?? '1') === '1')
const autoModeText = computed(() => {
  const m = String(autoParams.value['autotune.mode'] ?? '2')
  return m === '1' ? '建议（人工确认后生效）' : '自动（区间内直接生效）'
})

const isAutoKey = key => String(key || '').startsWith('autotune.')

/** 拉取参数表并解析 autotune.* 快照 */
function loadParams() {
  loading.value = true
  listParams().then(res => {
    const data = res.data ?? res
    let list = Array.isArray(data) ? data : (data.params ?? data.items ?? [])
    paramList.value = list
    const map = {}
    list.forEach(p => {
      const k = pick(p, 'param_key', 'paramKey')
      if (isAutoKey(k)) map[k] = pick(p, 'param_value', 'paramValue')
    })
    autoParams.value = map
  }).finally(() => { loading.value = false })
}

/** ---------- 行内调整 ---------- */
const editVisible = ref(false)
const editingKey = ref('')
const editingMeaning = ref('')
const editingUnit = ref('')
const editForm = reactive({ value: 0, min: undefined, max: undefined, step: 1 })

function handleEdit(row) {
  const key = pick(row, 'param_key', 'paramKey')
  const v = pick(row, 'param_value', 'paramValue')
  const min = row.min_value ?? row.minValue
  const max = row.max_value ?? row.maxValue
  editingKey.value = key
  editingMeaning.value = pick(row, 'meaning', 'meaning') ?? '-'
  editingUnit.value = row.unit ? `（${row.unit}）` : ''
  editForm.value = Number(v)
  editForm.min = min == null ? undefined : Number(min)
  editForm.max = max == null ? undefined : Number(max)
  editForm.step = Number(v) < 1 ? 0.1 : 1
  editVisible.value = true
}

function submitEdit() {
  const { min, max, value } = editForm
  if (value == null || Number.isNaN(value)) {
    ElMessage.warning('请输入有效数值')
    return
  }
  if ((min != null && value < min) || (max != null && value > max)) {
    ElMessage.warning(`数值超出允许区间 [${min ?? '-∞'}, ${max ?? '+∞'}]`)
    return
  }
  updateParam(editingKey.value, { value, operator: useUserStore().name }).then(() => {
    ElMessage.success('参数已更新，下一轮研判生效')
    editVisible.value = false
    loadAll()
  })
}

/** ---------- 立即调参 ---------- */
function handleRunAutotune() {
  ElMessageBox.confirm('立即执行一轮自动调参（回测近窗口研判，有界调整参数），是否继续？', '立即调参', {
    type: 'warning'
  }).then(() => {
    tuning.value = true
    return runAutotune()
  }).then(res => {
    const data = res.data ?? res
    const adjusted = data.adjusted ?? data.changes ?? data.applied ?? data.params ?? []
    if (Array.isArray(adjusted) && adjusted.length > 0) {
      const lines = adjusted.map(a =>
        `${a.paramKey ?? a.param_key ?? a.key ?? '-'}：${a.oldValue ?? a.old_value ?? '-'} → ${a.newValue ?? a.new_value ?? '-'}`
      ).join('；')
      ElMessageBox.alert(lines, '本轮已调整参数', { confirmButtonText: '知道了' })
    } else {
      ElMessage.info('本轮回验无改进，参数已跳过')
    }
    loadAll()
  }).catch(e => {
    if (e !== 'cancel') console.error(e)
  }).finally(() => { tuning.value = false })
}

/** ---------- 调参历史 ---------- */
const historyLogs = ref([])
let chart = null
const chartRef = ref(null)

const TRIGGER_TEXT = { 1: '人工', 2: '自动', 3: '建议' }
const triggerText = row => TRIGGER_TEXT[pick(row, 'trigger', 'trigger')] ?? (pick(row, 'trigger', 'trigger') ?? '-')

function triggerTag(row) {
  const t = String(pick(row, 'trigger', 'trigger'))
  return t === '2' ? 'warning' : (t === '3' ? 'info' : 'primary')
}

/** evidence 里取命中率前后（容错多种字段名） */
function hitBefore(row) {
  const ev = rowEvidence(row)
  const v = ev.hit_rate_before ?? ev.hitRateBefore ?? ev.hit_before ?? ev.hitBefore
  return pct(v)
}

function hitAfter(row) {
  const ev = rowEvidence(row)
  const v = ev.hit_rate_after ?? ev.hitRateAfter ?? ev.hit_after ?? ev.hitAfter
  return pct(v)
}

function rowEvidence(row) {
  const ev = pick(row, 'evidence', 'evidence')
  if (ev == null) return {}
  return typeof ev === 'string' ? safeJson(ev) : ev
}

function pct(v) {
  if (v == null || v === '') return '-'
  return (v <= 1 ? (v * 100).toFixed(1) : v) + '%'
}

function evidenceText(row) {
  const ev = rowEvidence(row)
  return ev.reason ?? ev.adjust_reason ?? ev.rationale ?? pick(row, 'reason', 'reason') ?? '-'
}

function safeJson(s) {
  try { return JSON.parse(s) } catch { return {} }
}

/** 历史曲线：优先后端 series；否则由日志重建阶梯线 */
function renderHistory(data) {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  let dates = data.dates ?? []
  let series = []
  let hitSeries = null
  const rawSeries = data.series ?? data.paramSeries ?? null

  if (rawSeries && typeof rawSeries === 'object') {
    const keys = Object.keys(rawSeries)
    // x 轴取所有键的日期并集
    const dateSet = new Set()
    keys.forEach(k => (rawSeries[k] || []).forEach(p => dateSet.add(p.date ?? p.date_ ?? p[0])))
    dates = [...dateSet].sort()
    keys.forEach(k => {
      const map = new Map((rawSeries[k] || []).map(p => [p.date ?? p.date_ ?? p[0], p.value ?? p[1]]))
      let last = null
      series.push({
        name: k,
        type: 'line',
        step: 'end',
        connectNulls: true,
        showSymbol: (rawSeries[k] || []).length <= 30,
        data: dates.map(d => (map.has(d) ? (last = map.get(d)) : last))
      })
    })
  } else if (historyLogs.value.length > 0) {
    // 由日志重建：每个参数一条阶梯线，值=调整后的值
    dates = [...new Set(historyLogs.value.map(l => (pick(l, 'created_at', 'createdAt') ?? '').slice(0, 10)).filter(Boolean))].sort()
    const byKey = {}
    historyLogs.value.forEach(l => {
      const k = pick(l, 'param_key', 'paramKey')
      const d = (pick(l, 'created_at', 'createdAt') ?? '').slice(0, 10)
      if (!k || !d) return
      ;(byKey[k] = byKey[k] || []).push([d, Number(pick(l, 'new_value', 'newValue'))])
    })
    Object.keys(byKey).forEach(k => {
      const map = new Map(byKey[k])
      let last = byKey[k][0] ? byKey[k][0][1] - (byKey[k][0][1] || 1) * 0 : null
      series.push({
        name: k,
        type: 'line',
        step: 'end',
        connectNulls: true,
        data: dates.map(d => {
          if (map.has(d)) last = map.get(d)
          return last
        })
      })
    })
  }

  // 命中率曲线（副轴）
  const rawHit = data.hitSeries ?? data.hit_series ?? data.hitRate ?? null
  if (Array.isArray(rawHit) && rawHit.length > 0) {
    if (rawHit[0] && typeof rawHit[0] === 'object') {
      if (!dates.length) dates = rawHit.map(p => p.date)
      const map = new Map(rawHit.map(p => [p.date, p.hitRate ?? p.hit_rate]))
      hitSeries = {
        name: '窗口命中率',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: dates.map(d => (map.has(d) ? map.get(d) : null))
      }
    } else {
      hitSeries = { name: '窗口命中率', type: 'line', yAxisIndex: 1, smooth: true, data: rawHit }
    }
  }

  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { type: 'scroll', top: 0 },
    grid: { left: 56, right: 56, top: 36, bottom: 40, containLabel: false },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: '参数值', scale: true },
      { type: 'value', name: '命中率', scale: true, axisLabel: { formatter: v => (v <= 1 ? (v * 100).toFixed(0) + '%' : v) } }
    ],
    dataZoom: dates.length > 40 ? [{ type: 'inside' }, { type: 'slider', height: 16, bottom: 6 }] : [],
    series: hitSeries ? [...series, hitSeries] : series
  }, true)
}

function loadHistory() {
  getAutotuneHistory().then(res => {
    const data = res.data ?? res
    historyLogs.value = data.logs ?? data.changes ?? data.adjustments ?? data.records ?? []
    // 日志按时间倒序展示（表格），曲线用正序
    renderHistory(data)
  }).catch(() => {
    historyLogs.value = []
  })
}

function loadAll() {
  loadParams()
  loadHistory()
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped lang="scss">
.mb12 { margin-bottom: 12px; }
.mr8 { margin-right: 8px; }
.ml8 { margin-left: 8px; }

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.auto-desc {
  font-size: 13px;
  color: #606266;
  line-height: 20px;
}

.history-chart {
  height: 320px;
  width: 100%;
}

.section-title {
  margin: 16px 0 8px;
  font-weight: 600;
  font-size: 14px;
}

.mono { font-family: Consolas, Monaco, monospace; }
.meaning { color: #606266; }
.range-tip { margin-left: 12px; font-size: 12px; color: #909399; }
</style>
