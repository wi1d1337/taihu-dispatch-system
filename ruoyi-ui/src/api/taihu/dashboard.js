import request from '@/utils/request'

// 运行看板数据（后端合并 algo /dashboard/eval 与 /history/series）
// 期望结构：{ kpi:{POD,FAR,CSI}, daily:[{date,planTons,actualTons,absDev}],
//            zoneStats:[...], history:{dates:[], zones:{分区名: chla[]}} }
export function getDashboard(query) {
  return request({
    url: '/taihu/dashboard',
    method: 'get',
    params: query
  })
}
