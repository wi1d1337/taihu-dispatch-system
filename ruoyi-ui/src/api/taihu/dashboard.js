import request from '@/utils/request'

// 考核看板（days=30）：响应时长分布（派单→接单/到场/办结）、办结率、
// 日生日清率、超时督办次数、各分区吨数（代理 algo /dashboard/eval）
export function getDashboard(query) {
  return request({
    url: '/taihu/dashboard',
    method: 'get',
    params: query
  })
}
