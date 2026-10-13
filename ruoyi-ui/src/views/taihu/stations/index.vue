<template>
  <div class="app-container">
    <!-- 查询区 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="站名" prop="stationName">
        <el-input v-model="queryParams.stationName" placeholder="请输入站名" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="运行状态" clearable style="width: 160px">
          <el-option label="可用" value="0" />
          <el-option label="维护" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['taihu:station:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['taihu:station:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="stationList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="编号" align="center" prop="stationId" width="80" />
      <el-table-column label="站名" align="center" prop="stationName" />
      <el-table-column label="处理能力(吨/日)" align="center" prop="capacityTDay" width="140" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="small">
            {{ scope.row.status === '0' ? '可用' : '维护' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
      <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['taihu:station:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['taihu:station:remove']">删除</el-button>
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

    <!-- 新增/修改分离站对话框 -->
    <el-dialog :title="title" v-model="open" width="480px" append-to-body>
      <el-form ref="stationRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="站名" prop="stationName">
          <el-input v-model="form.stationName" placeholder="请输入站名" />
        </el-form-item>
        <el-form-item label="处理能力(吨/日)" prop="capacityTDay">
          <el-input-number v-model="form.capacityTDay" :precision="2" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">可用</el-radio>
            <el-radio value="1">维护</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TaihuStation">
import { listStation, getStation, addStation, updateStation, delStation } from '@/api/taihu/stations'

const { proxy } = getCurrentInstance()

const stationList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const title = ref('')

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    stationName: undefined,
    status: undefined
  },
  rules: {
    stationName: [{ required: true, message: '站名不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询分离站列表 */
function getList() {
  loading.value = true
  listStation(queryParams.value).then(res => {
    stationList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

/** 表单重置 */
function reset() {
  form.value = {
    stationId: undefined,
    stationName: undefined,
    capacityTDay: undefined,
    status: '0',
    remark: undefined
  }
  proxy.resetForm('stationRef')
}

/** 取消 */
function cancel() {
  open.value = false
  reset()
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置 */
function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

/** 多选 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.stationId)
  multiple.value = !selection.length
}

/** 新增 */
function handleAdd() {
  reset()
  open.value = true
  title.value = '新增分离站'
}

/** 修改 */
function handleUpdate(row) {
  reset()
  const stationId = row.stationId || ids.value[0]
  getStation(stationId).then(res => {
    form.value = res.data
    open.value = true
    title.value = '修改分离站'
  })
}

/** 提交 */
function submitForm() {
  proxy.$refs['stationRef'].validate(valid => {
    if (!valid) return
    if (form.value.stationId) {
      updateStation(form.value).then(() => {
        proxy.$modal.msgSuccess('修改成功')
        open.value = false
        getList()
      })
    } else {
      addStation(form.value).then(() => {
        proxy.$modal.msgSuccess('新增成功')
        open.value = false
        getList()
      })
    }
  })
}

/** 删除 */
function handleDelete(row) {
  const stationIds = row.stationId ? [row.stationId] : ids.value
  proxy.$modal.confirm('是否确认删除所选分离站？').then(() => {
    return delStation(stationIds.join(','))
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>
