import request from '@/utils/request'

// 日报数据（date=YYYY-MM-DD）：当日工单汇总 + 吨数 + 未办结清单
export function getDailyReport(query) {
  return request({
    url: '/taihu/daily/report',
    method: 'get',
    params: query
  })
}
