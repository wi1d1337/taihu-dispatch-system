import request from '@/utils/request'

// 生成今日调度单（代理 algo /pipeline/run-daily，仅管理员）
export function runPipeline(data) {
  return request({
    url: '/taihu/pipeline/run',
    method: 'post',
    data: data
  })
}

// 重置演示数据（代理 algo /seed，仅管理员，演示重置用）
export function seedDemoData(data) {
  return request({
    url: '/taihu/seed',
    method: 'post',
    data: data
  })
}
