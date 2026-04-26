import request from './request'

export function getHttpApiSources(params) {
  return request({ url: '/http-api-sources', method: 'get', params })
}

export function getHttpApiSource(id) {
  return request({ url: `/http-api-sources/${id}`, method: 'get' })
}

export function createHttpApiSource(data) {
  return request({ url: '/http-api-sources', method: 'post', data })
}

export function updateHttpApiSource(id, data) {
  return request({ url: `/http-api-sources/${id}`, method: 'put', data })
}

export function deleteHttpApiSource(id) {
  return request({ url: `/http-api-sources/${id}`, method: 'delete' })
}
