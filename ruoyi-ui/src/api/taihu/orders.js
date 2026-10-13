import request from '@/utils/request'

// 工单分页列表（过滤：date/zoneId/level/status，联表返回 zoneName/leaderName/boatName）
export function listOrder(query) {
  return request({
    url: '/taihu/orders/list',
    method: 'get',
    params: query
  })
}

// 工单详情（含流转日志 logs，字段以后端实现为准）
export function getOrder(orderId) {
  return request({
    url: '/taihu/orders/' + orderId,
    method: 'get'
  })
}

// 人工创建工单（source=2）
export function addOrder(data) {
  return request({
    url: '/taihu/orders',
    method: 'post',
    data: data
  })
}

// 撤销（status∈{0,1}，revokeReason 必填）→ 6
export function revokeOrder(orderId, revokeReason) {
  return request({
    url: '/taihu/orders/' + orderId + '/revoke',
    method: 'put',
    data: { revokeReason }
  })
}

// 改派（leaderId）→ 回 0，记日志
export function reassignOrder(orderId, leaderId) {
  return request({
    url: '/taihu/orders/' + orderId + '/reassign',
    method: 'put',
    data: { leaderId }
  })
}

// 核查（status=3 → pass=true→4 / false→5+checkComment）
export function checkOrder(orderId, pass, checkComment) {
  return request({
    url: '/taihu/orders/' + orderId + '/check',
    method: 'put',
    data: { pass, checkComment }
  })
}
