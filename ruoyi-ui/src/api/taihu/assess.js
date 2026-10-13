import request from '@/utils/request'

// 今日研判：六分区同比外推定级 + basis 明细，概率预报(prob/q25/q50/q75)并列，含参数与模型版本
export function getAssessToday() {
  return request({
    url: '/taihu/assess/today',
    method: 'get'
  })
}

// 研判回验（days=30）：预测等级 vs 次日实际，输出命中/误报/漏报
export function getAssessAccuracy(query) {
  return request({
    url: '/taihu/assess/accuracy',
    method: 'get',
    params: query
  })
}
