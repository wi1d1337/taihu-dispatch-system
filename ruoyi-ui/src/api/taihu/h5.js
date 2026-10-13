import request from '@/utils/request'

// —— H5 移动端接口（免动态菜单，仍走 JWT；路径前缀以后端底稿为准） ——

// H5 登录（复用若依账号体系，返回 token+角色）
export function h5Login(data) {
  return request({
    url: '/taihu/h5/login',
    method: 'post',
    data: data
  })
}

// 当前用户信息+角色（1片长 2打捞员，以后端返回为准）
export function h5Me() {
  return request({
    url: '/taihu/h5/me',
    method: 'get'
  })
}

// 我的工单（片长=派给我的；打捞员=crew_ids 含我）
export function h5MyOrders(query) {
  return request({
    url: '/taihu/h5/my/orders',
    method: 'get',
    params: query
  })
}

// 片长接单（boatId, crewIds 必填）→ 1
export function h5Accept(orderId, data) {
  return request({
    url: '/taihu/h5/orders/' + orderId + '/accept',
    method: 'put',
    data: data
  })
}

// 到场打卡（lng, lat）→ 2
export function h5Arrive(orderId, data) {
  return request({
    url: '/taihu/h5/orders/' + orderId + '/arrive',
    method: 'put',
    data: data
  })
}

// 提交完成（actualTons 必填, photoUrls 选填）→ 3
export function h5Submit(orderId, data) {
  return request({
    url: '/taihu/h5/orders/' + orderId + '/submit',
    method: 'put',
    data: data
  })
}

// 照片上传（复用若依 /common/upload，返回 fileName）
export function uploadPhoto(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/common/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data', repeatSubmit: false },
    timeout: 60000
  })
}
