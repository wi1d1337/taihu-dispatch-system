<template>
  <div class="app-container workbench">
    <!-- 顶栏：操作入口 -->
    <el-card shadow="never" class="mb12">
      <div class="toolbar">
        <div class="toolbar-title">
          <span class="title-text">调度工作台</span>
          <span class="title-date">{{ today }}</span>
        </div>
        <div>
          <el-button
            type="primary"
            icon="Refresh"
            :loading="pipelineRunning"
            v-hasPermi="['taihu:pipeline:run']"
            @click="handleRunPipeline"
          >生成今日调度单</el-button>
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
    </el-card>

    <el-row :gutter="12">
      <!-- 左：分区概率地图（GeoJSON 零瓦片） -->
      <el-col :span="14">
        <el-card shadow="never" class="map-card">
          <template #header><span>分区水华概率（72h）</span></template>
          <div ref="mapRef" class="taihu-map"></div>
          <!-- 五级色阶图例 -->
          <div class="map-legend">
            <div class="legend-title">水华概率</div>
            <div v-for="item in legendItems" :key="item.label" class="legend-item">
              <span class="legend-swatch" :style="{ background: item.color }"></span>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右：今日调度单（按状态） -->
      <el-col :span="10">
        <el-card shadow="never" class="order-card">
          <template #header>
            <div class="order-header">
              <span>今日调度单</span>
              <el-button link type="primary" icon="Refresh" @click="getList">刷新</el-button>
            </div>
          </template>
          <el-tabs v-model="activeStatus" @tab-change="getList">
            <el-tab-pane label="待确认" name="0" />
            <el-tab-pane label="已确认" name="1" />
            <el-tab-pane label="已完成" name="3" />
            <el-tab-pane label="已驳回" name="2" />
          </el-tabs>
          <div v-loading="loading" class="order-list">
            <el-empty v-if="!loading && orderList.length === 0" description="暂无调度单" />
            <div v-for="row in orderList" :key="row.orderId" class="order-item">
              <div class="order-main">
                <span class="order-boat">{{ row.boatName }}</span>
                <el-tag size="small" type="info">{{ row.zoneName }}</el-tag>
                <el-tag size="small" :type="row.halfDay === '0' ? '' : 'success'">
                  {{ row.halfDay === '0' ? '上午' : '下午' }}
                </el-tag>
              </div>
              <div class="order-meta">
                <span>计划 {{ formatTons(row.planTons) }} 吨</span>
                <span v-if="row.stationName"> → {{ row.stationName }}</span>
                <span v-if="row.actualTons != null" class="order-actual">实收 {{ formatTons(row.actualTons) }} 吨</span>
              </div>
              <div v-if="activeStatus === '2' && row.remark" class="order-reject">驳回原因：{{ row.remark }}</div>
              <div class="order-actions">
                <el-button
                  v-if="row.status === '0'"
                  size="small" type="primary" plain
                  v-hasPermi="['taihu:order:confirm']"
                  @click="handleConfirm(row)"
                >确认</el-button>
                <el-button
                  v-if="row.status === '0'"
                  size="small" type="danger" plain
                  v-hasPermi="['taihu:order:reject']"
                  @click="handleReject(row)"
                >驳回</el-button>
                <el-button
                  v-if="row.status === '1'"
                  size="small" type="success" plain
                  v-hasPermi="['taihu:order:done']"
                  @click="handleDone(row)"
                >完成</el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="TaihuWorkbench">
import { listOrder, confirmOrder, rejectOrder, doneOrder } from '@/api/taihu/orders'
import { getGeojson } from '@/api/taihu/zones'
import { runPipeline, seedDemoData } from '@/api/taihu/pipeline'
import { ElMessage, ElMessageBox } from 'element-plus'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const { proxy } = getCurrentInstance()

/** 今日日期（YYYY-MM-DD） */
const today = proxy.parseTime(new Date(), '{y}-{m}-{d}')

/** ---------- 调度单列表 ---------- */
const orderList = ref([])
const loading = ref(false)
const activeStatus = ref('0')
const pipelineRunning = ref(false)
const seeding = ref(false)

/** 查询今日调度单（当前状态 tab） */
function getList() {
  loading.value = true
  listOrder({ pageNum: 1, pageSize: 50, date: today, status: activeStatus.value }).then(res => {
    orderList.value = res.rows || []
    loading.value = false
  }).catch(() => { loading.value = false })
}

/** 吨数显示：空值显示 "-" */
function formatTons(v) {
  return v == null ? '-' : v
}

/** 确认：待确认 → 已确认 */
function handleConfirm(row) {
  proxy.$modal.confirm(`确认调度单：${row.boatName} → ${row.zoneName}？`).then(() => {
    return confirmOrder(row.orderId)
  }).then(() => {
    ElMessage.success('已确认')
    refreshAll()
  }).catch(() => {})
}

/** 驳回：待确认 → 已驳回，原因必填 */
function handleReject(row) {
  ElMessageBox.prompt(`驳回调度单：${row.boatName} → ${row.zoneName}，请填写原因`, '驳回', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /\S/,
    inputErrorMessage: '驳回原因不能为空'
  }).then(({ value }) => {
    return rejectOrder(row.orderId, value)
  }).then(() => {
    ElMessage.success('已驳回')
    refreshAll()
  }).catch(() => {})
}

/** 完成：已确认 → 已完成，实收吨数选填（缺省由后端取台账汇总） */
function handleDone(row) {
  ElMessageBox.prompt(`登记完成：${row.boatName} → ${row.zoneName}，请填写实收吨数（可留空）`, '完成', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /^(\d+(\.\d{1,2})?)?$/,
    inputErrorMessage: '请输入数字（最多两位小数）或留空'
  }).then(({ value }) => {
    return doneOrder(row.orderId, value === '' ? undefined : Number(value))
  }).then(() => {
    ElMessage.success('已完成')
    refreshAll()
  }).catch(() => {})
}

/** 生成今日调度单：跑当日管线（预报→求解） */
function handleRunPipeline() {
  pipelineRunning.value = true
  runPipeline({ date: today }).then(() => {
    ElMessage.success('今日调度单已生成')
    refreshAll()
  }).finally(() => { pipelineRunning.value = false })
}

/** 重置演示数据：清空并重新生成（含历史台账），二次确认 */
function handleSeed() {
  proxy.$modal.confirm('将重置全部演示数据（分区、船只、台账与今日调度单），是否继续？').then(() => {
    seeding.value = true
    return seedDemoData()
  }).then(() => {
    ElMessage.success('演示数据已重置')
    refreshAll()
  }).finally(() => { seeding.value = false })
}

/** 列表与地图一起刷新 */
function refreshAll() {
  getList()
  loadGeo()
}

/** ---------- Leaflet 地图（零瓦片：纯色底 + 分区面） ---------- */
const mapRef = ref(null)
let map = null
let geoLayer = null

/** 概率五级色阶（浅青 → 深青） */
const legendItems = [
  { label: '< 20%', color: '#e8f5f4' },
  { label: '20% - 40%', color: '#bfe5e2' },
  { label: '40% - 60%', color: '#8ad2cd' },
  { label: '60% - 80%', color: '#4aa9a2' },
  { label: '≥ 80%', color: '#1d726c' }
]

function probColor(p) {
  if (p == null || p === '') return '#cfd8dc'
  if (p < 0.2) return '#e8f5f4'
  if (p < 0.4) return '#bfe5e2'
  if (p < 0.6) return '#8ad2cd'
  if (p < 0.8) return '#4aa9a2'
  return '#1d726c'
}

/** 分区面样式：填充色随 prob_72h */
function styleByProb(props) {
  return {
    fillColor: probColor(props.prob_72h),
    fillOpacity: 0.9,
    color: '#5f7a78',
    weight: 1
  }
}

/** 弹窗内容：分区名 / 概率 / 预计可收量 / Chl-a */
function popupHtml(props) {
  const prob = props.prob_72h == null ? '-' : (props.prob_72h * 100).toFixed(0) + '%'
  const tons = props.tons_q50 == null ? '-' : props.tons_q50
  const chla = props.chla == null ? '-' : props.chla
  return `
    <div class="zone-popup">
      <div class="zone-popup-title">${props.zone_name ?? ''}</div>
      <div>72h 水华概率：${prob}</div>
      <div>预计可收量：${tons} 吨/日</div>
      <div>Chl-a：${chla} μg/L</div>
    </div>`
}

function initMap() {
  // 零瓦片方案：不加载任何 tileLayer，容器背景即水面底色
  map = L.map(mapRef.value, {
    attributionControl: true,
    zoomControl: true
  }).setView([31.2, 120.1], 9)
  map.attributionControl.setPrefix('太湖分区示意')
  loadGeo()
}

/** 加载分区 GeoJSON 并渲染 */
function loadGeo() {
  getGeojson().then(res => {
    const fc = res.data ?? res
    if (!fc || !fc.features) return
    if (geoLayer) map.removeLayer(geoLayer)
    geoLayer = L.geoJSON(fc, {
      style: feature => styleByProb(feature.properties || {}),
      onEachFeature: (feature, layer) => {
        const props = feature.properties || {}
        layer.bindPopup(popupHtml(props))
        // 悬停高亮当前分区
        layer.on('mouseover', () => layer.setStyle({ weight: 3, color: '#2c5f5b' }))
        layer.on('mouseout', () => layer.setStyle(styleByProb(props)))
      }
    }).addTo(map)
    map.fitBounds(geoLayer.getBounds(), { padding: [16, 16] })
  })
}

onMounted(() => {
  getList()
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

  .map-card {
    :deep(.el-card__body) { position: relative; }

    .taihu-map {
      height: 560px;
      background: #dcecec; /* 零瓦片：容器纯色即水面底色 */
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

  .order-card {
    :deep(.el-card__body) { padding-top: 0; }

    .order-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .order-list {
      min-height: 480px;
      max-height: 620px;
      overflow-y: auto;
    }

    .order-item {
      padding: 10px 12px;
      border: 1px solid #ebeef5;
      border-radius: 4px;
      margin-bottom: 8px;

      .order-main {
        display: flex;
        align-items: center;
        gap: 8px;

        .order-boat { font-weight: 600; }
      }

      .order-meta {
        margin-top: 4px;
        font-size: 13px;
        color: #606266;

        .order-actual { color: #67c23a; margin-left: 8px; }
      }

      .order-reject {
        margin-top: 4px;
        font-size: 12px;
        color: #f56c6c;
      }

      .order-actions {
        margin-top: 8px;
        text-align: right;
      }
    }
  }
}

/* Leaflet 弹窗样式（全局，因为 popup 渲染在地图容器内） */
.zone-popup {
  .zone-popup-title { font-weight: 600; margin-bottom: 4px; }
  div { line-height: 20px; font-size: 13px; }
}
</style>
