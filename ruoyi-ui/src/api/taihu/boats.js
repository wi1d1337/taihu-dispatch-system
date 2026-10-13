import request from '@/utils/request'

// 查询船只列表
export function listBoat(query) {
  return request({
    url: '/taihu/boats/list',
    method: 'get',
    params: query
  })
}

// 查询船只详细
export function getBoat(boatId) {
  return request({
    url: '/taihu/boats/' + boatId,
    method: 'get'
  })
}

// 新增船只
export function addBoat(data) {
  return request({
    url: '/taihu/boats',
    method: 'post',
    data: data
  })
}

// 修改船只
export function updateBoat(data) {
  return request({
    url: '/taihu/boats',
    method: 'put',
    data: data
  })
}

// 删除船只
export function delBoat(boatId) {
  return request({
    url: '/taihu/boats/' + boatId,
    method: 'delete'
  })
}
