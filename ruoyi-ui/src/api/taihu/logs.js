import request from '@/utils/request'

// 查询打捞台账列表（契约 §4：GET /taihu/logs…，按若依 CRUD 风格）
export function listLog(query) {
  return request({
    url: '/taihu/logs/list',
    method: 'get',
    params: query
  })
}

// 查询台账详细
export function getLog(logId) {
  return request({
    url: '/taihu/logs/' + logId,
    method: 'get'
  })
}

// 新增台账（POST 支持带 orderId，自动带出船/分区/日期）
export function addLog(data) {
  return request({
    url: '/taihu/logs',
    method: 'post',
    data: data
  })
}

// 修改台账
export function updateLog(data) {
  return request({
    url: '/taihu/logs',
    method: 'put',
    data: data
  })
}

// 删除台账
export function delLog(logId) {
  return request({
    url: '/taihu/logs/' + logId,
    method: 'delete'
  })
}
