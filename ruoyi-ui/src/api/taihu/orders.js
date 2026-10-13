import request from '@/utils/request'

// 查询调度单列表（分页：date/status/halfDay 过滤，联表返回 boatName/zoneName/stationName）
export function listOrder(query) {
  return request({
    url: '/taihu/orders/list',
    method: 'get',
    params: query
  })
}

// 调度单详情
export function getOrder(orderId) {
  return request({
    url: '/taihu/orders/' + orderId,
    method: 'get'
  })
}

// 待确认 → 已确认
export function confirmOrder(orderId) {
  return request({
    url: '/taihu/orders/' + orderId + '/confirm',
    method: 'put'
  })
}

// 待确认 → 已驳回（remark 必填驳回原因）
export function rejectOrder(orderId, remark) {
  return request({
    url: '/taihu/orders/' + orderId + '/reject',
    method: 'put',
    data: { remark }
  })
}

// 已确认 → 已完成（actualTons 选填，缺省取台账汇总）
export function doneOrder(orderId, actualTons) {
  return request({
    url: '/taihu/orders/' + orderId + '/done',
    method: 'put',
    data: { actualTons }
  })
}
