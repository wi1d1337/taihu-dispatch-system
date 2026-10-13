<template>
  <div class="app-container">
    <!-- 查询区：日期 / 状态 / 时段 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="日期" prop="date">
        <el-date-picker
          v-model="queryParams.date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          clearable
          style="width: 180px"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="调度单状态" clearable style="width: 160px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="时段" prop="halfDay">
        <el-select v-model="queryParams.halfDay" placeholder="作业时段" clearable style="width: 160px">
          <el-option label="上午" value="0" />
          <el-option label="下午" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 调度单表格 -->
    <el-table v-loading="loading" :data="orderList">
      <el-table-column label="编号" align="center" prop="orderId" width="80" />
      <el-table-column label="日期" align="center" prop="orderDate" width="110" />
      <el-table-column label="时段" align="center" width="80">
        <template #default="scope">{{ scope.row.halfDay === '0' ? '上午' : '下午' }}</template>
      </el-table-column>
      <el-table-column label="船只" align="center" prop="boatName" />
      <el-table-column label="分区" align="center" prop="zoneName" />
      <el-table-column label="分离站" align="center" prop="stationName" />
      <el-table-column label="计划(吨)" align="center" prop="planTons" width="100" />
      <el-table-column label="实收(吨)" align="center" prop="actualTons" width="100" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="statusTag[scope.row.status]" size="small">{{ statusText[scope.row.status] || scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
      <el-table-column label="操作" width="200" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
            v-if="scope.row.status === '0'"
            link type="primary" icon="Check"
            v-hasPermi="['taihu:order:confirm']"
            @click="handleConfirm(scope.row)"
          >确认</el-button>
          <el-button
            v-if="scope.row.status === '0'"
            link type="danger" icon="Close"
            v-hasPermi="['taihu:order:reject']"
            @click="handleReject(scope.row)"
          >驳回</el-button>
          <el-button
            v-if="scope.row.status === '1'"
            link type="success" icon="Finished"
            v-hasPermi="['taihu:order:done']"
            @click="handleDone(scope.row)"
          >完成</el-button>
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
  </div>
</template>

<script setup name="TaihuOrder">
import { listOrder, confirmOrder, rejectOrder, doneOrder } from '@/api/taihu/orders'
import { ElMessage, ElMessageBox } from 'element-plus'

const { proxy } = getCurrentInstance()

/** 状态字典（调度单状态机：0待确认 →1已确认 →3已完成；0→2已驳回） */
const statusOptions = [
  { label: '待确认', value: '0' },
  { label: '已确认', value: '1' },
  { label: '已完成', value: '3' },
  { label: '已驳回', value: '2' }
]
const statusText = { '0': '待确认', '1': '已确认', '2': '已驳回', '3': '已完成' }
const statusTag = { '0': 'warning', '1': 'primary', '2': 'danger', '3': 'success' }

const orderList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    date: proxy.parseTime(new Date(), '{y}-{m}-{d}'),
    status: undefined,
    halfDay: undefined
  }
})

const { queryParams } = toRefs(data)

/** 查询调度单列表 */
function getList() {
  loading.value = true
  listOrder(queryParams.value).then(res => {
    orderList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置（恢复为今日） */
function resetQuery() {
  proxy.resetForm("queryRef")
  queryParams.value.date = proxy.parseTime(new Date(), '{y}-{m}-{d}')
  handleQuery()
}

/** 确认：待确认 → 已确认 */
function handleConfirm(row) {
  proxy.$modal.confirm(`确认调度单：${row.boatName} → ${row.zoneName}（${row.orderDate}）？`).then(() => {
    return confirmOrder(row.orderId)
  }).then(() => {
    ElMessage.success('已确认')
    getList()
  }).catch(() => {})
}

/** 驳回：原因必填 */
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
    getList()
  }).catch(() => {})
}

/** 完成：实收吨数选填 */
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
    getList()
  }).catch(() => {})
}

getList()
</script>
