import request from '@/utils/request'

// 查询分离站列表
export function listStation(query) {
  return request({
    url: '/taihu/stations/list',
    method: 'get',
    params: query
  })
}

// 查询分离站详细
export function getStation(stationId) {
  return request({
    url: '/taihu/stations/' + stationId,
    method: 'get'
  })
}

// 新增分离站
export function addStation(data) {
  return request({
    url: '/taihu/stations',
    method: 'post',
    data: data
  })
}

// 修改分离站
export function updateStation(data) {
  return request({
    url: '/taihu/stations',
    method: 'put',
    data: data
  })
}

// 删除分离站
export function delStation(stationId) {
  return request({
    url: '/taihu/stations/' + stationId,
    method: 'delete'
  })
}
