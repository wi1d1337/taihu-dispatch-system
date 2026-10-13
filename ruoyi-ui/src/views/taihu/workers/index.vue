<template>
  <div class="app-container">
    <!-- 查询区 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="姓名" prop="workerName">
        <el-input v-model="queryParams.workerName" placeholder="按姓名模糊查询" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="角色" prop="role">
        <el-select v-model="queryParams.role" placeholder="全部角色" clearable style="width: 140px">
          <el-option label="片长" value="1" />
          <el-option label="打捞员" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 120px">
          <el-option label="正常" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['taihu:worker:add']">新增</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="workerList">
      <el-table-column label="编号" align="center" prop="workerId" width="70" />
      <el-table-column label="姓名" align="center" prop="workerName" />
      <el-table-column label="角色" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.role === '1' ? 'primary' : 'success'" size="small">{{ roleText(scope.row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="电话" align="center" prop="phone" width="130" />
      <el-table-column label="负责分区" align="center">
        <template #default="scope">{{ scope.row.role === '1' ? (zoneName(scope.row.zoneId) || scope.row.zoneName || '-') : '-' }}</template>
      </el-table-column>
      <el-table-column label="班组" align="center" prop="teamName" />
      <el-table-column label="登录名" align="center" prop="username" />
      <el-table-column label="状态" align="center" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.status === '1' ? 'danger' : 'success'" size="small">
            {{ scope.row.status === '1' ? '停用' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['taihu:worker:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['taihu:worker:remove']">删除</el-button>
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

    <!-- 新增/修改弹窗 -->
    <el-dialog :title="title" v-model="visible" width="500px" append-to-body>
      <el-form ref="workerRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="姓名" prop="workerName">
          <el-input v-model="form.workerName" placeholder="请输入姓名" maxlength="50" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="form.role">
            <el-radio value="1">片长</el-radio>
            <el-radio value="2">打捞员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="20" />
        </el-form-item>
        <el-form-item label="负责分区" prop="zoneId">
          <el-select v-model="form.zoneId" placeholder="片长需选择负责分区" clearable style="width: 100%">
            <el-option v-for="z in zones" :key="z.zoneId" :label="z.zoneName" :value="z.zoneId" />
          </el-select>
        </el-form-item>
        <el-form-item label="班组" prop="teamName">
          <el-input v-model="form.teamName" placeholder="所属队伍/班组" maxlength="50" />
        </el-form-item>
        <el-form-item label="登录名" prop="username">
          <el-input v-model="form.username" placeholder="关联 sys_user 登录名（H5 登录用）" maxlength="64" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TaihuWorker">
import { listWorker, getWorker, addWorker, updateWorker, delWorker } from '@/api/taihu/workers'
import { listZone } from '@/api/taihu/zones'
import { pick } from '@/utils/taihu'

const { proxy } = getCurrentInstance()

const workerList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const zones = ref([])
const visible = ref(false)
const title = ref('')

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    workerName: undefined,
    role: undefined,
    status: undefined
  },
  form: {},
  rules: {
    workerName: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
    role: [{ required: true, message: '请选择角色', trigger: 'change' }],
    phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

function roleText(role) {
  return role === '1' ? '片长' : '打捞员'
}

function zoneName(zoneId) {
  const z = zones.value.find(z => z.zoneId === zoneId)
  return z ? z.zoneName : ''
}

function getList() {
  loading.value = true
  listWorker(queryParams.value).then(res => {
    workerList.value = res.rows
    total.value = res.total
    loading.value = false
  }).catch(() => { loading.value = false })
}

function loadZones() {
  listZone({ pageNum: 1, pageSize: 100 }).then(res => {
    zones.value = res.rows ?? res.data ?? []
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function reset() {
  form.value = {
    workerId: undefined,
    workerName: undefined,
    role: '2',
    phone: undefined,
    zoneId: undefined,
    teamName: undefined,
    username: undefined,
    status: '0'
  }
  proxy.resetForm("workerRef")
}

function handleAdd() {
  reset()
  visible.value = true
  title.value = '新增作业人员'
}

function handleUpdate(row) {
  reset()
  const id = pick(row, 'worker_id', 'workerId') ?? row.workerId
  getWorker(id).then(res => {
    const d = res.data ?? res
    form.value = { ...d, role: String(d.role ?? '2'), status: String(d.status ?? '0') }
    visible.value = true
    title.value = '修改作业人员'
  })
}

function submitForm() {
  proxy.$refs["workerRef"].validate(valid => {
    if (!valid) return
    if (form.value.workerId != null) {
      updateWorker(form.value).then(() => {
        proxy.$modal.msgSuccess('修改成功')
        visible.value = false
        getList()
      })
    } else {
      addWorker(form.value).then(() => {
        proxy.$modal.msgSuccess('新增成功')
        visible.value = false
        getList()
      })
    }
  })
}

function handleDelete(row) {
  const id = pick(row, 'worker_id', 'workerId') ?? row.workerId
  proxy.$modal.confirm(`确认删除人员「${row.workerName}」？`).then(() => {
    return delWorker(id)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

function cancel() {
  visible.value = false
  reset()
}

loadZones()
getList()
</script>
