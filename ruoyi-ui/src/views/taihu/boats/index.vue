<template>
  <div class="app-container">
    <!-- 查询区 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="船名" prop="boatName">
        <el-input v-model="queryParams.boatName" placeholder="请输入船名" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="船只状态" clearable style="width: 160px">
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['taihu:boat:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['taihu:boat:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="boatList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="编号" align="center" prop="boatId" width="80" />
      <el-table-column label="船名" align="center" prop="boatName" />
      <el-table-column label="类型" align="center" prop="boatType" />
      <el-table-column label="容量(吨/日)" align="center" prop="capacityT" width="110" />
      <el-table-column label="航速(km/h)" align="center" prop="speedKmh" width="110" />
      <el-table-column label="油耗(L/km)" align="center" prop="fuelLkm" width="110" />
      <el-table-column label="母港" align="center" prop="homePort" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="small">
            {{ scope.row.status === '0' ? '可用' : '维护' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['taihu:boat:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['taihu:boat:remove']">删除</el-button>
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

    <!-- 新增/修改船只对话框 -->
    <el-dialog :title="title" v-model="open" width="520px" append-to-body>
      <el-form ref="boatRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="船名" prop="boatName">
          <el-input v-model="form.boatName" placeholder="请输入船名" />
        </el-form-item>
        <el-form-item label="类型" prop="boatType">
          <el-select v-model="form.boatType" placeholder="请选择类型" style="width: 100%">
            <el-option label="打捞" value="打捞" />
            <el-option label="机动" value="机动" />
            <el-option label="小型" value="小型" />
          </el-select>
        </el-form-item>
        <el-form-item label="容量(吨/日)" prop="capacityT">
          <el-input-number v-model="form.capacityT" :precision="2" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="航速(km/h)" prop="speedKmh">
          <el-input-number v-model="form.speedKmh" :precision="1" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="油耗(L/km)" prop="fuelLkm">
          <el-input-number v-model="form.fuelLkm" :precision="2" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="母港" prop="homePort">
          <el-input v-model="form.homePort" placeholder="请输入母港" />
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

<script setup name="TaihuBoat">
import { listBoat, getBoat, addBoat, updateBoat, delBoat } from '@/api/taihu/boats'

const { proxy } = getCurrentInstance()

const boatList = ref([])
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
    boatName: undefined,
    status: undefined
  },
  rules: {
    boatName: [{ required: true, message: '船名不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询船只列表 */
function getList() {
  loading.value = true
  listBoat(queryParams.value).then(res => {
    boatList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

/** 表单重置 */
function reset() {
  form.value = {
    boatId: undefined,
    boatName: undefined,
    boatType: '打捞',
    capacityT: undefined,
    speedKmh: undefined,
    fuelLkm: undefined,
    homePort: undefined,
    status: '0',
    remark: undefined
  }
  proxy.resetForm('boatRef')
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
  ids.value = selection.map(item => item.boatId)
  multiple.value = !selection.length
}

/** 新增 */
function handleAdd() {
  reset()
  open.value = true
  title.value = '新增船只'
}

/** 修改 */
function handleUpdate(row) {
  reset()
  const boatId = row.boatId || ids.value[0]
  getBoat(boatId).then(res => {
    form.value = res.data
    open.value = true
    title.value = '修改船只'
  })
}

/** 提交 */
function submitForm() {
  proxy.$refs['boatRef'].validate(valid => {
    if (!valid) return
    if (form.value.boatId) {
      updateBoat(form.value).then(() => {
        proxy.$modal.msgSuccess('修改成功')
        open.value = false
        getList()
      })
    } else {
      addBoat(form.value).then(() => {
        proxy.$modal.msgSuccess('新增成功')
        open.value = false
        getList()
      })
    }
  })
}

/** 删除 */
function handleDelete(row) {
  const boatIds = row.boatId ? [row.boatId] : ids.value
  proxy.$modal.confirm('是否确认删除所选船只？').then(() => {
    return delBoat(boatIds.join(','))
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>
