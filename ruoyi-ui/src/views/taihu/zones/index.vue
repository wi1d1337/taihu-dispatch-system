<template>
  <div class="app-container">
    <!-- 查询区 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="分区名" prop="zoneName">
        <el-input v-model="queryParams.zoneName" placeholder="请输入分区名" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['taihu:zone:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['taihu:zone:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="zoneList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="编号" align="center" prop="zoneId" width="80" />
      <el-table-column label="分区名" align="center" prop="zoneName" />
      <el-table-column label="代码" align="center" prop="zoneCode" width="90" />
      <el-table-column label="面积(km²)" align="center" prop="areaKm2" width="100" />
      <el-table-column label="72h概率" align="center" prop="prob72h" width="100">
        <template #default="scope">{{ formatProb(scope.row.prob72h) }}</template>
      </el-table-column>
      <el-table-column label="可收量q25(吨)" align="center" prop="tonsQ25" width="120" />
      <el-table-column label="可收量q50(吨)" align="center" prop="tonsQ50" width="120" />
      <el-table-column label="Chl-a(μg/L)" align="center" prop="chla" width="110" />
      <el-table-column label="边界" align="center" width="110">
        <template #default="scope">
          <span>{{ boundaryTip(scope.row.boundary) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['taihu:zone:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['taihu:zone:remove']">删除</el-button>
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

    <!-- 新增/修改分区对话框（boundary 由演示数据生成，此处不提供编辑） -->
    <el-dialog :title="title" v-model="open" width="520px" append-to-body>
      <el-form ref="zoneRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="分区名" prop="zoneName">
          <el-input v-model="form.zoneName" placeholder="请输入分区名" />
        </el-form-item>
        <el-form-item label="代码" prop="zoneCode">
          <el-input v-model="form.zoneCode" placeholder="如 MLW/ZSH/GH/HX/XBAY/NBAY" />
        </el-form-item>
        <el-form-item label="面积(km²)" prop="areaKm2">
          <el-input-number v-model="form.areaKm2" :precision="2" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="72h概率(0-1)" prop="prob72h">
          <el-input-number v-model="form.prob72h" :precision="2" :min="0" :max="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="可收量q25(吨)" prop="tonsQ25">
          <el-input-number v-model="form.tonsQ25" :precision="2" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="可收量q50(吨)" prop="tonsQ50">
          <el-input-number v-model="form.tonsQ50" :precision="2" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Chl-a(μg/L)" prop="chla">
          <el-input-number v-model="form.chla" :precision="2" :min="0" controls-position="right" style="width: 100%" />
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

<script setup name="TaihuZone">
import { listZone, getZone, addZone, updateZone, delZone } from '@/api/taihu/zones'

const { proxy } = getCurrentInstance()

const zoneList = ref([])
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
    zoneName: undefined
  },
  rules: {
    zoneName: [{ required: true, message: '分区名不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 概率显示：0.63 → 63% */
function formatProb(v) {
  return v == null ? '-' : (v * 100).toFixed(0) + '%'
}

/** boundary 只读提示：显示坐标点数，不渲染地图 */
function boundaryTip(boundary) {
  if (!boundary) return '未设置'
  try {
    const arr = typeof boundary === 'string' ? JSON.parse(boundary) : boundary
    const count = Array.isArray(arr) ? arr.length : 0
    return `${count} 个坐标点`
  } catch (e) {
    return '格式异常'
  }
}

/** 查询分区列表 */
function getList() {
  loading.value = true
  listZone(queryParams.value).then(res => {
    zoneList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

/** 表单重置 */
function reset() {
  form.value = {
    zoneId: undefined,
    zoneName: undefined,
    zoneCode: undefined,
    areaKm2: undefined,
    prob72h: undefined,
    tonsQ25: undefined,
    tonsQ50: undefined,
    chla: undefined,
    remark: undefined
  }
  proxy.resetForm('zoneRef')
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
  ids.value = selection.map(item => item.zoneId)
  multiple.value = !selection.length
}

/** 新增 */
function handleAdd() {
  reset()
  open.value = true
  title.value = '新增分区'
}

/** 修改 */
function handleUpdate(row) {
  reset()
  const zoneId = row.zoneId || ids.value[0]
  getZone(zoneId).then(res => {
    form.value = res.data
    open.value = true
    title.value = '修改分区'
  })
}

/** 提交 */
function submitForm() {
  proxy.$refs['zoneRef'].validate(valid => {
    if (!valid) return
    if (form.value.zoneId) {
      updateZone(form.value).then(() => {
        proxy.$modal.msgSuccess('修改成功')
        open.value = false
        getList()
      })
    } else {
      addZone(form.value).then(() => {
        proxy.$modal.msgSuccess('新增成功')
        open.value = false
        getList()
      })
    }
  })
}

/** 删除 */
function handleDelete(row) {
  const zoneIds = row.zoneId ? [row.zoneId] : ids.value
  proxy.$modal.confirm('是否确认删除所选分区？').then(() => {
    return delZone(zoneIds.join(','))
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>
