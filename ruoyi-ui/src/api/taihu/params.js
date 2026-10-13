import request from '@/utils/request'

// 算法参数注册表（含 autotune.* 五参数）
export function listParams() {
  return request({
    url: '/taihu/params',
    method: 'get'
  })
}

// 修改参数（body: value, operator）→ 写库 + 版本递增 + 变更留痕
export function updateParam(key, data) {
  return request({
    url: '/taihu/params/' + key,
    method: 'put',
    data: data
  })
}

// 立即自动调参（回测 → 有界调参，taihu:param:edit 权限）
export function runAutotune() {
  return request({
    url: '/taihu/autotune/run',
    method: 'post',
    timeout: 120000
  })
}

// 自动调参历史：参数值/命中率随时间曲线 + 每次调整证据
export function getAutotuneHistory() {
  return request({
    url: '/taihu/autotune/history',
    method: 'get'
  })
}
