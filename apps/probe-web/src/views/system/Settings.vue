<template>
  <div class="settings-container">
    <!-- 顶部搜索栏和操作按钮 -->
    <div class="settings-header">
      <el-input
        v-model="searchQuery"
        placeholder="搜索设置项..."
        :prefix-icon="Search"
        clearable
        class="search-input"
        aria-label="搜索设置项"
        @input="handleSearch"
      >
        <template #suffix>
          <span v-if="filteredItems.length > 0" class="search-count" aria-live="polite">
            {{ filteredItems.length }} 个结果
          </span>
        </template>
      </el-input>

      <div class="header-actions">
        <el-button-group>
          <el-button
            @click="togglePreviewMode"
            :type="isPreviewMode ? 'warning' : 'default'"
            :aria-label="isPreviewMode ? '退出预览模式' : '进入预览模式'"
            :aria-pressed="isPreviewMode"
          >
            <el-icon><View /></el-icon>
            {{ isPreviewMode ? '退出预览' : '预览模式' }}
          </el-button>
          <el-dropdown @command="handleActionCommand" trigger="click">
            <el-button aria-label="更多操作菜单">
              更多操作
              <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu aria-label="操作菜单">
                <el-dropdown-item command="export">
                  <el-icon><Download /></el-icon>
                  导出设置
                </el-dropdown-item>
                <el-dropdown-item command="import">
                  <el-icon><Upload /></el-icon>
                  导入设置
                </el-dropdown-item>
                <el-dropdown-item command="reset" divided>
                  <el-icon><RefreshLeft /></el-icon>
                  重置当前分类
                </el-dropdown-item>
                <el-dropdown-item command="resetAll">
                  <el-icon><RefreshLeft /></el-icon>
                  重置所有设置
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-button-group>
      </div>
    </div>

    <!-- 搜索结果下拉 -->
    <div v-if="searchQuery && filteredItems.length > 0" class="search-results" role="listbox" :aria-label="'搜索结果，共' + filteredItems.length + '项'">
      <button
        v-for="item in filteredItems"
        :key="item.key"
        class="search-result-item"
        role="option"
        :aria-selected="false"
        @click="jumpToSetting(item)"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <div class="item-info">
          <div class="item-label">{{ item.label }}</div>
          <div class="item-category">{{ getCategoryName(item.category) }}</div>
        </div>
        <el-icon><ArrowRight /></el-icon>
      </button>
    </div>

    <!-- 主布局 -->
    <div class="settings-layout">
      <!-- 侧边导航 -->
      <nav class="settings-nav-card" :class="{ 'collapsed': navCollapsed }" aria-label="设置分类导航">
        <div class="nav-header">
          <span v-if="!navCollapsed">设置分类</span>
          <button
            class="collapse-trigger touch-target"
            :aria-label="navCollapsed ? '展开导航' : '折叠导航'"
            :aria-expanded="!navCollapsed"
            @click="navCollapsed = !navCollapsed"
          >
            <el-icon><ArrowLeft v-if="!navCollapsed" /><ArrowRight v-else /></el-icon>
          </button>
        </div>
        <el-menu
          :default-active="activeTab"
          mode="vertical"
          :collapse="navCollapsed"
          :collapse-transition="true"
          @select="handleTabSelect"
          class="settings-menu"
          role="menu"
        >
          <el-menu-item index="general" role="menuitem">
            <el-icon><Setting /></el-icon>
            <span>通用设置</span>
          </el-menu-item>
          <el-menu-item index="appearance" role="menuitem">
            <el-icon><Brush /></el-icon>
            <span>外观设置</span>
          </el-menu-item>
          <el-menu-item index="notification" role="menuitem">
            <el-icon><Bell /></el-icon>
            <span>通知设置</span>
          </el-menu-item>
          <el-menu-item index="security" role="menuitem">
            <el-icon><Lock /></el-icon>
            <span>安全设置</span>
          </el-menu-item>
          <el-menu-item index="system" role="menuitem">
            <el-icon><Monitor /></el-icon>
            <span>系统设置</span>
          </el-menu-item>
        </el-menu>
      </nav>

      <!-- 设置内容区 -->
      <el-card class="settings-content-card">
        <!-- 通用设置 -->
        <div v-show="activeTab === 'general'" class="settings-panel">
          <div class="panel-header">
            <h3>通用设置</h3>
            <p>配置系统的基本行为和偏好</p>
          </div>

          <el-form label-width="150px" class="settings-form">
            <el-form-item label="语言">
              <el-select v-model="generalSettings.language" style="width: 200px">
                <el-option label="简体中文" value="zh-CN" />
                <el-option label="繁体中文" value="zh-TW" />
                <el-option label="English" value="en-US" />
              </el-select>
            </el-form-item>

            <el-form-item label="时区">
              <el-select v-model="generalSettings.timezone" style="width: 200px">
                <el-option label="GMT+8 北京时间" value="GMT+8" />
                <el-option label="GMT+0 格林威治" value="GMT+0" />
                <el-option label="GMT-5 东部时间" value="GMT-5" />
              </el-select>
            </el-form-item>

            <el-form-item label="日期格式">
              <el-select v-model="generalSettings.dateFormat" style="width: 200px">
                <el-option label="YYYY-MM-DD" value="YYYY-MM-DD" />
                <el-option label="DD/MM/YYYY" value="DD/MM/YYYY" />
                <el-option label="MM/DD/YYYY" value="MM/DD/YYYY" />
              </el-select>
            </el-form-item>

            <el-form-item label="时间格式">
              <el-radio-group v-model="generalSettings.timeFormat">
                <el-radio value="24h">24小时制</el-radio>
                <el-radio value="12h">12小时制</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="分页大小">
              <el-select v-model="generalSettings.pageSize" style="width: 150px">
                <el-option label="10条/页" :value="10" />
                <el-option label="20条/页" :value="20" />
                <el-option label="50条/页" :value="50" />
                <el-option label="100条/页" :value="100" />
              </el-select>
              <span class="form-item-desc">每页显示的数据条数</span>
            </el-form-item>

            <el-form-item label="自动刷新间隔">
              <el-select v-model="generalSettings.refreshInterval" style="width: 200px">
                <el-option label="不自动刷新" :value="0" />
                <el-option label="10秒" :value="10" />
                <el-option label="30秒" :value="30" />
                <el-option label="1分钟" :value="60" />
                <el-option label="5分钟" :value="300" />
              </el-select>
              <span class="form-item-desc">数据自动刷新时间间隔</span>
            </el-form-item>
          </el-form>
        </div>

        <!-- 外观设置 -->
        <div v-show="activeTab === 'appearance'" class="settings-panel">
          <div class="panel-header">
            <h3>外观设置</h3>
            <p>自定义系统的外观和主题</p>
          </div>

          <el-form label-width="150px" class="settings-form">
            <el-form-item>
              <template #label>
                <span class="form-label">主题色</span>
              </template>
              <div
                class="color-picker-group"
                role="radiogroup"
                :aria-label="'选择主题色，当前为' + (themeColors.find(c => c.value === appearanceSettings.primaryColor)?.name || '默认色')"
              >
                <button
                  v-for="color in themeColors"
                  :key="color.value"
                  class="color-option"
                  :class="{ active: appearanceSettings.primaryColor === color.value }"
                  :style="{ background: color.value }"
                  :aria-label="'选择' + color.name + '主题色'"
                  :aria-pressed="appearanceSettings.primaryColor === color.value"
                  type="button"
                  @click="handleColorChange(color.value)"
                >
                  <el-icon v-if="appearanceSettings.primaryColor === color.value" aria-hidden="true">
                    <Check />
                  </el-icon>
                </button>
              </div>
            </el-form-item>

            <el-form-item label="侧边栏宽度">
              <el-radio-group v-model="appearanceSettings.sidebarWidth">
                <el-radio value="narrow">窄</el-radio>
                <el-radio value="medium">中</el-radio>
                <el-radio value="wide">宽</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="动画效果">
              <el-switch v-model="appearanceSettings.animation" />
              <span class="form-item-desc">启用界面动画和过渡效果</span>
            </el-form-item>

            <el-form-item label="紧凑模式">
              <el-switch v-model="appearanceSettings.compact" />
              <span class="form-item-desc">使用更紧凑的界面布局</span>
            </el-form-item>
          </el-form>
        </div>

        <!-- 通知设置 -->
        <div v-show="activeTab === 'notification'" class="settings-panel">
          <div class="panel-header">
            <h3>通知设置</h3>
            <p>配置告警和系统通知方式</p>
          </div>

          <el-form label-width="150px" class="settings-form">
            <div class="setting-group">
              <h4>桌面通知</h4>
              <el-form-item label="启用桌面通知">
                <el-switch v-model="notificationSettings.desktop" />
                <span class="form-item-desc">在浏览器桌面显示通知</span>
              </el-form-item>

              <el-form-item label="告警通知">
                <el-switch v-model="notificationSettings.alert" />
                <span class="form-item-desc">接收告警级别的通知</span>
              </el-form-item>

              <el-form-item label="系统通知">
                <el-switch v-model="notificationSettings.system" />
                <span class="form-item-desc">接收系统级别的通知</span>
              </el-form-item>
            </div>

            <div class="setting-group">
              <h4>声音通知</h4>
              <el-form-item label="启用声音">
                <el-switch v-model="notificationSettings.sound" />
                <span class="form-item-desc">通知时播放提示音</span>
              </el-form-item>

              <el-form-item label="告警声音">
                <el-select v-model="notificationSettings.alertSound" style="width: 200px">
                  <el-option label="默认提示音" value="default" />
                  <el-option label="柔和提示音" value="soft" />
                  <el-option label="紧急提示音" value="urgent" />
                </el-select>
              </el-form-item>

              <el-form-item label="音量">
                <el-slider
                  v-model="notificationSettings.volume"
                  :min="0"
                  :max="100"
                  style="width: 200px"
                />
                <span class="form-item-desc">{{ notificationSettings.volume }}%</span>
              </el-form-item>
            </div>

            <div class="setting-group">
              <h4>邮件通知</h4>
              <el-form-item label="启用邮件通知">
                <el-switch v-model="notificationSettings.email" />
              </el-form-item>

              <el-form-item label="邮箱地址">
                <el-input
                  v-model="notificationSettings.emailAddress"
                  placeholder="请输入邮箱地址"
                  style="width: 300px"
                  :disabled="!notificationSettings.email"
                />
              </el-form-item>

              <el-form-item label="发送频率">
                <el-checkbox-group v-model="notificationSettings.emailFrequency" :disabled="!notificationSettings.email">
                  <el-checkbox value="immediate">立即发送</el-checkbox>
                  <el-checkbox value="hourly">每小时汇总</el-checkbox>
                  <el-checkbox value="daily">每日汇总</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
            </div>
          </el-form>
        </div>

        <!-- 安全设置 -->
        <div v-show="activeTab === 'security'" class="settings-panel">
          <div class="panel-header">
            <h3>安全设置</h3>
            <p>配置系统安全相关参数</p>
          </div>

          <el-form label-width="150px" class="settings-form">
            <el-form-item label="会话超时">
              <el-input-number
                v-model="securitySettings.sessionTimeout"
                :min="5"
                :max="480"
                :step="5"
                style="width: 150px"
              />
              <span class="form-item-desc">分钟，无操作后自动登出</span>
            </el-form-item>

            <el-form-item label="单点登录">
              <el-switch v-model="securitySettings.singleSignOn" />
              <span class="form-item-desc">启用单点登录功能</span>
            </el-form-item>

            <el-form-item label="操作日志">
              <el-switch v-model="securitySettings.logOperations" />
              <span class="form-item-desc">记录用户操作日志</span>
            </el-form-item>

            <el-form-item label="日志保留">
              <el-input-number
                v-model="securitySettings.logRetention"
                :min="1"
                :max="365"
                style="width: 150px"
              />
              <span class="form-item-desc">天，超过此时间的日志将被清理</span>
            </el-form-item>

            <el-form-item label="IP白名单">
              <el-switch v-model="securitySettings.ipWhitelist" />
              <span class="form-item-desc">只允许白名单IP访问</span>
            </el-form-item>

            <el-form-item v-if="securitySettings.ipWhitelist" label="白名单列表">
              <div class="ip-list">
                <el-tag
                  v-for="(ip, index) in securitySettings.whitelistIPs"
                  :key="index"
                  closable
                  @close="removeWhitelistIP(index)"
                  style="margin-right: 8px; margin-bottom: 8px"
                >
                  {{ ip }}
                </el-tag>
                <el-input
                  v-model="newIP"
                  placeholder="输入IP地址或段"
                  style="width: 200px; margin-right: 8px"
                  @keyup.enter="addWhitelistIP"
                />
                <el-button @click="addWhitelistIP" size="small">
                  <el-icon><Plus /></el-icon>
                  添加
                </el-button>
              </div>
              <span class="form-item-desc">支持单个IP（192.168.1.1）或IP段（192.168.1.0/24）</span>
            </el-form-item>
          </el-form>
        </div>

        <!-- 系统设置 -->
        <div v-show="activeTab === 'system'" class="settings-panel">
          <div class="panel-header">
            <h3>系统设置</h3>
            <p>配置探针监控和数据处理参数</p>
          </div>

          <el-form label-width="150px" class="settings-form">
            <el-form-item label="监控间隔">
              <el-input-number
                v-model="systemSettings.defaultInterval"
                :min="1"
                :max="3600"
                :step="10"
                style="width: 150px"
              />
              <span class="form-item-desc">秒，探针默认采集间隔</span>
            </el-form-item>

            <el-form-item label="数据保留">
              <el-input-number
                v-model="systemSettings.dataRetention"
                :min="1"
                :max="365"
                style="width: 150px"
              />
              <span class="form-item-desc">天，超过此时间的监控数据将被清理</span>
            </el-form-item>

            <div class="setting-group">
              <h4>告警阈值</h4>
              <el-form-item label="CPU阈值">
                <el-input-number
                  v-model="systemSettings.cpuThreshold"
                  :min="0"
                  :max="100"
                  style="width: 150px"
                />
                <span class="form-item-desc">%，CPU使用率超过此值触发告警</span>
              </el-form-item>

              <el-form-item label="内存阈值">
                <el-input-number
                  v-model="systemSettings.memoryThreshold"
                  :min="0"
                  :max="100"
                  style="width: 150px"
                />
                <span class="form-item-desc">%，内存使用率超过此值触发告警</span>
              </el-form-item>

              <el-form-item label="告警静默">
                <el-input-number
                  v-model="systemSettings.alertSilence"
                  :min="0"
                  :max="1440"
                  style="width: 150px"
                />
                <span class="form-item-desc">分钟，同一告警的静默时间</span>
              </el-form-item>
            </div>

            <div class="setting-group">
              <h4>性能优化</h4>
              <el-form-item label="启用缓存">
                <el-switch v-model="systemSettings.enableCache" />
                <span class="form-item-desc">启用数据缓存提升查询性能</span>
              </el-form-item>

              <el-form-item v-if="systemSettings.enableCache" label="缓存时间">
                <el-input-number
                  v-model="systemSettings.cacheTime"
                  :min="1"
                  :max="60"
                  style="width: 150px"
                />
                <span class="form-item-desc">分钟，缓存数据的有效期</span>
              </el-form-item>

              <el-form-item label="最大连接数">
                <el-input-number
                  v-model="systemSettings.maxConnections"
                  :min="10"
                  :max="1000"
                  :step="10"
                  style="width: 150px"
                />
                <span class="form-item-desc">同时连接的最大探针数量</span>
              </el-form-item>
            </div>
          </el-form>
        </div>
      </el-card>
    </div>

    <!-- 底部操作栏 -->
    <div class="settings-footer" :class="{ 'preview-mode': isPreviewMode }">
      <div v-if="isPreviewMode" class="preview-info">
        <el-icon><InfoFilled /></el-icon>
        <span>预览模式：更改将在退出预览后生效</span>
      </div>
      <div class="footer-actions">
        <el-button @click="handleReset" :disabled="saving">
          <el-icon><RefreshLeft /></el-icon>
          重置当前分类
        </el-button>
        <el-button
          type="primary"
          @click="handleSave"
          :loading="saving"
          :disabled="isPreviewMode"
        >
          <el-icon><Check /></el-icon>
          保存设置
        </el-button>
        <el-button
          v-if="isPreviewMode"
          type="success"
          @click="exitPreviewMode(true)"
        >
          <el-icon><Check /></el-icon>
          应用更改
        </el-button>
        <el-button
          v-if="isPreviewMode"
          @click="exitPreviewMode(false)"
        >
          <el-icon><Close /></el-icon>
          取消
        </el-button>
      </div>
    </div>

    <!-- 导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入设置"
      width="500px"
    >
      <el-upload
        drag
        :auto-upload="false"
        :on-change="handleFileChange"
        :limit="1"
        accept=".json"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            只支持 .json 格式的设置文件
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="confirmImport"
          :loading="importing"
          :disabled="!importFile"
        >
          导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, View, ArrowDown, Download, Upload,
  RefreshLeft, UploadFilled, Setting, Brush,
  Bell, Lock, Monitor, Check, ArrowRight,
  Plus, InfoFilled, Close, ArrowLeft
} from '@element-plus/icons-vue'
import * as SettingUtils from '@/utils/settings'

// 状态管理
const activeTab = ref('general')
const searchQuery = ref('')
const navCollapsed = ref(false)
const saving = ref(false)
const importing = ref(false)
const isPreviewMode = ref(false)
const previewSnapshot = ref(null)
const importDialogVisible = ref(false)
const importFile = ref(null)
const newIP = ref('')

// 响应式设置对象
const generalSettings = reactive({ ...SettingUtils.DEFAULT_SETTINGS.general })
const appearanceSettings = reactive({ ...SettingUtils.DEFAULT_SETTINGS.appearance })
const notificationSettings = reactive({ ...SettingUtils.DEFAULT_SETTINGS.notification })
const securitySettings = reactive({ ...SettingUtils.DEFAULT_SETTINGS.security })
const systemSettings = reactive({ ...SettingUtils.DEFAULT_SETTINGS.system })

// 主题色选项
const themeColors = [
  { name: '默认蓝', value: '#409eff' },
  { name: '成功绿', value: '#67c23a' },
  { name: '警告橙', value: '#e6a23c' },
  { name: '危险红', value: '#f56c6c' },
  { name: '紫色', value: '#9c27b0' },
  { name: '青色', value: '#00bcd4' }
]

// 设置项索引（用于搜索）
const settingItems = computed(() => {
  const items = [
    // 通用设置
    { category: 'general', label: '语言', key: 'language', icon: 'Globe' },
    { category: 'general', label: '时区', key: 'timezone', icon: 'Clock' },
    { category: 'general', label: '日期格式', key: 'dateFormat', icon: 'Calendar' },
    { category: 'general', label: '分页大小', key: 'pageSize', icon: 'List' },
    { category: 'general', label: '刷新间隔', key: 'refreshInterval', icon: 'Refresh' },
    // 外观设置
    { category: 'appearance', label: '主题色', key: 'primaryColor', icon: 'Brush' },
    { category: 'appearance', label: '侧边栏宽度', key: 'sidebarWidth', icon: 'Menu' },
    { category: 'appearance', label: '动画效果', key: 'animation', icon: 'Film' },
    { category: 'appearance', label: '紧凑模式', key: 'compact', icon: 'Crop' },
    // 通知设置
    { category: 'notification', label: '桌面通知', key: 'desktop', icon: 'Monitor' },
    { category: 'notification', label: '告警通知', key: 'alert', icon: 'Bell' },
    { category: 'notification', label: '声音通知', key: 'sound', icon: 'Mute' },
    { category: 'notification', label: '邮件通知', key: 'email', icon: 'Message' },
    // 安全设置
    { category: 'security', label: '会话超时', key: 'sessionTimeout', icon: 'Timer' },
    { category: 'security', label: '操作日志', key: 'logOperations', icon: 'Document' },
    { category: 'security', label: 'IP白名单', key: 'ipWhitelist', icon: 'Lock' },
    // 系统设置
    { category: 'system', label: '监控间隔', key: 'defaultInterval', icon: 'Odometer' },
    { category: 'system', label: '数据保留', key: 'dataRetention', icon: 'FolderOpened' },
    { category: 'system', label: 'CPU阈值', key: 'cpuThreshold', icon: 'Cpu' },
    { category: 'system', label: '内存阈值', key: 'memoryThreshold', icon: 'MemoryStick' }
  ]
  return items
})

// 过滤后的设置项
const filteredItems = computed(() => {
  if (!searchQuery.value) return []
  const query = searchQuery.value.toLowerCase()
  return settingItems.value.filter(item =>
    item.label.toLowerCase().includes(query) ||
    item.category.toLowerCase().includes(query)
  )
})

// 组件挂载时加载设置
onMounted(() => {
  loadSettings()
  setupWatchers()
})

// 加载设置
const loadSettings = () => {
  const settings = SettingUtils.loadSettings()
  Object.assign(generalSettings, settings.general)
  Object.assign(appearanceSettings, settings.appearance)
  Object.assign(notificationSettings, settings.notification)
  Object.assign(securitySettings, settings.security)
  Object.assign(systemSettings, settings.system)

  // 应用设置到UI - 但不应用布局相关的设置（避免影响侧边栏宽度）
  applySettings(false)
}

// 应用设置到UI
// applyLayout: 是否应用布局相关的设置（侧边栏宽度等），默认为 false
const applySettings = (applyLayout = false) => {
  // 应用主题色
  applyPrimaryColor()
  // 应用侧边栏宽度 - 仅在明确要求时才应用
  if (applyLayout) {
    applySidebarWidth()
  }
  // 应用动画
  applyAnimation()
  // 应用紧凑模式 - 仅在明确要求时才应用
  if (applyLayout) {
    applyCompact()
  }
}

// 应用主题色
const applyPrimaryColor = () => {
  document.documentElement.style.setProperty('--el-color-primary', appearanceSettings.primaryColor)
  window.dispatchEvent(new CustomEvent('settings-change', {
    detail: { type: 'primaryColor', value: appearanceSettings.primaryColor }
  }))
}

// 应用侧边栏宽度
const applySidebarWidth = () => {
  window.dispatchEvent(new CustomEvent('settings-change', {
    detail: { type: 'sidebarWidth', value: appearanceSettings.sidebarWidth }
  }))
}

// 应用动画
const applyAnimation = () => {
  document.documentElement.style.setProperty('--animation-enabled', appearanceSettings.animation ? '1' : '0')
  window.dispatchEvent(new CustomEvent('settings-change', {
    detail: { type: 'animation', value: appearanceSettings.animation }
  }))
}

// 应用紧凑模式
const applyCompact = () => {
  document.body.classList.toggle('compact-mode', appearanceSettings.compact)
  window.dispatchEvent(new CustomEvent('settings-change', {
    detail: { type: 'compact', value: appearanceSettings.compact }
  }))
}

// 设置监听器
const setupWatchers = () => {
  // 监听外观设置变化并实时应用
  // 对于布局相关的设置，使用 flush: 'post' 避免初始化时触发
  watch(() => appearanceSettings.primaryColor, () => applyPrimaryColor())

  watch(() => appearanceSettings.sidebarWidth, (newVal, oldVal) => {
    console.log('[Settings] 侧边栏宽度变化:', { from: oldVal, to: newVal })
    applySidebarWidth()
  }, { flush: 'post' })

  watch(() => appearanceSettings.animation, () => applyAnimation())

  watch(() => appearanceSettings.compact, (newVal, oldVal) => {
    console.log('[Settings] 紧凑模式变化:', { from: oldVal, to: newVal })
    applyCompact()
  }, { flush: 'post' })
}

// 切换标签页
const handleTabSelect = (index) => {
  activeTab.value = index
}

// 搜索处理
const handleSearch = () => {
  // 搜索结果通过computed自动更新
}

// 跳转到设置项
const jumpToSetting = (item) => {
  activeTab.value = item.category
  searchQuery.value = ''

  nextTick(() => {
    const element = document.getElementById(`setting-${item.key}`)
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'center' })
      element.classList.add('highlight')
      setTimeout(() => element.classList.remove('highlight'), 2000)
    }
  })
}

// 获取分类名称
const getCategoryName = (category) => {
  const names = {
    general: '通用设置',
    appearance: '外观设置',
    notification: '通知设置',
    security: '安全设置',
    system: '系统设置'
  }
  return names[category] || category
}

// 颜色切换
const handleColorChange = (color) => {
  appearanceSettings.primaryColor = color
  applyPrimaryColor()
  ElMessage.success('主题色已更新')
}

// 添加IP白名单
const addWhitelistIP = () => {
  const ip = newIP.value.trim()
  if (!ip) {
    ElMessage.warning('请输入IP地址')
    return
  }

  // 简单的IP格式验证
  const ipPattern = /^(\d{1,3}\.){3}\d{1,3}(\/\d{1,2})?$/
  if (!ipPattern.test(ip)) {
    ElMessage.warning('IP地址格式不正确')
    return
  }

  if (securitySettings.whitelistIPs.includes(ip)) {
    ElMessage.warning('该IP已存在')
    return
  }

  securitySettings.whitelistIPs.push(ip)
  newIP.value = ''
}

// 移除IP白名单
const removeWhitelistIP = (index) => {
  securitySettings.whitelistIPs.splice(index, 1)
}

// 处理操作命令
const handleActionCommand = (command) => {
  switch (command) {
    case 'export':
      handleExport()
      break
    case 'import':
      handleImport()
      break
    case 'reset':
      handleReset()
      break
    case 'resetAll':
      handleResetAll()
      break
  }
}

// 导出设置
const handleExport = () => {
  const settings = {
    general: { ...generalSettings },
    appearance: { ...appearanceSettings },
    notification: { ...notificationSettings },
    security: { ...securitySettings },
    system: { ...systemSettings }
  }

  try {
    SettingUtils.exportSettings(settings)
    ElMessage.success('设置已导出')
  } catch (error) {
    ElMessage.error('导出失败：' + error.message)
  }
}

// 导入设置
const handleImport = () => {
  importDialogVisible.value = true
}

// 文件变化
const handleFileChange = (file) => {
  importFile.value = file
}

// 确认导入
const confirmImport = () => {
  if (!importFile.value) {
    ElMessage.warning('请选择要导入的文件')
    return
  }

  importing.value = true

  SettingUtils.importSettings(importFile.value.raw)
    .then((data) => {
      // 应用导入的设置
      Object.assign(generalSettings, data.settings.general || {})
      Object.assign(appearanceSettings, data.settings.appearance || {})
      Object.assign(notificationSettings, data.settings.notification || {})
      Object.assign(securitySettings, data.settings.security || {})
      Object.assign(systemSettings, data.settings.system || {})

      // 应用设置到UI - 导入设置时应用所有设置包括布局
      applySettings(true)

      importDialogVisible.value = false
      importFile.value = null

      ElMessage.success('设置已导入')
    })
    .catch((error) => {
      ElMessage.error('导入失败：' + error.message)
    })
    .finally(() => {
      importing.value = false
    })
}

// 重置当前分类
const handleReset = () => {
  ElMessageBox.confirm(
    '确定要重置当前分类的设置为默认值吗？此操作不可恢复。',
    '重置设置',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    const defaults = SettingUtils.DEFAULT_SETTINGS[activeTab.value]
    Object.assign(getCurrentSettings(), defaults)

    // 应用设置到UI - 重置时应用所有设置包括布局
    applySettings(true)

    // 保存
    debouncedSave()

    ElMessage.success('设置已重置为默认值')
  }).catch(() => {
    // 用户取消
  })
}

// 重置所有设置
const handleResetAll = () => {
  ElMessageBox.confirm(
    '确定要重置所有设置为默认值吗？此操作不可恢复。',
    '重置所有设置',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    const defaults = SettingUtils.DEFAULT_SETTINGS
    Object.assign(generalSettings, defaults.general)
    Object.assign(appearanceSettings, defaults.appearance)
    Object.assign(notificationSettings, defaults.notification)
    Object.assign(securitySettings, defaults.security)
    Object.assign(systemSettings, defaults.system)

    // 应用设置到UI - 重置时应用所有设置包括布局
    applySettings(true)

    // 保存
    debouncedSave()

    ElMessage.success('所有设置已重置为默认值')
  }).catch(() => {
    // 用户取消
  })
}

// 获取当前设置对象
const getCurrentSettings = () => {
  switch (activeTab.value) {
    case 'general': return generalSettings
    case 'appearance': return appearanceSettings
    case 'notification': return notificationSettings
    case 'security': return securitySettings
    case 'system': return systemSettings
    default: return generalSettings
  }
}

// 防抖保存函数
let debouncedSave = () => {}

// 保存设置
const handleSave = () => {
  saving.value = true

  // 组合设置对象
  const settings = {
    general: { ...generalSettings },
    appearance: { ...appearanceSettings },
    notification: { ...notificationSettings },
    security: { ...securitySettings },
    system: { ...systemSettings }
  }

  // 验证设置
  const validation = SettingUtils.validateSettings(settings)
  if (!validation.valid) {
    ElMessage.error('设置验证失败：' + validation.errors.join('；'))
    saving.value = false
    return
  }

  // 保存到localStorage
  setTimeout(() => {
    const success = SettingUtils.saveSettings(settings)
    if (success) {
      ElMessage.success('设置已保存')
    } else {
      ElMessage.error('设置保存失败，请检查浏览器存储权限')
    }
    saving.value = false
  }, 300)
}

// 切换预览模式
const togglePreviewMode = () => {
  if (isPreviewMode.value) {
    exitPreviewMode(false)
  } else {
    enterPreviewMode()
  }
}

// 进入预览模式
const enterPreviewMode = () => {
  // 保存当前设置快照
  previewSnapshot.value = {
    general: { ...generalSettings },
    appearance: { ...appearanceSettings },
    notification: { ...notificationSettings },
    security: { ...securitySettings },
    system: { ...systemSettings }
  }

  isPreviewMode.value = true
  ElMessage.info('预览模式：更改将在退出预览后生效')
}

// 退出预览模式
const exitPreviewMode = (save) => {
  if (save) {
    // 应用预览中的更改
    applySettings(true)
    debouncedSave()
    ElMessage.success('预览更改已应用')
  } else {
    // 恢复预览前的设置
    Object.assign(generalSettings, previewSnapshot.value.general)
    Object.assign(appearanceSettings, previewSnapshot.value.appearance)
    Object.assign(notificationSettings, previewSnapshot.value.notification)
    Object.assign(securitySettings, previewSnapshot.value.security)
    Object.assign(systemSettings, previewSnapshot.value.system)

    // 恢复UI状态
    applySettings(true)
    ElMessage.info('已取消预览更改')
  }

  isPreviewMode.value = false
  previewSnapshot.value = null
}

// 创建防抖保存函数（简单实现）
const createDebounce = (func, wait) => {
  let timeout
  return function(...args) {
    clearTimeout(timeout)
    timeout = setTimeout(() => func.apply(this, args), wait)
  }
}

// 初始化防抖保存
debouncedSave = createDebounce(() => {
  const settings = {
    general: { ...generalSettings },
    appearance: { ...appearanceSettings },
    notification: { ...notificationSettings },
    security: { ...securitySettings },
    system: { ...systemSettings }
  }

  const validation = SettingUtils.validateSettings(settings)
  if (!validation.valid) {
    return
  }

  SettingUtils.saveSettings(settings)
}, 2000)
</script>

<style scoped lang="scss">
// ========================================
// DESIGN TOKENS - 设计变量
// ========================================
.settings-container {
  // 间距系统 - 8px基准网格
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;

  // 圆角系统
  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;

  // 阴影系统
  --shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 12px rgba(0, 0, 0, 0.08);
  --shadow-lg: 0 8px 24px rgba(0, 0, 0, 0.12);
  --shadow-xl: 0 16px 48px rgba(0, 0, 0, 0.15);

  // 色彩系统 - 使用全局设计系统变量
  --color-primary: #3B82F6;
  --color-primary-dark: #2563EB;
  --color-primary-light: #93C5FD;
  --color-success: #10B981;
  --color-warning: #F59E0B;
  --color-danger: #EF4444;

  // 背景和文字颜色 - 使用全局设计系统变量
  --bg-page: #F8FAFC;
  --bg-card: #FFFFFF;
  --bg-secondary: #F1F5F9;
  --border-color: #E2E8F0;
  --border-color-light: rgba(0, 0, 0, 0.06);
  --text-primary: #0F172A;
  --text-secondary: #475569;
  --text-tertiary: #94A3B8;

  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--spacing-lg);
  gap: var(--spacing-lg);
  background: var(--bg-page);
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  line-height: 1.5;

  .settings-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--spacing-lg);
    padding: var(--spacing-lg);
    background: var(--gradient-card);
    border: 1px solid var(--border-color-light);
    border-radius: var(--border-radius-lg);
    box-shadow: var(--shadow-sm);
    transition: var(--transition-base);

    &:hover {
      box-shadow: var(--shadow-md);
    }

    .search-input {
      max-width: 480px;
      flex: 1;

      :deep(.el-input__wrapper) {
        border-radius: var(--border-radius-md);
        box-shadow: none;
        border: 1px solid var(--border-color);
        transition: var(--transition-input);

        &:hover {
          border-color: var(--primary-400);
        }

        &.is-focus {
          border-color: var(--primary-500);
          box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
        }
      }

      .search-count {
        color: var(--text-secondary);
        font-size: var(--text-xs);
        font-weight: var(--font-weight-medium);
      }
    }

    .header-actions {
      display: flex;
      gap: var(--spacing-3);

      :deep(.el-button-group) {
        display: flex;
        gap: var(--spacing-2);

        .el-button {
          border-radius: var(--border-radius-md);
          font-weight: var(--font-weight-medium);
          padding: var(--spacing-2) var(--spacing-4);
          transition: var(--transition-button);
          border: 1px solid var(--border-color);
          min-height: 40px;
          background: var(--bg-card);

          &:hover:not(:disabled) {
            transform: translateY(-1px);
            box-shadow: var(--shadow-sm);
            border-color: var(--primary-400);
            color: var(--primary-600);
            background: var(--primary-50);
          }

          &:active:not(:disabled) {
            transform: translateY(0);
          }

          &.el-button--primary {
            background: var(--gradient-primary);
            border-color: transparent;
            color: white;

            &:hover:not(:disabled) {
              background: var(--gradient-primary-hover);
              box-shadow: var(--shadow-md), 0 4px 12px rgba(59, 130, 246, 0.3);
              border-color: transparent;
              color: white;
            }
          }

          &.el-button--warning {
            background: var(--gradient-warning);
            border-color: transparent;
            color: white;

            &:hover:not(:disabled) {
              box-shadow: var(--shadow-md);
              border-color: transparent;
              color: white;
            }
          }
        }
      }

      :deep(.el-dropdown) {
        .el-button {
          border-radius: var(--border-radius-md);
        }
      }
    }
  }

  .search-results {
    background: var(--bg-card);
    border-radius: var(--radius-xl);
    box-shadow: var(--shadow-lg);
    padding: var(--spacing-sm) 0;
    max-height: 320px;
    overflow-y: auto;
    z-index: 100;
    border: 1px solid var(--border-color);

    .search-result-item {
      display: flex;
      align-items: center;
      gap: var(--spacing-md);
      padding: var(--spacing-md) var(--spacing-lg);
      cursor: pointer;
      transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
      border: none;
      background: none;
      width: 100%;
      text-align: left;
      border-radius: var(--radius-lg);
      margin: 0 var(--spacing-sm);

      &:hover {
        background-color: var(--bg-secondary);
        transform: translateX(4px);
      }

      &:focus-visible {
        outline: 2px solid var(--color-primary);
        outline-offset: -2px;
        background-color: var(--bg-secondary);
      }

      &:active {
        background-color: #E2E8F0;
      }

      .item-info {
        flex: 1;

        .item-label {
          font-size: 15px;
          color: var(--text-primary);
          font-weight: 600;
          letter-spacing: -0.01em;
        }

        .item-category {
          font-size: 13px;
          color: var(--text-secondary);
          margin-top: 2px;
          font-weight: 500;
        }
      }
    }
  }

  .settings-layout {
    display: flex;
    gap: var(--spacing-lg);
    flex: 1;
    overflow: hidden;

    .settings-nav-card {
      width: 260px;
      height: 100%;
      overflow-y: auto;
      background: var(--bg-card);
      border-radius: var(--radius-xl);
      box-shadow: var(--shadow-md);
      border: 1px solid var(--border-color);
      transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);

      &.collapsed {
        width: 70px;

        .nav-header span {
          display: none;
        }
      }

      :deep(.el-card__body) {
        padding: 0;
      }

      .nav-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--spacing-lg);
        font-size: 15px;
        font-weight: 700;
        color: var(--text-primary);
        border-bottom: 2px solid var(--border-color);
        letter-spacing: -0.01em;

        .collapse-trigger {
          cursor: pointer;
          font-size: 18px;
          color: var(--text-secondary);
          transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
          width: 36px;
          height: 36px;
          display: flex;
          align-items: center;
          justify-content: center;
          border-radius: var(--radius-md);

          &:hover {
            color: var(--color-primary);
            background: var(--bg-secondary);
          }
        }
      }

      .settings-menu {
        border-right: none;
        padding: var(--spacing-md);

        .el-menu-item {
          height: 52px;
          line-height: 52px;
          border-radius: var(--radius-lg);
          margin-bottom: var(--spacing-xs);
          color: var(--text-secondary);
          font-weight: 500;
          transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);

          &:hover {
            background: var(--bg-secondary);
            color: var(--text-primary);
          }

          &.is-active {
            background: linear-gradient(135deg, rgba(59, 130, 246, 0.1) 0%, rgba(37, 99, 235, 0.1) 100%);
            color: var(--color-primary);
            font-weight: 600;
          }

          .el-icon {
            font-size: 20px;
          }
        }
      }
    }

    .settings-content-card {
      flex: 1;
      overflow-y: auto;
      background: var(--bg-card);
      border-radius: var(--radius-xl);
      box-shadow: var(--shadow-md);
      border: 1px solid var(--border-color);

      :deep(.el-card__body) {
        padding: 0;
        height: 100%;
      }

      .settings-panel {
        padding: var(--spacing-xl);
        max-width: 1000px;
        margin: 0 auto;

        .panel-header {
          margin-bottom: var(--spacing-xl);
          padding-bottom: var(--spacing-lg);
          border-bottom: 2px solid var(--border-color);

          h3 {
            font-size: 28px;
            font-weight: 700;
            color: var(--text-primary);
            margin-bottom: var(--spacing-sm);
            letter-spacing: -0.02em;
          }

          p {
            font-size: 15px;
            color: var(--text-secondary);
            margin: 0;
            font-weight: 500;
          }
        }

        .setting-group {
          margin-bottom: 40px;

          h4 {
            font-size: 18px;
            font-weight: 700;
            color: var(--text-primary);
            margin-bottom: var(--spacing-lg);
            padding-bottom: var(--spacing-sm);
            border-bottom: 2px solid var(--border-color);
            letter-spacing: -0.01em;
          }
        }

        .settings-form {
          :deep(.el-form-item) {
            margin-bottom: var(--spacing-lg);

            .el-form-item__label {
              color: var(--text-primary);
              font-weight: 600;
              font-size: 14px;
            }
          }

          .form-item-desc {
            margin-left: var(--spacing-md);
            font-size: 13px;
            color: var(--text-secondary);
            font-weight: 500;
          }

          .ip-list {
            display: flex;
            flex-wrap: wrap;
            gap: var(--spacing-sm);
            align-items: center;
          }
        }

        .color-picker-group {
          display: flex;
          gap: var(--spacing-md);
          flex-wrap: wrap;

          .color-option {
            width: 48px;
            height: 48px;
            min-width: 48px;
            min-height: 48px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            border: 3px solid transparent;
            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
            background: none;
            padding: 0;
            position: relative;

            &:hover {
              transform: scale(1.15);
              box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
            }

            &:focus-visible {
              outline: 2px solid var(--color-primary);
              outline-offset: 2px;
            }

            &:active {
              transform: scale(1.05);
            }

            &.active {
              border-color: #fff;
              box-shadow: 0 0 0 4px var(--color-primary);
            }

            .el-icon {
              font-size: 22px;
              color: #fff;
              pointer-events: none;
            }
          }
        }

        .highlight {
          animation: highlight 2s ease-in-out;
        }
      }
    }
  }

  .settings-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--spacing-md);
    padding: var(--spacing-lg) var(--spacing-xl);
    background: var(--bg-card);
    border-radius: var(--radius-xl);
    border-top: 1px solid var(--border-color);
    box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.05);

    &.preview-mode {
      background: linear-gradient(135deg, rgba(59, 130, 246, 0.05) 0%, rgba(37, 99, 235, 0.05) 100%);
      border-top: 2px solid var(--color-primary);
    }

    .preview-info {
      display: flex;
      align-items: center;
      gap: var(--spacing-sm);
      color: var(--color-primary);
      font-weight: 600;
      font-size: 14px;
    }

    .footer-actions {
      display: flex;
      gap: var(--spacing-sm);

      :deep(.el-button) {
        border-radius: var(--radius-lg);
        font-weight: 600;
        padding: 12px 24px;
        transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
        border: 2px solid var(--border-color);
        min-height: 44px;

        &:hover:not(:disabled) {
          transform: translateY(-2px);
          box-shadow: var(--shadow-md);
        }

        &.el-button--primary {
          background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-primary) 100%);
          border: none;
          box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);

          &:hover:not(:disabled) {
            box-shadow: 0 6px 20px rgba(37, 99, 235, 0.4);
          }

          &:disabled {
            opacity: 0.6;
            cursor: not-allowed;
          }
        }

        &.el-button--success {
          background: linear-gradient(135deg, var(--color-success) 0%, #34D399 100%);
          border: none;
          box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);

          &:hover:not(:disabled) {
            box-shadow: 0 6px 20px rgba(16, 185, 129, 0.4);
          }
        }

        &.el-button--default {
          background: #ffffff;
          color: var(--text-primary);

          &:hover:not(:disabled) {
            background: var(--bg-secondary);
            border-color: var(--color-primary);
          }
        }

        .el-icon {
          margin-right: 6px;
        }
      }
    }
  }
}

@keyframes highlight {
  0%, 100% {
    background-color: transparent;
    box-shadow: none;
  }
  50% {
    background-color: rgba(59, 130, 246, 0.08);
    box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
  }
}

// 响应式设计
@media (max-width: 1024px) {
  .settings-container {
    .settings-layout {
      .settings-nav-card {
        width: 220px;
      }
    }
  }
}

@media (max-width: 768px) {
  .settings-container {
    padding: var(--spacing-md);

    .settings-header {
      flex-direction: column;
      align-items: stretch;
      gap: var(--spacing-md);
      padding: var(--spacing-md);

      .search-input {
        max-width: none;
      }

      .header-actions {
        :deep(.el-button-group) {
          display: flex;
          width: 100%;

          .el-button {
            flex: 1;
          }
        }
      }
    }

    .settings-layout {
      flex-direction: column;
      gap: var(--spacing-md);

      .settings-nav-card {
        width: 100%;
        max-height: 240px;
        overflow-y: auto;

        &.collapsed {
          width: 100%;
          max-height: 70px;
        }

        .nav-header {
          padding: var(--spacing-md);
        }

        .settings-menu {
          padding: var(--spacing-sm);

          .el-menu-item {
            height: 48px;
            line-height: 48px;
          }
        }
      }

      .settings-content-card {
        .settings-panel {
          padding: var(--spacing-lg);
          max-width: 100%;

          .panel-header {
            margin-bottom: var(--spacing-lg);
            padding-bottom: var(--spacing-md);

            h3 {
              font-size: 24px;
            }

            p {
              font-size: 14px;
            }
          }

          .setting-group {
            margin-bottom: var(--spacing-lg);

            h4 {
              font-size: 16px;
              margin-bottom: var(--spacing-md);
            }
          }

          .settings-form {
            .el-form-item {
              margin-bottom: var(--spacing-md);

              :deep(.el-form-item__content) {
                flex-wrap: wrap;
              }

              :deep(.el-select),
              :deep(.el-input-number) {
                width: 100% !important;
              }
            }
          }

          .color-picker-group {
            gap: var(--spacing-sm);

            .color-option {
              width: 44px;
              height: 44px;
              min-width: 44px;
              min-height: 44px;
            }
          }
        }
      }
    }

    .settings-footer {
      flex-direction: column;
      gap: var(--spacing-md);
      padding: var(--spacing-md);

      .footer-actions {
        flex-direction: column;
        width: 100%;

        .el-button {
          width: 100%;
        }
      }
    }
  }
}

@media (max-width: 480px) {
  .settings-container {
    padding: var(--spacing-sm);

    .settings-header {
      padding: var(--spacing-sm);
    }

    .settings-content-card .settings-panel {
      padding: var(--spacing-md);

      .panel-header {
        h3 {
          font-size: 20px;
        }
      }

      .settings-form {
        .el-form-item {
          :deep(.el-form-item__label) {
            width: 100% !important;
            text-align: left;
            margin-bottom: var(--spacing-xs);
          }
        }
      }
    }

    .settings-footer {
      padding: var(--spacing-sm);
    }
  }
}

// 减少动画偏好支持
@media (prefers-reduced-motion: reduce) {
  .settings-container {
    * {
      animation-duration: 0.01ms !important;
      animation-iteration-count: 1 !important;
      transition-duration: 0.01ms !important;
    }
  }
}

// 高对比度模式
@media (prefers-contrast: high) {
  .settings-container {
    .settings-nav-card,
    .settings-content-card,
    .search-results {
      border-width: 2px;
    }

    .color-option {
      border-width: 2px;
    }
  }
}

// 打印样式
@media print {
  .settings-container {
    .settings-header,
    .settings-nav-card,
    .settings-footer {
      display: none !important;
    }

    .settings-content-card {
      box-shadow: none;
      border: 1px solid #ddd;
    }
  }
}

// ===================================
// 全局按钮样式优化
// ===================================
:deep(.el-dialog) {
  .el-button {
    border-radius: var(--radius-lg);
    font-weight: 600;
    padding: 10px 20px;
    transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    border: 2px solid var(--border-color);
    min-height: 40px;

    &:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: var(--shadow-md);
    }

    &.el-button--primary {
      background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-primary) 100%);
      border: none;
      box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);

      &:hover:not(:disabled) {
        box-shadow: 0 6px 20px rgba(37, 99, 235, 0.4);
      }
    }

    &.el-button--default {
      background: #ffffff;
      color: var(--text-primary);

      &:hover:not(:disabled) {
        background: var(--bg-secondary);
        border-color: var(--color-primary);
      }
    }

    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  }
}

// 表单控件样式优化
:deep(.el-select),
:deep(.el-input-number),
:deep(.el-switch) {
  .el-input__wrapper {
    border-radius: var(--radius-md);
    border: 2px solid var(--border-color);
    transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: none;

    &:hover {
      border-color: var(--color-primary-light);
    }

    &.is-focus {
      border-color: var(--color-primary);
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
    }
  }
}

// 上传组件样式优化
:deep(.el-upload-dragger) {
  border-radius: var(--radius-lg);
  border: 2px dashed var(--border-color);
  background: var(--bg-secondary);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  padding: var(--spacing-xl);

  &:hover {
    border-color: var(--color-primary);
    background: rgba(59, 130, 246, 0.05);
  }

  .el-icon--upload {
    font-size: 48px;
    color: var(--color-primary);
    margin-bottom: var(--spacing-md);
  }

  .el-upload__text {
    color: var(--text-primary);
    font-size: 15px;
    font-weight: 500;

    em {
      color: var(--color-primary);
      font-style: normal;
      font-weight: 600;
    }
  }

  .el-upload__tip {
    color: var(--text-secondary);
    font-size: 13px;
    margin-top: var(--spacing-md);
  }
}

// 开关样式优化 - 使用绿色渐变
:deep(.el-switch) {
  .el-switch__core {
    border-radius: 12px;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    background: #E2E8F0 !important;
    border: 2px solid #CBD5E1 !important;
    background-image: none !important;
  }

  &.is-checked .el-switch__core {
    background: #ffffff !important;
    border-color: var(--color-primary) !important;
    box-shadow: 0 0 0 1px var(--color-primary) !important;
    background-image: none !important;
  }

  .el-switch__action {
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  }

  &:hover:not(.is-disabled) {
    .el-switch__core {
      border-color: var(--color-primary-light);
    }
  }
}

// 单选框组样式优化 - 移除蓝色背景（使用!important强制覆盖）
:deep(.el-radio-group) {
  .el-radio {
    margin-right: var(--spacing-md);
    padding: var(--spacing-sm) var(--spacing-md);
    border-radius: var(--radius-md);
    border: 2px solid var(--border-color) !important;
    transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    cursor: pointer;
    background: #ffffff !important;

    &:hover {
      border-color: var(--color-primary-light) !important;
      background: var(--bg-secondary) !important;
    }

    &.is-checked {
      border-color: var(--color-primary) !important;
      background: #ffffff !important;
      box-shadow: 0 0 0 1px var(--color-primary) !important;

      .el-radio__label {
        color: var(--color-primary) !important;
        font-weight: 600;
      }

      .el-radio__inner {
        background: var(--color-primary) !important;
        border-color: var(--color-primary) !important;
      }
    }

    .el-radio__input.is-checked + .el-radio__label {
      color: var(--color-primary) !important;
      font-weight: 600;
    }
  }
}

// 复选框样式优化 - 移除蓝色背景（使用!important强制覆盖）
:deep(.el-checkbox-group) {
  .el-checkbox {
    margin-right: var(--spacing-md);
    padding: var(--spacing-sm) var(--spacing-md);
    border-radius: var(--radius-md);
    border: 2px solid var(--border-color) !important;
    transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    cursor: pointer;
    background: #ffffff !important;

    &:hover {
      border-color: var(--color-primary-light) !important;
      background: var(--bg-secondary) !important;
    }

    &.is-checked {
      border-color: var(--color-primary) !important;
      background: #ffffff !important;
      box-shadow: 0 0 0 1px var(--color-primary) !important;

      .el-checkbox__label {
        color: var(--color-primary) !important;
        font-weight: 600;
      }

      .el-checkbox__inner {
        background: var(--color-primary) !important;
        border-color: var(--color-primary) !important;
      }
    }

    .el-checkbox__input.is-checked + .el-checkbox__label {
      color: var(--color-primary) !important;
      font-weight: 600;
    }
  }
}

// 颜色选择器按钮优化 - 移除过强的阴影
:deep(.color-picker-group) {
  .color-option {
    &:hover {
      transform: translateY(-2px) scale(1.05);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }

    &.active {
      border-color: #ffffff;
      box-shadow: 0 0 0 3px var(--color-primary), 0 2px 8px rgba(0, 0, 0, 0.1);
    }
  }
}

// IP标签样式优化
:deep(.el-tag) {
  background: var(--bg-secondary);
  border-color: var(--border-color);
  color: var(--text-primary);
  font-weight: 500;
  border-radius: var(--radius-md);
  padding: 4px 12px;

  &.el-tag--success {
    background: rgba(16, 185, 129, 0.1);
    border-color: rgba(16, 185, 129, 0.3);
    color: var(--color-success);
  }

  &.el-tag--info {
    background: rgba(59, 130, 246, 0.1);
    border-color: rgba(59, 130, 246, 0.3);
    color: var(--color-primary);
  }

  &.el-tag--warning {
    background: rgba(245, 158, 11, 0.1);
    border-color: rgba(245, 158, 11, 0.3);
    color: var(--color-warning);
  }

  &.el-tag--danger {
    background: rgba(239, 68, 68, 0.1);
    border-color: rgba(239, 68, 68, 0.3);
    color: var(--color-danger);
  }
}

// 滑块样式优化
:deep(.el-slider) {
  .el-slider__runway {
    background: var(--border-color);
    height: 6px;
  }

  .el-slider__bar {
    background: linear-gradient(90deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
    height: 6px;
  }

  .el-slider__button {
    border: 2px solid var(--color-primary);
    background: #ffffff;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);

    &:hover {
      transform: scale(1.1);
      box-shadow: 0 4px 12px rgba(59, 130, 246, 0.25);
    }
  }
}
</style>

<style lang="scss">
// 全局样式 - 高亮动画
@keyframes highlight {
  0%, 100% {
    background-color: transparent;
  }
  50% {
    background-color: #ecf5ff;
  }
}

/* Settings页面内部菜单样式 - 覆盖App.vue的全局样式 */
.settings-menu {
  background-color: transparent !important;
  border: none !important;

  .el-menu-item {
    background-color: transparent !important;

    &.is-active {
      background-color: #ffffff !important;
      color: var(--color-primary) !important;
      font-weight: 600;
      border-right: 3px solid var(--color-primary);
      box-shadow: none !important;

      // 移除App.vue中添加的左侧指示器
      &::before {
        display: none !important;
      }
    }

    &:hover {
      background-color: var(--bg-secondary) !important;
    }
  }
}

// 强制覆盖菜单项的背景（移除Element Plus的蓝色渐变背景）
:deep(.settings-menu .el-menu-item.is-active) {
  background: #ffffff !important;
  background-image: none !important;
}

:deep(.settings-menu .el-menu-item) {
  background: transparent !important;
  background-image: none !important;
}

// ===================================
// UI/UX 优化 (v2.0)
// ===================================

// 触摸目标优化
.touch-target {
  min-width: 44px;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

// 折叠触发按钮优化
.collapse-trigger {
  background: none;
  border: none;
  cursor: pointer;
  padding: 8px;
  border-radius: 4px;
  transition: all 0.2s ease;

  &:hover {
    background-color: rgba(64, 158, 255, 0.1);
  }

  &:focus-visible {
    outline: 2px solid var(--primary-500, #409eff);
    outline-offset: 2px;
  }
}

// 表单标签优化
.form-label {
  font-weight: 500;
  color: var(--text-secondary);
}

// 设置项高亮动画增强
@keyframes highlight {
  0%, 100% {
    background-color: transparent;
    box-shadow: none;
  }
  50% {
    background-color: rgba(64, 158, 255, 0.1);
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
  }
}

.highlight {
  animation: highlight 2s ease-in-out;
  scroll-margin-top: 20px;
}

// 全局表单控件样式优化 - 移除Element Plus默认的蓝色背景（强制覆盖）
.el-radio,
.el-checkbox {
  background: #ffffff !important;

  &.is-checked {
    background: #ffffff !important;
  }

  &:hover {
    background: var(--bg-secondary) !important;
  }
}

// 单选框内部圆点样式覆盖
:deep(.el-radio__input.is-checked .el-radio__inner) {
  background: var(--color-primary) !important;
  border-color: var(--color-primary) !important;
}

// 复选框内部样式覆盖
:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background: var(--color-primary) !important;
  border-color: var(--color-primary) !important;
}

// 开关样式覆盖（绿色）- 使用最大特异性强制覆盖
:deep(.el-switch.is-checked) {
  background: transparent !important;
  border-color: transparent !important;
}

:deep(.el-switch .el-switch__core) {
  background: #E2E8F0 !important;
  border: 2px solid #CBD5E1 !important;
  background-image: none !important;
}

:deep(.el-switch.is-checked .el-switch__core) {
  background: #ffffff !important;
  background-image: none !important;
  border-color: var(--color-primary) !important;
  box-shadow: 0 0 0 1px var(--color-primary) !important;
}

// 响应式优化增强
@media (max-width: 768px) {
  .settings-container {
    padding: 10px;
  }

  .settings-header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;

    .search-input {
      max-width: none;
    }

    .header-actions {
      justify-content: stretch;

      .el-button-group {
        display: flex;
        width: 100%;

        .el-button {
          flex: 1;
        }
      }
    }
  }

  .settings-layout {
    flex-direction: column;
    gap: 12px;
  }

  .settings-nav-card {
    width: 100%;
    max-height: 200px;
    overflow-y: auto;

    &.collapsed {
      max-height: 60px;
    }

    .nav-header {
      padding: 12px;
    }
  }

  .settings-content-card {
    .settings-panel {
      padding: 16px;

      .panel-header {
        margin-bottom: 24px;
        padding-bottom: 12px;

        h3 {
          font-size: 20px;
        }
      }

      .settings-form {
        .el-form-item {
          margin-bottom: 20px;

          :deep(.el-form-item__content) {
            flex-wrap: wrap;
          }

          :deep(.el-select),
          :deep(.el-input-number) {
            width: 100% !important;
          }

          :deep(.el-radio-group) {
            display: flex;
            flex-direction: column;
            gap: 8px;

            .el-radio {
              margin: 0;
            }
          }
        }
      }

      .color-picker-group {
        gap: 8px;

        .color-option {
          width: 40px;
          height: 40px;
          min-width: 40px;
          min-height: 40px;
        }
      }
    }
  }

  .settings-footer {
    flex-direction: column;
    gap: 12px;
    padding: 12px 16px;

    .footer-actions {
      flex-direction: column;
      width: 100%;

      .el-button {
        width: 100%;
      }
    }
  }
}

// 超小屏幕优化
@media (max-width: 480px) {
  .settings-container {
    padding: 8px;
  }

  .settings-content-card .settings-panel {
    padding: 12px;

    .panel-header h3 {
      font-size: 18px;
    }

    .settings-form {
      .el-form-item {
        :deep(.el-form-item__label) {
          width: 100% !important;
          text-align: left;
          margin-bottom: 8px;
        }
      }
    }
  }
}

// 减少动画偏好支持
@media (prefers-reduced-motion: reduce) {
  .highlight {
    animation: none !important;
  }

  .color-option,
  .search-result-item,
  .collapse-trigger {
    transition: none !important;
  }
}

// 高对比度模式
@media (prefers-contrast: high) {
  .settings-nav-card,
  .settings-content-card {
    border-width: 2px;
  }

  .color-option {
    border-width: 2px;
  }

  .setting-group h4 {
    text-decoration: underline;
  }
}

// 打印样式
@media print {
  .settings-header,
  .settings-nav-card,
  .settings-footer {
    display: none !important;
  }

  .settings-content-card {
    box-shadow: none;
    border: 1px solid #ddd;
  }
}

// 焦点可见性增强
:deep(.el-select:focus-visible),
:deep(.el-input:focus-visible),
:deep(.el-switch:focus-visible) {
  outline: 2px solid var(--primary-500, #409eff);
  outline-offset: 2px;
}

// ===================================
// 强制覆盖Element Plus主题的开关背景色
// ===================================
// 使用最大特异性确保覆盖theme-element-plus.css中的--gradient-primary
.settings-container .settings-content-card .settings-panel .settings-form
.el-form-item
.el-switch.is-checked
.el-switch__core {
  background: linear-gradient(135deg, #10B981 0%, #34D399 100%) !important;
  background-image: linear-gradient(135deg, #10B981 0%, #34D399 100%) !important;
  border-color: transparent !important;
}

.settings-container .settings-content-card .settings-panel .settings-form
.el-form-item
.el-switch
.el-switch__core {
  background: #E2E8F0 !important;
  background-image: none !important;
  border: 2px solid #CBD5E1 !important;
}
</style>
