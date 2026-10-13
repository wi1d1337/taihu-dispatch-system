import request from '@/utils/request'

// 查询分区列表
export function listZone(query) {
  return request({
    url: '/taihu/zones/list',
    method: 'get',
    params: query
  })
}

// 查询分区详细
export function getZone(zoneId) {
  return request({
    url: '/taihu/zones/' + zoneId,
    method: 'get'
  })
}

// 新增分区
export function addZone(data) {
  return request({
    url: '/taihu/zones',
    method: 'post',
    data: data
  })
}

// 修改分区
export function updateZone(data) {
  return request({
    url: '/taihu/zones',
    method: 'put',
    data: data
  })
}

// 删除分区
export function delZone(zoneId) {
  return request({
    url: '/taihu/zones/' + zoneId,
    method: 'delete'
  })
}

// 分区 GeoJSON（FeatureCollection，代理 algo /zones/geojson）
export function getGeojson() {
  return request({
    url: '/taihu/geojson',
    method: 'get'
  })
}
