<template>
  <div class="app-container">
    <!-- 工具条：日期 + 导出 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="14">
        <el-date-picker
          v-model="date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日报日期"
          :clearable="false"
          style="width: 180px"
          @change="load"
        />
        <el-button class="ml8" icon="Refresh" @click="load">刷新</el-button>
      </el-col>
      <el-col :span="10" style="text-align: right">
        <el-button type="primary" plain icon="Download" :disabled="!hasData" @click="handleExport">导出</el-button>
      </el-col>
    </el-row>

    <!-- 汇总卡 -->
    <el-row :gutter="12" class="mb12">
      <el-col :span="6" v-for="c in summaryCards" :key="c.label">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">{{ c.label }}</div>
            <div class="kpi-value" :style="{ color: c.color }">{{ c.value }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 工单明细 -->
    <el-card shadow="never" class="mb12">
      <template #header><span>当日工单明细</span></template>
      <el-table v-loading="loading" :data="orders" size="small">
        <el-table-column label="工单号" align="center" prop="orderNo" width="150" />
        <el-table-column label="分区" align="center" prop="zoneName" />
        <el-table-column label="等级" align="center" width="80">
          <template #default="s">
            <span class="level-badge" :style="{ background: levelColor(s.row.level) }">{{ levelText(s.row.level) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="片长" align="center" prop="leaderName" />
        <el-table-column label="船只" align="center" prop="boatName" />
        <el-table-column label="实收(吨)" align="center" prop="actualTons" width="90" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="s">
            <el-tag :type="ORDER_STATUS_TAG[s.row.status] ?? 'info'" size="small">
              {{ ORDER_STATUS_TEXT[s.row.status] ?? s.row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 未办结清单 -->
    <el-card shadow="never">
      <template #header><span>未办结清单（对齐水利局逐日上报口径）</span></template>
      <el-table :data="uncompleted" size="small">
        <el-table-column label="工单号" align="center" prop="orderNo" width="150" />
        <el-table-column label="分区" align="center" prop="zoneName" />
        <el-table-column label="片长" align="center" prop="leaderName" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="s">
            <el-tag :type="ORDER_STATUS_TAG[s.row.status] ?? 'info'" size="small">
              {{ ORDER_STATUS_TEXT[s.row.status] ?? s.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处置时限" align="center" prop="deadline" width="150" />
        <el-table-column label="备注" align="center">
          <template #default="s">{{ s.row.revokeReason ?? s.row.checkComment ?? '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup name="TaihuDaily">
import { getDailyReport } from '@/api/taihu/daily'
import { ORDER_STATUS_TEXT, ORDER_STATUS_TAG, pick, levelColor, levelText } from '@/utils/taihu'
import { saveAs } from 'file-saver'

const { proxy } = getCurrentInstance()

const date = ref(proxy.parseTime(new Date(), '{y}-{m}-{d}'))
const loading = ref(false)
const hasData = ref(false)
const summary = ref({})
const orders = ref([])
const uncompleted = ref([])

const summaryCards = computed(() => [
  { label: '工单总数', value: summary.value.total ?? '-', color: '#303133' },
  { label: '已办结', value: summary.value.completed ?? '-', color: '#67c23a' },
  { label: '实收吨数', value: summary.value.tons ?? '-', color: '#409eff' },
  { label: '未办结', value: summary.value.uncompleted ?? '-', color: '#e6a23c' }
])

function load() {
  loading.value = true
  getDailyReport({ date: date.value }).then(res => {
    const data = res.data ?? res
    const s = data.summary ?? {}
    const ordersRaw = data.orders ?? data.order_list ?? data.list ?? []
    const unRaw = data.uncompleted ?? data.uncompleted_list ?? data.pending ?? []
    summary.value = {
      total: pick(s, 'total', 'total') ?? (Array.isArray(ordersRaw) ? ordersRaw.length : undefined),
      completed: pick(s, 'completed', 'completed') ?? pick(s, 'finished', 'finished'),
      tons: pick(s, 'tons', 'tons') ?? pick(s, 'total_tons', 'totalTons'),
      uncompleted: pick(s, 'uncompleted', 'uncompleted') ?? (Array.isArray(unRaw) ? unRaw.length : undefined)
    }
    orders.value = ordersRaw
    uncompleted.value = unRaw
    hasData.value = ordersRaw.length > 0 || unRaw.length > 0
  }).finally(() => { loading.value = false })
}

/** 前端生成 CSV（UTF-8 BOM，Excel 可直接打开）；正式 xlsx 导出待后端补 download 接口 */
function handleExport() {
  const head = ['工单号', '分区', '等级', '片长', '船只', '实收吨数', '状态', '处置时限']
  const statusText = s => ORDER_STATUS_TEXT[s] ?? s ?? ''
  const line = r => [
    r.orderNo ?? '', r.zoneName ?? '', levelText(r.level), r.leaderName ?? '',
    r.boatName ?? '', r.actualTons ?? '', statusText(r.status), r.deadline ?? ''
  ].map(v => `"${String(v).replace(/"/g, '""')}"`).join(',')
  const unHead = ['未办结工单号', '分区', '片长', '状态', '处置时限', '备注']
  const rows = [
    `太湖蓝藻打捞日报 ${date.value}`,
    `工单总数,${summary.value.total ?? ''},已办结,${summary.value.completed ?? ''},实收吨数,${summary.value.tons ?? ''},未办结,${summary.value.uncompleted ?? ''}`,
    '',
    head.join(','),
    ...orders.value.map(line)
  ]
  if (uncompleted.value.length > 0) {
    rows.push('', unHead.join(','))
    uncompleted.value.forEach(r => {
      rows.push([r.orderNo ?? '', r.zoneName ?? '', r.leaderName ?? '', statusText(r.status), r.deadline ?? '', r.revokeReason ?? r.checkComment ?? '']
        .map(v => `"${String(v).replace(/"/g, '""')}"`).join(','))
    })
  }
  const blob = new Blob(['\ufeff' + rows.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  saveAs(blob, `太湖打捞日报_${date.value}.csv`)
}

load()
</script>

<style scoped lang="scss">
.mb12 { margin-bottom: 12px; }
.mb8 { margin-bottom: 8px; }
.ml8 { margin-left: 8px; }

.kpi-card {
  text-align: center;
  .kpi-label { font-size: 13px; color: #606266; }
  .kpi-value { font-size: 26px; font-weight: 600; margin-top: 4px; }
}

.level-badge {
  display: inline-block;
  padding: 0 6px;
  border-radius: 3px;
  color: #fff;
  font-size: 12px;
  line-height: 18px;
}
</style>
