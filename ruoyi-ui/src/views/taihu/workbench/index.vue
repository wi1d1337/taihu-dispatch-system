<template>
  <div class="app-container workbench">
    <!-- 顶栏：标题 + 版本徽标 + 操作 -->
    <el-card shadow="never" class="mb12">
      <div class="toolbar">
        <div class="toolbar-title">
          <span class="title-text">调度工作台</span>
          <span class="title-date">{{ today }}</span>
          <el-tag v-if="paramVersion" size="small" type="info" effect="plain" class="ml8">
            当前版本 v{{ paramVersion }}<template v-if="autoTuneCount">（含 {{ autoTuneCount }} 次自动调参）</template>
          </el-tag>
          <el-tag v-if="modelVersion" size="small" type="info" effect="plain" class="ml8">{{ modelVersion }}</el-tag>
        </div>
        <div>
          <el-button
            type="primary"
            icon="Refresh"
            :loading="pipelineRunning"
            v-hasPermi="['taihu:pipeline:run']"
            @click="handleRunPipeline"
          >运行今日研判</el-button>
          <el-button
            type="warning"
            plain
            icon="Delete"
            :loading="seeding"
            v-hasPermi="['taihu:pipeline:run']"
            @click="handleSeed"
          >重置演示数据</el-button>
        </div>
      </div>
      <!-- 今日研判准确度行 -->
      <div class="accuracy-line">
        <template v-if="accuracy">
          <span>近 {{ accuracy.days ?? 30 }} 日研判回验：</span>
          <span class="acc-item">样本 {{ accuracy.total ?? '-' }}</span>
          <span class="acc-item ok">命中 {{ accuracy.hits ?? '-' }}</span>
          <span class="acc-item warn">误报 {{ accuracy.falseAlarms ?? '-' }}</span>
          <span class="acc-item bad">漏报 {{ accuracy.misses ?? '-' }}</span>
          <span class="acc-item strong">命中率 {{ hitRateText }}</span>
        </template>
        <span v-else class="acc-empty">研判回验数据加载中或暂无记录</span>
      </div>
    </el-card>

    <el-row :gutter="12">
      <!-- 左：六分区研判卡片 -->
      <el-col :span="13">
        <el-card shadow="never" class="mb12">
          <template #header><span>今日研判（同比外推定级 ⊕ 概率预报第二意见）</span></template>
          <div v-loading="assessLoading" class="zone-grid">
            <el-empty v-if="!assessLoading && zoneCards.length === 0" description="暂无今日研判，请先运行今日研判" />
            <div v-for="z in zoneCards" :key="z.id" class="zone-card" :style="{ borderLeftColor: z.color }">
              <div class="zone-head">
                <span class="zone-name">{{ z.name }}</span>
                <span class="level-badge" :style="{ background: z.color }">{{ z.levelText }}</span>
              </div>
              <div class="zone-row">
                <span class="z-label">预测</span>
                <span class="z-strong">{{ fmt(z.predicted) }} μg/L</span>
                <span class="z-sub">去年同周期 {{ fmt(z.yoy) }}</span>
              </div>
              <div class="zone-row">
                <span class="z-label">近7日</span>
                <span>{{ fmt(z.recent7) }} μg/L</span>
                <span :class="trendClass(z.trend)">趋势 {{ fmt(z.trend, '%') }}</span>
              </div>
              <div class="zone-row">
                <span class="z-label">超阈</span>
                <span :class="z.exceedDays > 0 ? 'z-warn' : ''">近 {{ z.exceedDays }} 天</span>
                <span class="z-label z-ml">置信</span>
                <span>{{ fmt(z.confidence) }}</span>
              </div>
              <div class="zone-row prob-row">
                <span class="z-label">概率预报</span>
                <span class="prob-cell">prob <b>{{ probText(z.prob) }}</b></span>
                <span class="prob-cell">q50 <b>{{ fmt(z.q50) }}</b> μg/L</span>
              </div>
              <div v-if="z.degraded" class="zone-degraded">同比数据缺失，回落近7日均值口径</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右：分区等级地图（Leaflet 零瓦片，色阶=研判等级） -->
      <el-col :span="11">
        <el-card shadow="never" class="map-card mb12">
          <template #header><span>分区藻情等级</span></template>
          <div ref="mapRef" class="taihu-map"></div>
          <div class="map-legend">
            <div class="legend-title">研判等级</div>
            <div v-for="(color, lv) in LEVEL_COLORS" :key="lv" class="legend-item">
              <span class="legend-swatch" :style="{ background: color }"></span>
              <span>{{ levelText(lv) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 下半部：今日工单 -->
    <el-card shadow="never">
      <template #header>
        <div class="order-header">
          <span>今日工单</span>
          <el-button link type="primary" icon="Refresh" @click="getList">刷新</el-button>
        </div>
      </template>
      <el-tabs v-model="activeStatus" @tab-change="getList">
        <el-tab-pane label="待接单" name="0" />
        <el-tab-pane label="已接单" name="1" />
        <el-tab-pane label="进行中" name="2" />
        <el-tab-pane label="待核查" name="3" />
      </el-tabs>
      <el-table v-loading="loading" :data="orderList" size="small">
        <el-table-column label="工单号" align="center" prop="orderNo" width="140" />
        <el-table-column label="分区" align="center" prop="zoneName" />
        <el-table-column label="等级" align="center" width="80">
          <template #default="scope">
            <span class="level-badge sm" :style="{ background: levelColor(scope.row.level) }">{{ levelText(scope.row.level) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="片长" align="center" prop="leaderName" />
        <el-table-column label="船只" align="center" prop="boatName" />
        <el-table-column label="时限" align="center" width="150">
          <template #default="scope">{{ fmt(scope.row.deadline) }}</template>
        </el-table-column>
        <el-table-column label="实收(吨)" align="center" prop="actualTons" width="90" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="ORDER_STATUS_TAG[scope.row.status] ?? 'info'" size="small">
              {{ ORDER_STATUS_TEXT[scope.row.status] ?? scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === '0' || scope.row.status === 0"
              link type="primary" @click="handleReassign(scope.row)"
            >改派</el-button>
            <el-button
              v-if="scope.row.status === '0' || scope.row.status === 0 || scope.row.status === '1' || scope.row.status === 1"
              link type="danger" @click="handleRevoke(scope.row)"
            >撤销</el-button>
            <el-button
              v-if="scope.row.status === '3' || scope.row.status === 3"
              link type="success" @click="handleCheck(scope.row)"
            >核查</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 改派弹窗 -->
    <el-dialog v-model="reassignVisible" title="改派工单" width="420px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="新片长">
          <el-select v-model="reassignLeaderId" placeholder="选择片长" style="width: 100%">
            <el-option v-for="w in leaders" :key="w.workerId" :label="`${w.workerName}${w.zoneName ? '（' + w.zoneName + '）' : ''}`" :value="w.workerId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reassignVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!reassignLeaderId" @click="submitReassign">确定</el-button>
      </template>
    </el-dialog>

    <!-- 核查弹窗 -->
    <el-dialog v-model="checkVisible" title="核查工单" width="460px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="核查结论">
          <el-radio-group v-model="checkForm.pass">
            <el-radio :value="true">通过（办结）</el-radio>
            <el-radio :value="false">退回（整改重报）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="核查意见">
          <el-input v-model="checkForm.comment" type="textarea" :rows="2" maxlength="200"
            :placeholder="checkForm.pass ? '选填' : '退回原因（建议填写）'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCheck">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TaihuWorkbench">
import { listOrder, revokeOrder, reassignOrder, checkOrder } from '@/api/taihu/orders'
import { listWorker } from '@/api/taihu/workers'
import { getAssessToday, getAssessAccuracy } from '@/api/taihu/assess'
import { getAutotuneHistory } from '@/api/taihu/params'
import { getGeojson } from '@/api/taihu/zones'
import { runPipeline, seedDemoData } from '@/api/taihu/pipeline'
import { LEVEL_COLORS, ORDER_STATUS_TEXT, ORDER_STATUS_TAG, pick, levelColor, levelText, fmt } from '@/utils/taihu'
import { ElMessage, ElMessageBox } from 'element-plus'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const { proxy } = getCurrentInstance()

const today = proxy.parseTime(new Date(), '{y}-{m}-{d}')

/** ---------- 今日研判面板 ---------- */
const assessLoading = ref(false)
const zoneCards = ref([])
const paramVersion = ref(null)
const modelVersion = ref(null)
const autoTuneCount = ref(0)
const accuracy = ref(null)

const hitRateText = computed(() => {
  const r = accuracy.value?.hitRate
  if (r == null) return '-'
  return (r <= 1 ? (r * 100).toFixed(1) : r) + '%'
})

/** 取当前工单行状态为字符串口径 */
function rowStatus(v) {
  return v == null ? '' : String(v)
}

function trendClass(t) {
  if (t == null) return 'z-sub'
  return t > 0 ? 'z-warn' : 'z-ok'
}

function probText(p) {
  if (p == null || p === '') return '-'
  return (p <= 1 ? (p * 100).toFixed(0) : p) + '%'
}

/** 研判 today 数据容错解析：zones 可能是数组或对象 map */
function parseToday(data) {
  paramVersion.value = pick(data, 'param_version', 'paramVersion') ?? null
  modelVersion.value = pick(data, 'model_version', 'modelVersion') ?? null
  let zones = data.zones ?? data.zone_list ?? []
  if (!Array.isArray(zones)) zones = Object.values(zones || {})
  zoneCards.value = zones.map(z => {
    const basis = z.basis ? (typeof z.basis === 'string' ? safeJson(z.basis) : z.basis) : {}
    const level = z.level
    return {
      id: pick(z, 'zone_id', 'zoneId') ?? z.name,
      name: pick(z, 'zone_name', 'zoneName') ?? z.name ?? '-',
      level,
      levelText: levelText(level),
      color: levelColor(level),
      predicted: pick(z, 'predicted', 'predicted') ?? basis.predicted,
      yoy: pick(z, 'yoy', 'yoy') ?? basis.yoy,
      recent7: pick(z, 'recent7', 'recent7') ?? basis.recent7,
      trend: pick(z, 'trend', 'trend') ?? basis.trend,
      exceedDays: pick(z, 'exceed_days', 'exceedDays') ?? basis.exceed_days ?? basis.exceedDays ?? 0,
      confidence: pick(z, 'confidence', 'confidence'),
      prob: pick(z, 'prob', 'prob'),
      q50: pick(z, 'q50', 'q50'),
      degraded: basis.degraded === true
    }
  })
}

function safeJson(s) {
  try { return JSON.parse(s) } catch { return {} }
}

function loadToday() {
  assessLoading.value = true
  getAssessToday().then(res => {
    const data = res.data ?? res
    if (data) parseToday(data)
    // 回验汇总：today 内置优先，否则另拉 accuracy
    const acc = pick(data || {}, 'accuracy', 'accuracy')
    if (acc && (acc.hits != null || acc.hitRate != null)) {
      accuracy.value = acc
      assessLoading.value = false
    } else {
      return getAssessAccuracy({ days: 30 }).then(r2 => {
        accuracy.value = r2.data ?? r2
      }).finally(() => { assessLoading.value = false })
    }
  }).catch(() => { assessLoading.value = false })
}

/** 自动调参次数（版本徽标用，失败不影响页面） */
function loadAutoTuneCount() {
  getAutotuneHistory().then(res => {
    const data = res.data ?? res
    const logs = data.logs ?? data.changes ?? data.adjustments ?? data.records ?? []
    autoTuneCount.value = logs.filter(l => String(l.trigger ?? '') === '2').length
  }).catch(() => {})
}

/** ---------- 今日工单 ---------- */
const orderList = ref([])
const loading = ref(false)
const activeStatus = ref('0')
const pipelineRunning = ref(false)
const seeding = ref(false)

function getList() {
  loading.value = true
  listOrder({ pageNum: 1, pageSize: 50, date: today, status: activeStatus.value }).then(res => {
    orderList.value = res.rows || []
    loading.value = false
  }).catch(() => { loading.value = false })
}

/** 撤销：原因必填 */
function handleRevoke(row) {
  ElMessageBox.prompt(`撤销工单 ${row.orderNo}（${row.zoneName}），请填写撤销原因`, '撤销', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /\S/,
    inputErrorMessage: '撤销原因不能为空'
  }).then(({ value }) => {
    return revokeOrder(row.orderId, value)
  }).then(() => {
    ElMessage.success('已撤销')
    refreshAll()
  }).catch(() => {})
}

/** 改派：选片长 */
const reassignVisible = ref(false)
const reassignLeaderId = ref(null)
const reassignRow = ref(null)
const leaders = ref([])

function loadLeaders() {
  listWorker({ pageNum: 1, pageSize: 100, role: '1' }).then(res => {
    leaders.value = res.rows ?? res.data ?? []
  }).catch(() => {})
}

function handleReassign(row) {
  reassignRow.value = row
  reassignLeaderId.value = null
  reassignVisible.value = true
  if (leaders.value.length === 0) loadLeaders()
}

function submitReassign() {
  reassignOrder(reassignRow.value.orderId, reassignLeaderId.value).then(() => {
    ElMessage.success('已改派，工单回到待接单')
    reassignVisible.value = false
    refreshAll()
  })
}

/** 核查：通过/退回 */
const checkVisible = ref(false)
const checkRow = ref(null)
const checkForm = reactive({ pass: true, comment: '' })

function handleCheck(row) {
  checkRow.value = row
  checkForm.pass = true
  checkForm.comment = ''
  checkVisible.value = true
}

function submitCheck() {
  if (!checkForm.pass && !checkForm.comment) {
    ElMessage.warning('退回请填写核查意见')
    return
  }
  checkOrder(checkRow.value.orderId, checkForm.pass, checkForm.comment || undefined).then(() => {
    ElMessage.success(checkForm.pass ? '已核查办结' : '已退回')
    checkVisible.value = false
    refreshAll()
  })
}

/** 运行今日研判 */
function handleRunPipeline() {
  proxy.$modal.confirm('运行今日研判并自动派单（达标分区自动建工单），是否继续？').then(() => {
    pipelineRunning.value = true
    return runPipeline({ date: today })
  }).then(() => {
    ElMessage.success('今日研判已完成')
    refreshAll()
  }).catch(() => {}).finally(() => { pipelineRunning.value = false })
}

/** 重置演示数据 */
function handleSeed() {
  proxy.$modal.confirm('将重置全部演示数据（两年历史、人员船只、工单与研判），是否继续？').then(() => {
    seeding.value = true
    return seedDemoData()
  }).then(() => {
    ElMessage.success('演示数据已重置')
    refreshAll()
    loadToday()
  }).catch(() => {}).finally(() => { seeding.value = false })
}

function refreshAll() {
  getList()
  loadGeo()
}

/** ---------- Leaflet 地图（零瓦片 + 等级色阶） ---------- */
const mapRef = ref(null)
let map = null
let geoLayer = null

function styleByLevel(props) {
  return {
    fillColor: levelColor(props.level),
    fillOpacity: 0.9,
    color: '#7a8794',
    weight: 1
  }
}

function popupHtml(props) {
  const name = props.zone_name ?? props.zoneName ?? ''
  const lv = props.level
  return `
    <div class="zone-popup">
      <div class="zone-popup-title">${name}</div>
      <div>研判等级：${levelText(lv)}</div>
      <div>预测 Chl-a：${props.predicted ?? '-'} μg/L</div>
      <div>概率中位数 q50：${props.q50 ?? '-'}</div>
    </div>`
}

function initMap() {
  map = L.map(mapRef.value, {
    attributionControl: true,
    zoomControl: true
  }).setView([31.2, 120.1], 9)
  map.attributionControl.setPrefix('太湖分区示意')
  loadGeo()
}

function loadGeo() {
  getGeojson().then(res => {
    const fc = res.data ?? res
    if (!fc || !fc.features) return
    if (geoLayer) map.removeLayer(geoLayer)
    geoLayer = L.geoJSON(fc, {
      style: feature => styleByLevel(feature.properties || {}),
      onEachFeature: (feature, layer) => {
        const props = feature.properties || {}
        layer.bindPopup(popupHtml(props))
        layer.on('mouseover', () => layer.setStyle({ weight: 3, color: '#42525f' }))
        layer.on('mouseout', () => layer.setStyle(styleByLevel(props)))
      }
    }).addTo(map)
    map.fitBounds(geoLayer.getBounds(), { padding: [16, 16] })
  }).catch(() => {})
}

onMounted(() => {
  loadToday()
  loadAutoTuneCount()
  getList()
  loadLeaders()
  initMap()
})

onBeforeUnmount(() => {
  if (map) {
    map.remove()
    map = null
  }
})
</script>

<style scoped lang="scss">
.workbench {
  .mb12 { margin-bottom: 12px; }
  .ml8 { margin-left: 8px; }

  .toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .toolbar-title {
      display: flex;
      align-items: baseline;
      gap: 12px;
      .title-text { font-size: 16px; font-weight: 600; }
      .title-date { font-size: 13px; color: #909399; }
    }
  }

  .accuracy-line {
    margin-top: 10px;
    padding: 6px 10px;
    background: #f5f7fa;
    border-radius: 4px;
    font-size: 13px;
    color: #606266;
    display: flex;
    gap: 16px;
    flex-wrap: wrap;

    .ok { color: #67c23a; }
    .warn { color: #e6a23c; }
    .bad { color: #f56c6c; }
    .strong { color: #303133; font-weight: 600; }
    .acc-empty { color: #909399; }
  }

  /* 六分区卡片 */
  .zone-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
    min-height: 320px;
  }

  .zone-card {
    border: 1px solid #ebeef5;
    border-left: 4px solid #b9bfc9;
    border-radius: 4px;
    padding: 10px 12px;

    .zone-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6px;

      .zone-name { font-weight: 600; font-size: 14px; }
    }

    .zone-row {
      display: flex;
      align-items: baseline;
      gap: 6px;
      font-size: 12.5px;
      color: #606266;
      line-height: 22px;

      .z-label { color: #909399; width: 52px; flex: none; }
      .z-strong { font-weight: 600; color: #303133; }
      .z-sub { color: #909399; }
      .z-warn { color: #e6a23c; }
      .z-ok { color: #67c23a; }
      .z-ml { margin-left: 8px; width: auto; }
    }

    .prob-row {
      margin-top: 4px;
      padding-top: 4px;
      border-top: 1px dashed #ebeef5;
      .prob-cell b { color: #409eff; }
    }

    .zone-degraded {
      margin-top: 4px;
      font-size: 12px;
      color: #e6a23c;
    }
  }

  .level-badge {
    display: inline-block;
    padding: 1px 8px;
    border-radius: 3px;
    color: #fff;
    font-size: 12px;
    line-height: 18px;

    &.sm { padding: 0 6px; }
  }

  /* 地图 */
  .map-card {
    :deep(.el-card__body) { position: relative; }

    .taihu-map {
      height: 460px;
      background: #dcecec;
      z-index: 0;
    }

    .map-legend {
      position: absolute;
      right: 20px;
      bottom: 24px;
      padding: 8px 12px;
      background: rgba(255, 255, 255, 0.92);
      border: 1px solid #e4e7ed;
      border-radius: 4px;
      font-size: 12px;
      line-height: 20px;
      z-index: 500;

      .legend-title { font-weight: 600; margin-bottom: 2px; }
      .legend-item { display: flex; align-items: center; gap: 6px; }
      .legend-swatch {
        width: 16px;
        height: 10px;
        display: inline-block;
        border: 1px solid #cfd8dc;
      }
    }
  }

  .order-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
}

/* Leaflet 弹窗（渲染在地图容器内，需非 scoped） */
.zone-popup {
  .zone-popup-title { font-weight: 600; margin-bottom: 4px; }
  div { line-height: 20px; font-size: 13px; }
}
</style>
