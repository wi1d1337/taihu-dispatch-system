<template>
  <div class="app-container">
    <!-- 查询区：日期 / 分区 / 等级 / 状态 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="日期" prop="date">
        <el-date-picker
          v-model="queryParams.date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          clearable
          style="width: 160px"
        />
      </el-form-item>
      <el-form-item label="分区" prop="zoneId">
        <el-select v-model="queryParams.zoneId" placeholder="全部分区" clearable style="width: 160px">
          <el-option v-for="z in zones" :key="z.zoneId" :label="z.zoneName" :value="z.zoneId" />
        </el-select>
      </el-form-item>
      <el-form-item label="等级" prop="level">
        <el-select v-model="queryParams.level" placeholder="全部等级" clearable style="width: 140px">
          <el-option v-for="(t, lv) in LEVEL_TEXT" :key="lv" :label="t" :value="String(lv)" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="(t, s) in ORDER_STATUS_TEXT" :key="s" :label="t" :value="String(s)" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">人工创建工单</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 工单表格 -->
    <el-table v-loading="loading" :data="orderList">
      <el-table-column label="工单号" align="center" prop="orderNo" width="150" />
      <el-table-column label="派单时间" align="center" prop="dispatchedAt" width="150" />
      <el-table-column label="分区" align="center" prop="zoneName" />
      <el-table-column label="等级" align="center" width="90">
        <template #default="scope">
          <span class="level-badge" :style="{ background: levelColor(scope.row.level) }">{{ levelText(scope.row.level) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="来源" align="center" width="90">
        <template #default="scope">{{ scope.row.source === '2' ? '人工' : '算法' }}</template>
      </el-table-column>
      <el-table-column label="片长" align="center" prop="leaderName" />
      <el-table-column label="船只" align="center" prop="boatName" />
      <el-table-column label="时限" align="center" prop="deadline" width="150" />
      <el-table-column label="实收(吨)" align="center" prop="actualTons" width="90" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="ORDER_STATUS_TAG[scope.row.status] ?? 'info'" size="small">
            {{ ORDER_STATUS_TEXT[scope.row.status] ?? scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
          <el-button
            v-if="canReassign(scope.row)"
            link type="primary" icon="Position"
            @click="handleReassign(scope.row)"
          >改派</el-button>
          <el-button
            v-if="canRevoke(scope.row)"
            link type="danger" icon="CircleClose"
            @click="handleRevoke(scope.row)"
          >撤销</el-button>
          <el-button
            v-if="canCheck(scope.row)"
            link type="success" icon="Checked"
            @click="handleCheck(scope.row)"
          >核查</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="工单详情" size="560px" append-to-body>
      <div v-loading="detailLoading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="ORDER_STATUS_TAG[detail.status] ?? 'info'" size="small">
              {{ ORDER_STATUS_TEXT[detail.status] ?? detail.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="分区">{{ fmt(detail.zoneName) }}</el-descriptions-item>
          <el-descriptions-item label="等级">
            <span class="level-badge" :style="{ background: levelColor(detail.level) }">{{ levelText(detail.level) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.source === '2' ? '人工创建' : '算法自动' }}</el-descriptions-item>
          <el-descriptions-item label="片长">{{ fmt(detail.leaderName) }}</el-descriptions-item>
          <el-descriptions-item label="船只">{{ fmt(detail.boatName) }}</el-descriptions-item>
          <el-descriptions-item label="打捞员">{{ fmt(detail.crewNames ?? detail.crewIds) }}</el-descriptions-item>
          <el-descriptions-item label="处置时限">{{ fmt(detail.deadline) }}</el-descriptions-item>
          <el-descriptions-item label="实收吨数">{{ fmt(detail.actualTons) }}</el-descriptions-item>
          <el-descriptions-item label="模型版本">{{ fmt(detail.modelVersion) }}</el-descriptions-item>
          <el-descriptions-item label="参数版本">{{ fmt(detail.paramVersion) }}</el-descriptions-item>
          <el-descriptions-item label="撤销原因" :span="2">{{ fmt(detail.revokeReason) }}</el-descriptions-item>
          <el-descriptions-item label="核查意见" :span="2">{{ fmt(detail.checkComment) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 关键时点 -->
        <div class="section-title">时间节点</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="派单">{{ fmt(detail.dispatchedAt) }}</el-descriptions-item>
          <el-descriptions-item label="接单">{{ fmt(detail.acceptedAt) }}</el-descriptions-item>
          <el-descriptions-item label="到场">{{ fmt(detail.arrivedAt) }}</el-descriptions-item>
          <el-descriptions-item label="提交">{{ fmt(detail.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="核查">{{ fmt(detail.checkedAt) }}</el-descriptions-item>
          <el-descriptions-item label="到场坐标">
            <span v-if="detail.arriveLng != null">{{ detail.arriveLng }}, {{ detail.arriveLat }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 现场照片 -->
        <template v-if="photoList.length">
          <div class="section-title">现场照片</div>
          <el-image
            v-for="(url, i) in photoList" :key="i"
            :src="url" :preview-src-list="photoList"
            fit="cover" class="photo-thumb" :initial-index="i"
          />
        </template>

        <!-- CP-SAT 推荐派船方案 -->
        <template v-if="suggestBoats.length">
          <div class="section-title">推荐派船方案（CP-SAT，接单时预填可改）</div>
          <el-table :data="suggestBoats" size="small" border>
            <el-table-column label="船" align="center">
              <template #default="s">{{ pick(s.row, 'boat_name', 'boatName') ?? pick(s.row, 'boat_id', 'boatId') }}</template>
            </el-table-column>
            <el-table-column label="作业分区" align="center">
              <template #default="s">{{ pick(s.row, 'zone_name', 'zoneName') ?? pick(s.row, 'zone_id', 'zoneId') ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="建议吨数" align="center">
              <template #default="s">{{ pick(s.row, 'tons', 'tons') ?? '-' }}</template>
            </el-table-column>
          </el-table>
        </template>

        <!-- 流转时间线 -->
        <div class="section-title">流转时间线</div>
        <el-empty v-if="logs.length === 0" description="暂无流转记录" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="(log, i) in logs" :key="i"
            :timestamp="logTime(log)" placement="top"
            :type="i === 0 ? 'primary' : ''"
          >
            <div class="log-line">
              <b>{{ pick(log, 'action', 'action') ?? '-' }}</b>
              <span class="log-op">{{ fmt(pick(log, 'operator', 'operator')) }}</span>
            </div>
            <div v-if="logStatusText(log)" class="log-status">{{ logStatusText(log) }}</div>
            <div v-if="pick(log, 'note', 'note')" class="log-note">{{ pick(log, 'note', 'note') }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>

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

    <!-- 人工创建弹窗 -->
    <el-dialog v-model="addVisible" title="人工创建工单" width="460px" append-to-body>
      <el-form ref="addRef" :model="addForm" :rules="addRules" label-width="80px">
        <el-form-item label="分区" prop="zoneId">
          <el-select v-model="addForm.zoneId" placeholder="选择分区" style="width: 100%">
            <el-option v-for="z in zones" :key="z.zoneId" :label="z.zoneName" :value="z.zoneId" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级" prop="level">
          <el-select v-model="addForm.level" placeholder="选择藻情等级" style="width: 100%">
            <el-option v-for="(t, lv) in LEVEL_TEXT" :key="lv" :label="`${t}（${lv}）`" :value="String(lv)" />
          </el-select>
        </el-form-item>
        <el-form-item label="片长" prop="leaderId">
          <el-select v-model="addForm.leaderId" placeholder="选择片长（首派对象）" style="width: 100%">
            <el-option v-for="w in leaders" :key="w.workerId" :label="`${w.workerName}${w.zoneName ? '（' + w.zoneName + '）' : ''}`" :value="w.workerId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TaihuOrder">
import { listOrder, getOrder, addOrder, revokeOrder, reassignOrder, checkOrder } from '@/api/taihu/orders'
import { listWorker } from '@/api/taihu/workers'
import { listZone } from '@/api/taihu/zones'
import { LEVEL_TEXT, ORDER_STATUS_TEXT, ORDER_STATUS_TAG, pick, levelColor, levelText, fmt } from '@/utils/taihu'
import { ElMessage, ElMessageBox } from 'element-plus'

const { proxy } = getCurrentInstance()

const orderList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const zones = ref([])
const leaders = ref([])

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    date: undefined,
    zoneId: undefined,
    level: undefined,
    status: undefined
  }
})

const { queryParams } = toRefs(data)

/** 状态口径统一为字符串 */
function st(v) {
  return v == null ? '' : String(v)
}
const canRevoke = row => ['0', '1'].includes(st(row.status))
const canReassign = row => st(row.status) === '0'
const canCheck = row => st(row.status) === '3'

/** 查询工单列表 */
function getList() {
  loading.value = true
  listOrder(queryParams.value).then(res => {
    orderList.value = res.rows
    total.value = res.total
    loading.value = false
  }).catch(() => { loading.value = false })
}

function loadZones() {
  listZone({ pageNum: 1, pageSize: 100 }).then(res => {
    zones.value = res.rows ?? res.data ?? []
  })
}

function loadLeaders() {
  listWorker({ pageNum: 1, pageSize: 100, role: '1' }).then(res => {
    leaders.value = res.rows ?? res.data ?? []
  }).catch(() => {})
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** ---------- 详情抽屉 ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref({})
const logs = ref([])
const suggestBoats = ref([])
const photoList = computed(() => {
  const v = detail.value.photoUrls
  if (!v) return []
  const base = import.meta.env.VITE_APP_BASE_API
  return String(v).split(',').filter(Boolean).map(u => (u.startsWith('http') ? u : base + u))
})

function handleDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = { ...row }
  logs.value = []
  suggestBoats.value = []
  getOrder(row.orderId).then(res => {
    // 详情可能整体在 data，也可能平铺；logs 可能内嵌或单独字段
    const d = res.data ?? res
    const order = d.order ?? d
    detail.value = { ...row, ...order }
    logs.value = order.logs ?? d.logs ?? order.orderLogs ?? []
    const sb = order.suggestBoats ?? order.suggest_boats ?? d.suggestBoats ?? d.suggest_boats
    if (Array.isArray(sb)) suggestBoats.value = sb
    else if (typeof sb === 'string' && sb) {
      try { suggestBoats.value = JSON.parse(sb) } catch { suggestBoats.value = [] }
    }
  }).finally(() => { detailLoading.value = false })
}

function logTime(log) {
  return pick(log, 'created_at', 'createdAt') ?? ''
}

function logStatusText(log) {
  const from = pick(log, 'from_status', 'fromStatus')
  const to = pick(log, 'to_status', 'toStatus')
  if (from == null && to == null) return ''
  return `${ORDER_STATUS_TEXT[from] ?? from ?? '-'} → ${ORDER_STATUS_TEXT[to] ?? to ?? '-'}`
}

/** ---------- 撤销 / 改派 / 核查 ---------- */
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
    getList()
  }).catch(() => {})
}

const reassignVisible = ref(false)
const reassignLeaderId = ref(null)
const reassignRow = ref(null)

function handleReassign(row) {
  reassignRow.value = row
  reassignLeaderId.value = null
  reassignVisible.value = true
}

function submitReassign() {
  reassignOrder(reassignRow.value.orderId, reassignLeaderId.value).then(() => {
    ElMessage.success('已改派，工单回到待接单')
    reassignVisible.value = false
    getList()
  })
}

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
    getList()
  })
}

/** ---------- 人工创建 ---------- */
const addVisible = ref(false)
const addRef = ref(null)
const addForm = reactive({ zoneId: undefined, level: undefined, leaderId: undefined })
const addRules = {
  zoneId: [{ required: true, message: '请选择分区', trigger: 'change' }],
  level: [{ required: true, message: '请选择等级', trigger: 'change' }],
  leaderId: [{ required: true, message: '请选择片长', trigger: 'change' }]
}

function handleAdd() {
  Object.assign(addForm, { zoneId: undefined, level: undefined, leaderId: undefined })
  addVisible.value = true
}

function submitAdd() {
  addRef.value.validate(valid => {
    if (!valid) return
    addOrder({ ...addForm }).then(() => {
      ElMessage.success('工单已创建')
      addVisible.value = false
      getList()
    })
  })
}

loadZones()
loadLeaders()
getList()
</script>

<style scoped lang="scss">
.level-badge {
  display: inline-block;
  padding: 0 6px;
  border-radius: 3px;
  color: #fff;
  font-size: 12px;
  line-height: 18px;
}

.section-title {
  margin: 16px 0 8px;
  font-weight: 600;
  font-size: 14px;
}

.photo-thumb {
  width: 72px;
  height: 72px;
  margin-right: 8px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.log-line {
  display: flex;
  gap: 8px;
  align-items: baseline;
  .log-op { color: #909399; font-size: 12px; }
}
.log-status { font-size: 12px; color: #606266; margin-top: 2px; }
.log-note { font-size: 12px; color: #909399; margin-top: 2px; }
</style>
