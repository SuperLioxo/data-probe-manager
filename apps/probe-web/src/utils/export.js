/**
 * 文件导出工具函数
 */

/**
 * 导出文件
 * @param {Blob} blob - 文件数据
 * @param {string} filename - 文件名
 */
export function exportFile(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/**
 * 从响应头中获取文件名
 * @param {string} disposition - Content-Disposition header
 * @returns {string} 文件名
 */
export function getFilenameFromHeader(disposition) {
  if (!disposition) return 'export.xlsx'
  const filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/
  const matches = filenameRegex.exec(disposition)
  if (matches != null && matches[1]) {
    return matches[1].replace(/['"]/g, '')
  }
  return 'export.xlsx'
}

/**
 * 导出Excel文件
 * @param {Blob} blob - Excel文件数据
 * @param {string} defaultName - 默认文件名
 */
export function exportExcelFile(blob, defaultName = 'export.xlsx') {
  exportFile(blob, defaultName)
}
