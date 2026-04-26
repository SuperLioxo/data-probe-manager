/**
 * 主题工具函数
 * 用于获取CSS变量值和提供常用颜色快捷方法
 */

/**
 * 获取CSS变量值
 * @param {string} variableName - CSS变量名（包含--前缀，如 '--bg-primary'）
 * @returns {string} 变量值
 */
export function getThemeVariable(variableName) {
  const style = getComputedStyle(document.documentElement)
  return style.getPropertyValue(variableName).trim()
}

/**
 * 常用颜色快捷方法
 * 提供访问常用主题颜色的便捷函数
 */
export const themeColors = {
  // 背景色
  bgPrimary: () => getThemeVariable('--bg-primary'),
  bgSecondary: () => getThemeVariable('--bg-secondary'),
  bgTertiary: () => getThemeVariable('--bg-tertiary'),
  bgCard: () => getThemeVariable('--bg-card'),
  bgElevated: () => getThemeVariable('--bg-elevated'),
  bgHover: () => getThemeVariable('--bg-hover'),
  bgActive: () => getThemeVariable('--bg-active'),

  // 文字颜色
  textPrimary: () => getThemeVariable('--text-primary'),
  textSecondary: () => getThemeVariable('--text-secondary'),
  textTertiary: () => getThemeVariable('--text-tertiary'),
  textDisabled: () => getThemeVariable('--text-disabled'),
  textInverse: () => getThemeVariable('--text-inverse'),

  // 边框颜色
  borderColor: () => getThemeVariable('--border-color'),
  borderHover: () => getThemeVariable('--border-hover'),
  borderFocus: () => getThemeVariable('--border-focus'),

  // 输入框
  inputBg: () => getThemeVariable('--input-bg'),
  inputBgHover: () => getThemeVariable('--input-bg-hover'),
  inputBorder: () => getThemeVariable('--input-border'),

  // 主题色
  primary: () => getThemeVariable('--primary'),
  primaryHover: () => getThemeVariable('--primary-hover'),
  primaryLight: () => getThemeVariable('--primary-light'),

  // 功能色
  success: () => getThemeVariable('--success'),
  warning: () => getThemeVariable('--warning'),
  error: () => getThemeVariable('--error'),
  info: () => getThemeVariable('--info'),

  // Element Plus 组件色
  elPrimary: () => getThemeVariable('--el-color-primary'),
  elSuccess: () => getThemeVariable('--el-color-success'),
  elWarning: () => getThemeVariable('--el-color-warning'),
  elDanger: () => getThemeVariable('--el-color-danger'),
  elInfo: () => getThemeVariable('--el-color-info'),

  // 探针类型色
  probeTypeSystem: () => getThemeVariable('--probe-type-system'),
  probeTypeApplication: () => getThemeVariable('--probe-type-application'),
  probeTypeNetwork: () => getThemeVariable('--probe-type-network'),
  probeTypeCustom: () => getThemeVariable('--probe-type-custom'),
  probeTypeFile: () => getThemeVariable('--probe-type-file'),
  probeTypeDatabase: () => getThemeVariable('--probe-type-database'),

  // 渐变色
  gradientCard: () => getThemeVariable('--gradient-card'),
  gradientHeader: () => getThemeVariable('--gradient-header'),

  // 阴影
  shadowCard: () => getThemeVariable('--shadow-card'),
  shadowCardHover: () => getThemeVariable('--shadow-card-hover'),
  shadowSm: () => getThemeVariable('--shadow-sm'),
  shadowMd: () => getThemeVariable('--shadow-md'),
  shadowLg: () => getThemeVariable('--shadow-lg'),
  shadowXl: () => getThemeVariable('--shadow-xl'),
}

/**
 * 获取探针类型对应的颜色
 * @param {string} type - 探针类型
 * @returns {string} 对应的颜色值
 */
export function getProbeTypeColor(type) {
  const colorMap = {
    'system': themeColors.probeTypeSystem(),
    'application': themeColors.probeTypeApplication(),
    'network': themeColors.probeTypeNetwork(),
    'custom': themeColors.probeTypeCustom(),
    'file': themeColors.probeTypeFile(),
    'database': themeColors.probeTypeDatabase(),
  }
  return colorMap[type] || themeColors.elInfo()
}

/**
 * 获取状态对应的颜色
 * @param {string} status - 状态值
 * @returns {string} 对应的颜色值
 */
export function getStatusColor(status) {
  const colorMap = {
    'online': themeColors.elSuccess(),
    'offline': themeColors.elInfo(),
    'error': themeColors.elDanger(),
    'maintenance': themeColors.elWarning(),
  }
  return colorMap[status] || themeColors.elInfo()
}

/**
 * 获取告警级别对应的颜色
 * @param {string} level - 告警级别
 * @returns {string} 对应的颜色值
 */
export function getAlertLevelColor(level) {
  const colorMap = {
    'critical': themeColors.elDanger(),
    'major': themeColors.elWarning(),
    'minor': themeColors.elPrimary(),
    'warning': themeColors.elWarning(),
  }
  return colorMap[level] || themeColors.elInfo()
}
