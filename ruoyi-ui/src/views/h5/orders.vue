<template>
  <div class="h5-page">
    <!-- 顶栏 -->
    <div class="h5-header">
      <div class="hd-left">
        <div class="hd-title">我的工单</div>
        <div class="hd-user">{{ userName }}（{{ role === '1' ? '片长' : '打捞员' }}）</div>
      </div>
      <div class="hd-right">
        <button class="btn-ghost sm" @click="refresh">刷新</button>
        <button class="btn-ghost sm" @click="handleLogout">退出</button>
      </div>
    </div>

    <!-- 状态筛选 -->
    <div class="tab-bar">
      <button
        v-for="t in tabs" :key="t.key"
        class="tab-pill" :class="{ active: activeTab === t.key }"
        @click="activeTab = t.key"
      >{{ t.label }}<span v-if="t.key !== 'all'" class="tab-count">{{ countByStatus(t.key) }}</span></button>
    </div>

    <!-- 工单卡片流 -->
    <div v-if="loading && orderList.length === 0" class="list-tip">加载中…</div>
    <div v-else-if="filteredList.length === 0" class="list-tip">暂无相关工单</div>

    <div v-for="row in filteredList" :key="row.orderId" class="order-card">
      <div class="card-top">
        <span class="order-no">{{ row.orderNo }}</span>
        <span class="status-tag" :class="'st' + row.status">{{ statusText(row.status) }}</span>
      </div>
      <div class="card-mid">
        <span class="zone">{{ row.zoneName ?? '-' }}</span>
        <span class="level-badge" :style="{ background: levelColor(row.level) }">{{ levelText(row.level) }}</span>
      </div>
      <div class="card-line">片长：{{ row.leaderName ?? '-' }}</div>
      <div class="card-line" v-if="row.boatName">船只：{{ row.boatName }}<template v-if="row.crewNames"> · 人员：{{ row.crewNames }}</template></div>
      <div class="card-line warn" v-if="row.deadline && ['0','1','2','3'].includes(String(row.status))">时限：{{ row.deadline }}</div>
      <div class="card-line ok" v-if="row.actualTons != null">实收：{{ row.actualTons }} 吨</div>
      <div class="card-line dim" v-if="String(row.status) === '5' && row.checkComment">退回原因：{{ row.checkComment }}</div>

      <!-- 操作区（按角色+状态） -->
      <div class="card-actions">
        <button
          v-if="role === '1' && String(row.status) === '0'"
          class="btn-primary"
          @click="openAccept(row)"
        >接单（确认派船派人）</button>
        <button
          v-if="role === '2' && String(row.status) === '1'"
          class="btn-primary"
          @click="openArrive(row)"
        >到场打卡</button>
        <button
          v-if="role === '2' && String(row.status) === '2'"
          class="btn-primary"
          @click="openSubmit(row)"
        >提交完成</button>
      </div>
    </div>

    <!-- 接单弹层（片长） -->
    <div v-if="sheet === 'accept'" class="mask" @click.self="closeSheet">
      <div class="sheet">
        <div class="sheet-title">接单 · {{ current.orderNo }}</div>
        <div class="sheet-zone">{{ current.zoneName }} · 等级 {{ levelText(current.level) }}</div>

        <div class="field">
          <label>作业船只</label>
          <select v-model.number="acceptForm.boatId" class="input">
            <option v-for="b in boats" :key="b.boatId" :value="b.boatId">{{ b.boatName }}</option>
          </select>
          <div v-if="suggestText" class="suggest-tip">算法推荐：{{ suggestText }}</div>
        </div>

        <div class="field">
          <label>打捞员（多选）</label>
          <div class="chips">
            <button
              v-for="w in crewList" :key="w.workerId"
              class="chip" :class="{ on: acceptForm.crewIds.includes(w.workerId) }"
              @click="toggleCrew(w.workerId)"
            >{{ w.workerName }}</button>
          </div>
          <div v-if="crewList.length === 0" class="dim-tip">打捞员名单加载中或为空</div>
        </div>

        <button class="btn-primary btn-block" :disabled="!acceptForm.boatId || acceptForm.crewIds.length === 0 || acting" @click="doAccept">
          {{ acting ? '提交中…' : '确认接单' }}
        </button>
        <button class="btn-plain btn-block" @click="closeSheet">取消</button>
      </div>
    </div>

    <!-- 到场打卡弹层（打捞员） -->
    <div v-if="sheet === 'arrive'" class="mask" @click.self="closeSheet">
      <div class="sheet">
        <div class="sheet-title">到场打卡 · {{ current.orderNo }}</div>
        <div class="sheet-zone">{{ current.zoneName }}</div>

        <div class="geo-state" :class="geoState">
          <template v-if="geoState === 'ok'">定位成功：{{ geo.lng }}, {{ geo.lat }}</template>
          <template v-else-if="geoState === 'loading'">正在获取定位…</template>
          <template v-else>定位失败（{{ geoErrMsg }}），可手动填写坐标</template>
        </div>

        <div v-if="geoState === 'fail'" class="field-row">
          <div class="field half">
            <label>经度</label>
            <input v-model="geo.lng" type="number" class="input" placeholder="如 120.15" />
          </div>
          <div class="field half">
            <label>纬度</label>
            <input v-model="geo.lat" type="number" class="input" placeholder="如 31.20" />
          </div>
        </div>

        <button class="btn-primary btn-block" :disabled="acting || !geoValid" @click="doArrive">
          {{ acting ? '提交中…' : '确认到场' }}
        </button>
        <button v-if="geoState === 'fail'" class="btn-plain btn-block" @click="retryGeo">重新定位</button>
        <button class="btn-plain btn-block" @click="closeSheet">取消</button>
      </div>
    </div>

    <!-- 提交完成弹层（打捞员） -->
    <div v-if="sheet === 'submit'" class="mask" @click.self="closeSheet">
      <div class="sheet">
        <div class="sheet-title">提交完成 · {{ current.orderNo }}</div>
        <div class="sheet-zone">{{ current.zoneName }}</div>

        <div class="field">
          <label>实收藻水吨数（必填）</label>
          <input v-model="submitForm.actualTons" type="number" class="input" placeholder="如 3.5" />
        </div>

        <div class="field">
          <label>现场照片（可选，可多张）</label>
          <input type="file" accept="image/*" multiple class="file-input" @change="onFilesPicked" />
          <div class="dim-tip">{{ uploadTip }}</div>
        </div>

        <button class="btn-primary btn-block" :disabled="!tonValid || acting" @click="doSubmit">
          {{ acting ? '提交中…' : '提交完成' }}
        </button>
        <button class="btn-plain btn-block" @click="closeSheet">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup name="H5Orders">
import { h5Me, h5MyOrders, h5Accept, h5Arrive, h5Submit, uploadPhoto } from '@/api/taihu/h5'
import { listBoat } from '@/api/taihu/boats'
import { listWorker } from '@/api/taihu/workers'
import { getToken, removeToken } from '@/utils/auth'
import { levelColor, levelText } from '@/utils/taihu'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()

const userName = ref('')
const role = ref('1') // 1片长 2打捞员
const orderList = ref([])
const loading = ref(false)
const activeTab = ref('all')
const acting = ref(false)

const tabs = [
  { key: 'all', label: '全部' },
  { key: '0', label: '待接单' },
  { key: 'running', label: '进行中' },
  { key: '3', label: '待核查' },
  { key: '4', label: '已办结' }
]

const STATUS_TEXT = {
  0: '待接单', 1: '已接单', 2: '进行中',
  3: '待核查', 4: '已办结', 5: '已退回', 6: '已撤销'
}
const statusText = s => STATUS_TEXT[s] ?? String(s ?? '-')

function countByStatus(key) {
  if (key === 'all') return orderList.value.length
  return filteredBy(key).length
}

function filteredBy(key) {
  if (key === 'all') return orderList.value
  return orderList.value.filter(r => {
    const s = String(r.status)
    if (key === 'running') return s === '1' || s === '2'
    return s === key
  })
}

const filteredList = computed(() => filteredBy(activeTab.value))

/** ---------- 角色与列表 ---------- */
function parseRole(me) {
  const direct = me.role ?? (me.user && me.user.role)
  if (direct != null) return String(direct)
  const roles = me.roles ?? (me.user && me.user.roles) ?? []
  const joined = Array.isArray(roles) ? roles.join(',') : String(roles)
  if (/worker|salvage|捞|船员/i.test(joined)) return '2'
  return '1' // 缺省按片长渲染
}

function loadMe() {
  return h5Me().then(res => {
    const me = res.data ?? res
    userName.value = me.nickName ?? me.userName ?? me.username ?? ''
    role.value = parseRole(me)
  })
}

function loadOrders() {
  loading.value = true
  return h5MyOrders({ pageNum: 1, pageSize: 100 }).then(res => {
    orderList.value = res.rows ?? res.data ?? (Array.isArray(res) ? res : [])
  }).finally(() => { loading.value = false })
}

function refresh() {
  loadOrders()
}

function handleLogout() {
  removeToken()
  router.replace('/h5/login')
}

/** ---------- 底部弹层通用 ---------- */
const sheet = ref('') // '' | accept | arrive | submit
const current = ref({})

function closeSheet() {
  sheet.value = ''
  current.value = {}
}

/** ---------- 接单（片长） ---------- */
const boats = ref([])
const crewList = ref([])
const acceptForm = reactive({ boatId: null, crewIds: [] })
const suggestText = ref('')

function parseSuggest(row) {
  let sb = row.suggestBoats ?? row.suggest_boats
  if (typeof sb === 'string' && sb) {
    try { sb = JSON.parse(sb) } catch { sb = [] }
  }
  return Array.isArray(sb) ? sb : []
}

function openAccept(row) {
  current.value = row
  const sb = parseSuggest(row)
  const first = sb[0] ?? {}
  acceptForm.boatId = first.boatId ?? first.boat_id ?? null
  acceptForm.crewIds = []
  const parts = []
  if (first.boatId ?? first.boat_id) {
    const zb = sb.map(b => `${b.zoneName ?? b.zone_name ?? ''} 建议 ${b.tons ?? '-'} 吨`).filter(Boolean)
    parts.push('已预填推荐船只')
    if (zb.length) parts.push(zb.join('；'))
  }
  suggestText.value = parts.join('，')
  sheet.value = 'accept'
  if (boats.value.length === 0) {
    listBoat({ pageNum: 1, pageSize: 100 }).then(res => { boats.value = res.rows ?? res.data ?? [] })
  }
  if (crewList.value.length === 0) {
    listWorker({ pageNum: 1, pageSize: 100, role: '2' }).then(res => { crewList.value = res.rows ?? res.data ?? [] })
  }
}

function toggleCrew(id) {
  const i = acceptForm.crewIds.indexOf(id)
  if (i >= 0) acceptForm.crewIds.splice(i, 1)
  else acceptForm.crewIds.push(id)
}

function doAccept() {
  acting.value = true
  h5Accept(current.value.orderId, { boatId: acceptForm.boatId, crewIds: acceptForm.crewIds }).then(() => {
    ElMessage.success('已接单')
    closeSheet()
    loadOrders()
  }).finally(() => { acting.value = false })
}

/** ---------- 到场打卡（打捞员） ---------- */
const geo = reactive({ lng: '', lat: '' })
const geoState = ref('loading') // loading | ok | fail
const geoErrMsg = ref('')

const geoValid = computed(() => geoState.value === 'ok' || (Number(geo.lng) !== 0 && geo.lng !== '' && geo.lat !== ''))

function getLocation() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('浏览器不支持定位'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      pos => resolve({
        lng: +Number(pos.coords.longitude).toFixed(6),
        lat: +Number(pos.coords.latitude).toFixed(6)
      }),
      err => reject(err),
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 30000 }
    )
  })
}

function tryGeo() {
  geoState.value = 'loading'
  geoErrMsg.value = ''
  getLocation().then(({ lng, lat }) => {
    geo.lng = lng
    geo.lat = lat
    geoState.value = 'ok'
  }).catch(err => {
    geoErrMsg.value = (err && err.message) ? String(err.message).slice(0, 30) : '无权限或超时'
    geoState.value = 'fail'
  })
}

function retryGeo() {
  tryGeo()
}

function openArrive(row) {
  current.value = row
  geo.lng = ''
  geo.lat = ''
  sheet.value = 'arrive'
  tryGeo()
}

function doArrive() {
  acting.value = true
  h5Arrive(current.value.orderId, { lng: Number(geo.lng), lat: Number(geo.lat) }).then(() => {
    ElMessage.success('到场打卡成功')
    closeSheet()
    loadOrders()
  }).finally(() => { acting.value = false })
}

/** ---------- 提交完成（打捞员） ---------- */
const submitForm = reactive({ actualTons: '' })
const photoUrls = ref([])
const uploading = ref(false)

const tonValid = computed(() => submitForm.actualTons !== '' && !Number.isNaN(Number(submitForm.actualTons)) && Number(submitForm.actualTons) >= 0)
const uploadTip = computed(() => {
  if (uploading.value) return '照片上传中…'
  return photoUrls.value.length > 0 ? `已上传 ${photoUrls.value.length} 张` : '未选择照片可直接提交'
})

function openSubmit(row) {
  current.value = row
  submitForm.actualTons = ''
  photoUrls.value = []
  uploading.value = false
  sheet.value = 'submit'
}

function onFilesPicked(e) {
  const files = Array.from(e.target.files || [])
  if (files.length === 0) return
  uploading.value = true
  Promise.all(files.map(f => uploadPhoto(f).then(res => res.fileName ?? res.newFileName ?? '')))
    .then(names => {
      photoUrls.value = [...photoUrls.value, ...names.filter(Boolean)]
      ElMessage.success('照片已上传')
    })
    .catch(() => {
      ElMessage.error('照片上传失败，请重试或直接提交')
    })
    .finally(() => {
      uploading.value = false
      e.target.value = ''
    })
}

function doSubmit() {
  acting.value = true
  h5Submit(current.value.orderId, {
    actualTons: Number(submitForm.actualTons),
    photoUrls: photoUrls.value.length ? photoUrls.value.join(',') : undefined
  }).then(() => {
    ElMessage.success('已提交，等待核查')
    closeSheet()
    loadOrders()
  }).finally(() => { acting.value = false })
}

/** ---------- 生命周期：未登录跳登录 + 30s 自动刷新 ---------- */
let timer = null

onMounted(() => {
  if (!getToken()) {
    router.replace('/h5/login')
    return
  }
  loadMe().catch(() => {}).finally(() => { loadOrders() })
  timer = setInterval(refresh, 30000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  timer = null
})
</script>

<style scoped lang="scss">
.h5-page {
  min-height: 100vh;
  background: #eef3f6;
  padding-bottom: 32px;
  -webkit-tap-highlight-color: transparent;
  font-size: 15px;
}

/* 顶栏 */
.h5-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  background: #1d726c;
  color: #fff;

  .hd-title { font-size: 18px; font-weight: 600; }
  .hd-user { font-size: 12px; opacity: 0.85; margin-top: 2px; }
  .hd-right { display: flex; gap: 8px; }
}

/* 状态筛选 */
.tab-bar {
  display: flex;
  gap: 8px;
  padding: 12px 16px 4px;
  overflow-x: auto;

  .tab-pill {
    flex: none;
    height: 34px;
    padding: 0 14px;
    border-radius: 17px;
    border: 1px solid #cfd8dc;
    background: #fff;
    color: #606266;
    font-size: 14px;

    &.active {
      background: #1d726c;
      border-color: #1d726c;
      color: #fff;
    }

    .tab-count { margin-left: 4px; font-size: 12px; opacity: 0.8; }
  }
}

/* 卡片流 */
.order-card {
  margin: 10px 16px 0;
  background: #fff;
  border-radius: 10px;
  padding: 12px 14px;
  box-shadow: 0 2px 8px rgba(31, 56, 88, 0.06);

  .card-top {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .order-no { font-weight: 600; font-size: 14px; color: #303133; }
  }

  .status-tag {
    font-size: 12px;
    padding: 2px 8px;
    border-radius: 4px;
    background: #f0f2f5;
    color: #606266;

    &.st0 { background: #fdf6ec; color: #e6a23c; }
    &.st1, &.st2 { background: #ecf5ff; color: #409eff; }
    &.st3 { background: #fdf6ec; color: #e6a23c; }
    &.st4 { background: #f0f9eb; color: #67c23a; }
    &.st5 { background: #fef0f0; color: #f56c6c; }
    &.st6 { background: #f4f4f5; color: #909399; }
  }

  .card-mid {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 8px 0 4px;

    .zone { font-size: 16px; font-weight: 600; color: #17323f; }
  }

  .level-badge {
    display: inline-block;
    padding: 1px 8px;
    border-radius: 4px;
    color: #fff;
    font-size: 12px;
    line-height: 18px;
  }

  .card-line {
    font-size: 13px;
    color: #606266;
    line-height: 22px;

    &.warn { color: #e6a23c; }
    &.ok { color: #67c23a; }
    &.dim { color: #909399; font-size: 12px; }
  }

  .card-actions { margin-top: 10px; }
}

.list-tip {
  text-align: center;
  color: #909399;
  font-size: 14px;
  padding: 48px 0;
}

/* 按钮 */
.btn-primary {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 8px;
  background: #1d726c;
  color: #fff;
  font-size: 15px;

  &:active { opacity: 0.85; }
  &:disabled { opacity: 0.5; }
}

.btn-ghost {
  height: 30px;
  padding: 0 12px;
  border-radius: 15px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  background: transparent;
  color: #fff;
  font-size: 13px;

  &.sm { font-size: 12px; }
}

.btn-plain {
  width: 100%;
  height: 40px;
  margin-top: 8px;
  border: none;
  background: transparent;
  color: #909399;
  font-size: 14px;
}

.btn-block { margin-top: 12px; }

/* 底部弹层 */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  background: #fff;
  border-radius: 14px 14px 0 0;
  padding: 18px 16px calc(18px + env(safe-area-inset-bottom));
  max-height: 82vh;
  overflow-y: auto;

  .sheet-title { font-size: 17px; font-weight: 600; color: #17323f; }
  .sheet-zone { margin-top: 4px; font-size: 13px; color: #909399; }
}

.field {
  margin-top: 14px;

  label {
    display: block;
    font-size: 13px;
    color: #606266;
    margin-bottom: 6px;
  }

  .input {
    width: 100%;
    height: 44px;
    padding: 0 12px;
    font-size: 15px;
    border: 1px solid #dcdfe6;
    border-radius: 8px;
    outline: none;
    box-sizing: border-box;
    background: #fff;

    &:focus { border-color: #1d726c; }
  }

  .file-input { width: 100%; font-size: 13px; }
}

.field-row {
  display: flex;
  gap: 10px;

  .half { flex: 1; }
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  .chip {
    padding: 6px 14px;
    border-radius: 16px;
    border: 1px solid #dcdfe6;
    background: #fff;
    color: #606266;
    font-size: 14px;

    &.on {
      background: #1d726c;
      border-color: #1d726c;
      color: #fff;
    }
  }
}

.suggest-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #409eff;
}

.dim-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.geo-state {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
  background: #f5f7fa;
  color: #606266;

  &.ok { background: #f0f9eb; color: #67c23a; }
  &.fail { background: #fef0f0; color: #f56c6c; }
}
</style>
