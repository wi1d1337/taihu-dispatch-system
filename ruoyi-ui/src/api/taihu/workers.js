import request from '@/utils/request'

// 查询作业人员列表（role: 1片长 2打捞员）
export function listWorker(query) {
  return request({
    url: '/taihu/workers/list',
    method: 'get',
    params: query
  })
}

// 查询人员详细
export function getWorker(workerId) {
  return request({
    url: '/taihu/workers/' + workerId,
    method: 'get'
  })
}

// 新增人员
export function addWorker(data) {
  return request({
    url: '/taihu/workers',
    method: 'post',
    data: data
  })
}

// 修改人员
export function updateWorker(data) {
  return request({
    url: '/taihu/workers',
    method: 'put',
    data: data
  })
}

// 删除人员
export function delWorker(workerId) {
  return request({
    url: '/taihu/workers/' + workerId,
    method: 'delete'
  })
}
