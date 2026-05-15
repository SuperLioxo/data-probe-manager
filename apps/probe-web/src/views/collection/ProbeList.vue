<template>
  <div class="probe-list-container">
    <!-- 页面标题和操作 -->
    <div class="page-header">
      <div class="page-title">
        <el-icon><Monitor /></el-icon>
        <span>探针管理</span>
      </div>
      <div class="page-actions">
        <!-- 主要操作按钮 -->
        <el-button v-if="hasPermission('probe:create')" type="primary" @click="handleQuickCommand('quick')" class="touch-target">
          <el-icon><Plus /></el-icon>
          新建探针
        </el-button>
        <el-button v-if="hasPermission('probe:create')" type="success" @click="handleQuickCommand('import')" class="touch-target">
          <el-icon><Upload /></el-icon>
          JSON导入
        </el-button>

        <!-- 分隔线 -->
        <el-divider direction="vertical" style="margin: 0 12px; height: 32px;" />

        <!-- 导出按钮组 -->
        <el-button-group>
          <el-button @click="handleExportJson" :loading="exporting" class="touch-target">
            <el-icon><DocumentCopy /></el-icon>
            导出JSON
          </el-button>
          <el-button type="success" @click="handleExport" :loading="exporting" class="touch-target">
            <el-icon><Download /></el-icon>
            导出Excel
          </el-button>
        </el-button-group>
      </div>
    </div>

    <!-- 查询表单 -->
    <el-card class="search-card" shadow="hover">
      <el-form :inline="true" :model="queryForm" class="search-form" autocomplete="off">
        <el-form-item label="探针名称">
          <el-input
            v-model="queryForm.name"
            placeholder="请输入探针名称"
            clearable
            prefix-icon="Search"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="探针状态">
          <el-select v-model="queryForm.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="在线" value="online" />
            <el-option label="离线" value="offline" />
            <el-option label="异常" value="error" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><RefreshLeft /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 探针列表表格 -->
    <el-card class="table-card" shadow="hover">
      <el-table
        :data="tableData"
        style="width: 100%"
        v-loading="loading"
        border
        stripe
        :row-class-name="tableRowClassName"
        @sort-change="handleSortChange"
        @row-click="handleView"
        @cell-click="handleCellClick"
      >
        <el-table-column type="index" label="序号" width="80" align="center" />
        <el-table-column prop="name" label="探针名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="probe-name">
              <el-icon class="type-icon" :class="getTypeClass(row.type)">
                <Monitor v-if="row.type === 'SYSTEM'" />
                <Folder v-else-if="row.type === 'FILE'" />
                <Setting v-else />
              </el-icon>
              <span class="name-text">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="probeKey" label="探针标识" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" type="info" class="probe-key-tag">{{ row.probeKey }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="探针类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="getTypeColor(row.type)" size="small">
              {{ getTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag
              :type="getStatusType(row.status)"
              effect="light"
              size="small"
            >
              <el-icon
                class="status-icon"
                :class="getStatusIconClass(row.status)"
              >
                <SuccessFilled v-if="row.status === 'online'" />
                <CircleClose v-else-if="row.status === 'offline'" />
                <WarningFilled v-else />
              </el-icon>
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastHeartbeat" label="最后心跳时间" width="180" align="center">
          <template #default="{ row }">
            <span v-if="row.lastHeartbeat">{{ formatDate(row.lastHeartbeat) }}</span>
            <span v-else style="color: #c0c4cc;">暂无</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="180" align="center" class-name="action-column">
          <template #default="scope">
            <div class="action-buttons-compact" @click.stop>
              <!-- 编辑 -->
              <el-tooltip content="编辑" placement="top" :show-after="500">
                <el-button size="small" @click="handleEdit(scope.row)" link>
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>

              <!-- 控制下拉 -->
              <el-dropdown @command="(cmd) => handleControl(scope.row, cmd)" trigger="click">
                <el-button
                  size="small"
                  :type="scope.row.status === 'offline' ? 'success' : 'primary'"
                  :loading="controlLoading[scope.row.probeKey]"
                  link
                  title="控制"
                >
                  <el-icon><VideoPlay /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="start" :disabled="scope.row.status === 'online'">
                      <el-icon><VideoPlay /></el-icon>
                      <span>启动探针</span>
                    </el-dropdown-item>
                    <el-dropdown-item command="stop" :disabled="scope.row.status === 'offline'">
                      <el-icon><VideoPause /></el-icon>
                      停止探针
                    </el-dropdown-item>
                    <el-dropdown-item command="restart" :disabled="scope.row.status === 'offline'">
                      <el-icon><RefreshRight /></el-icon>
                      重启探针
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>

              <!-- 删除 -->
              <el-tooltip v-if="hasPermission('probe:delete')" content="删除" placement="top" :show-after="500">
                <el-button size="small" @click="handleDelete(scope.row)" link type="danger">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 - 仅在数据超过一页时显示 -->
      <div class="pagination-container" v-show="pagination.total > pagination.pageSize">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          background
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="探针详情"
      :width="currentProbe && currentProbe.type === 'FILE' ? '1200px' : '1000px'"
      destroy-on-close
      class="probe-detail-dialog"
      :scrollbar="false"
      v-if="currentProbe"
    >
      <!-- 离线提示横幅 -->
      <el-alert
        v-if="currentProbe.status && currentProbe.status.toLowerCase() !== 'online'"
        :title="`探针已${getStatusText(currentProbe.status)}，部分功能不可用`"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 20px"
      />

      <el-tabs v-if="currentProbe.id" type="border-card" class="probe-detail-tabs">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息">
          <el-descriptions :column="2" border class="probe-basic-info">
            <el-descriptions-item label="探针名称">{{ currentProbe.name }}</el-descriptions-item>
            <el-descriptions-item label="探针标识">{{ currentProbe.probeKey }}</el-descriptions-item>
            <el-descriptions-item label="探针类型">
              <el-tag :type="getTypeColor(currentProbe.type)" size="small">
                {{ getTypeLabel(currentProbe.type) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(currentProbe.status)" effect="light" size="small">
                {{ getStatusText(currentProbe.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="主机IP">{{ currentProbe.hostIp }}</el-descriptions-item>
            <el-descriptions-item label="端口">{{ currentProbe.port }}</el-descriptions-item>

            <!-- 文件探针专属信息 -->
            <template v-if="currentProbe.type === 'FILE'">
              <el-descriptions-item label="扫描路径" :span="2">{{ formatJSONArray(currentProbe.scanPath) }}</el-descriptions-item>
              <el-descriptions-item label="文件扩展名">{{ currentProbe.fileExtensions || '全部' }}</el-descriptions-item>
              <el-descriptions-item label="扫描间隔">{{ currentProbe.scanInterval }}秒</el-descriptions-item>
              <el-descriptions-item label="忽略路径" :span="2">{{ formatJSONArray(currentProbe.ignorePaths) }}</el-descriptions-item>
              <el-descriptions-item label="最大深度">{{ currentProbe.maxDepth }}</el-descriptions-item>
              <el-descriptions-item label="文件数量">{{ currentProbe.totalFileCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="目录数量">{{ currentProbe.totalDirectoryCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="总大小">{{ formatSize(currentProbe.totalSize) }}</el-descriptions-item>
              <el-descriptions-item label="最后扫描">{{ formatTime(currentProbe.lastScanTime) }}</el-descriptions-item>
              <el-descriptions-item label="创建时间" :span="2">{{ formatTime(currentProbe.createTime) }}</el-descriptions-item>
            </template>

            <!-- 系统探针专属信息 -->
            <template v-else>
              <!-- 数据库探针：数据库类型 -->
              <el-descriptions-item v-if="currentProbe.type === 'DATABASE'" label="数据库类型">
                <el-tag :type="getDatabaseTypeColor(currentProbe.config)" size="small">
                  {{ getDatabaseTypeName(currentProbe.config) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-if="currentProbe.type === 'DATABASE'" label="采集间隔">
                {{ currentProbe.collectInterval }}秒
              </el-descriptions-item>
              <el-descriptions-item v-if="currentProbe.type !== 'DATABASE'" label="采集间隔">
                {{ currentProbe.collectInterval }}秒
              </el-descriptions-item>
              <el-descriptions-item label="最后心跳">{{ formatTime(currentProbe.lastHeartbeat) }}</el-descriptions-item>
              <el-descriptions-item label="创建时间" :span="2">{{ formatTime(currentProbe.createTime) }}</el-descriptions-item>
            </template>
          </el-descriptions>
        </el-tab-pane>

        <!-- 数据监控（根据探针类型显示不同内容） -->
        <el-tab-pane>
          <template #label>
            <span v-if="currentProbe.type === 'SYSTEM'">系统资源</span>
            <span v-else-if="currentProbe.type === 'DATABASE'">数据库详情</span>
            <span v-else-if="currentProbe.type === 'FILE'">文件浏览</span>
          </template>

          <!-- ========== FILE类型：文件浏览界面 ========== -->
          <div v-if="currentProbe.type === 'FILE'" class="file-browser-wrapper">
            <!-- 操作栏 -->
            <div class="browser-action-bar">
              <div class="action-title">
                <el-icon><FolderOpened /></el-icon>
                <span class="probe-name">{{ currentProbe.name }}</span>
              </div>
              <div class="action-buttons">
                <el-button
                  type="success"
                  :icon="Upload"
                  @click="showFileUploadDialog"
                  class="upload-btn"
                >
                  上传文件
                </el-button>
                <el-button
                  type="primary"
                  :icon="Refresh"
                  :loading="fileScanning"
                  :disabled="!isProbeOnline(currentProbe)"
                  @click="handleFileScan"
                  class="scan-btn"
                >
                  刷新扫描
                </el-button>
              </div>
            </div>

            <!-- 统计信息 -->
            <el-card class="file-stats-card" shadow="hover" v-if="fileStatistics">
              <template #header>
                <span>文件统计</span>
              </template>
              <el-row :gutter="16">
                <el-col :span="6">
                  <div class="stat-item">
                    <div class="stat-label">文件数</div>
                    <div class="stat-value">{{ fileStatistics.totalFileCount || 0 }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="stat-item">
                    <div class="stat-label">目录数</div>
                    <div class="stat-value">{{ fileStatistics.totalDirectoryCount || 0 }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="stat-item">
                    <div class="stat-label">总大小</div>
                    <div class="stat-value">{{ formatSize(fileStatistics.totalSize || 0) }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="stat-item">
                    <div class="stat-label">最后扫描</div>
                    <div class="stat-value">{{ formatTime(fileStatistics.lastScanTime) }}</div>
                  </div>
                </el-col>
              </el-row>
            </el-card>

            <!-- 文件列表 -->
            <el-card class="file-list-card" shadow="hover" v-loading="fileLoading">
              <template #header>
                <div class="file-list-header">
                  <span>文件列表</span>
                  <div class="view-switcher">
                    <el-radio-group v-model="fileViewMode" size="small">
                      <el-radio-button value="table">
                        <el-icon><List /></el-icon>
                        表格
                      </el-radio-button>
                      <el-radio-button value="tree">
                        <el-icon><Operation /></el-icon>
                        树形
                      </el-radio-button>
                    </el-radio-group>
                    <el-divider direction="vertical" />
                    <el-button
                      size="small"
                      :icon="Refresh"
                      @click="fetchFileList"
                      :loading="fileLoading"
                    >
                      刷新
                    </el-button>
                  </div>
                </div>
              </template>

              <!-- 树形视图 -->
              <div v-if="fileViewMode === 'tree'" class="file-tree-container">
                <el-tree
                  ref="fileTreeRef"
                  :data="fileTreeData"
                  :props="treeProps"
                  :load="loadTreeNode"
                  lazy
                  node-key="filePath"
                  :expand-on-click-node="false"
                  :highlight-current="true"
                  :default-expand-all="false"
                  :indent="20"
                  :auto-expand-parent="true"
                  @node-click="handleTreeNodeClick"
                >
                  <template #default="{ node, data }">
                    <div class="custom-tree-node">
                      <!-- 文件/目录图标 -->
                      <el-icon :size="18" class="node-icon">
                        <Folder v-if="data.fileType === 'DIRECTORY'" />
                        <Document v-else />
                      </el-icon>

                      <!-- 节点名称 -->
                      <span class="node-name" :title="data.fileName">
                        {{ data.fileName }}
                      </span>

                      <!-- 文件大小 -->
                      <span v-if="data.fileType === 'FILE'" class="node-size">
                        {{ formatSize(data.fileSize) }}
                      </span>

                      <!-- 完整路径（可选显示） -->
                      <span v-if="showFullPath && data.filePath" class="node-path">
                        {{ data.filePath }}
                      </span>

                      <!-- 操作按钮 -->
                      <span class="node-actions">
                        <el-button
                          v-if="data.fileType === 'FILE'"
                          size="small"
                          type="primary"
                          link
                          @click.stop="handleDownloadFile(data)"
                        >
                          <el-icon><Download /></el-icon>
                          下载
                        </el-button>
                        <el-button
                          size="small"
                          type="info"
                          link
                          @click.stop="handleShowFileDetails(data)"
                        >
                          <el-icon><InfoFilled /></el-icon>
                          详情
                        </el-button>
                      </span>
                    </div>
                  </template>
                </el-tree>

                <!-- 空状态提示 -->
                <el-empty
                  v-if="!fileTreeData || fileTreeData.length === 0"
                  description="暂无文件数据"
                  :image-size="120"
                />
              </div>

              <!-- 表格视图（原有代码） -->
              <el-table
                v-else
                :data="fileList"
                style="width: 100%"
                border
                stripe
                max-height="500"
              >
                <el-table-column type="index" label="序号" width="60" align="center" />
                <el-table-column prop="fileName" label="名称" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div class="file-name-cell">
                      <el-icon class="file-icon">
                        <Folder v-if="row.fileType === 'DIRECTORY'" />
                        <Document v-else />
                      </el-icon>
                      <span>{{ row.fileName }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="filePath" label="文件路径" min-width="300" show-overflow-tooltip />
                <el-table-column prop="fileSize" label="大小" width="100" align="center">
                  <template #default="{ row }">
                    {{ row.fileType === 'DIRECTORY' ? '-' : formatSize(row.fileSize) }}
                  </template>
                </el-table-column>
                <el-table-column prop="lastModified" label="修改时间" width="160" align="center">
                  <template #default="{ row }">
                    {{ formatTime(row.lastModified) }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="150" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      v-if="row.fileType !== 'DIRECTORY'"
                      size="small"
                      type="primary"
                      link
                      @click="handleDownloadFile(row)"
                    >
                      下载
                    </el-button>
                    <el-button
                      size="small"
                      type="danger"
                      link
                      @click="handleDeleteFile(row)"
                    >
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>

              <!-- 分页（仅在表格视图显示） -->
              <div v-if="fileViewMode === 'table'" class="pagination-container">
                <el-pagination
                  v-model:current-page="filePagination.page"
                  v-model:page-size="filePagination.size"
                  :page-sizes="[10, 20, 50, 100]"
                  :total="filePagination.total"
                  layout="total, sizes, prev, pager, next, jumper"
                  @size-change="handleFileSizeChange"
                  @current-change="handleFilePageChange"
                />
              </div>

              <el-empty v-if="!fileLoading && fileViewMode === 'table' && fileList.length === 0" description="暂无文件数据" />
            </el-card>
          </div>

          <!-- ========== DATABASE类型：数据库详情界面 ========== -->
          <div v-else-if="currentProbe.type === 'DATABASE'" class="database-detail-wrapper">
            <!-- 操作栏 -->
            <div class="database-action-bar">
              <div class="action-title">
                <el-icon><DataBoard /></el-icon>
                <span class="probe-name">{{ currentProbe.name }}</span>
              </div>
              <div class="action-buttons">
                <el-button
                  type="primary"
                  :icon="Refresh"
                  :loading="databaseRefreshing"
                  :disabled="!isProbeOnline(currentProbe)"
                  @click="handleRefreshDatabase"
                >
                  刷新
                </el-button>
              </div>
            </div>

            <!-- 数据库信息 -->
            <el-card class="database-info-card" shadow="hover" v-loading="databaseLoading">
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span>数据库信息</span>
                  <el-dropdown
                    trigger="click"
                    @command="handleInstanceSwitch"
                    :disabled="instanceLoading"
                  >
                    <el-tag type="success" class="database-type-selector" style="cursor: pointer; padding: 6px 12px; height: auto;">
                      <div style="display: flex; align-items: center; gap: 6px;">
                        <el-icon v-if="instanceLoading" class="is-loading"><Loading /></el-icon>
                        <el-icon v-else><DataLine /></el-icon>
                        <span style="font-size: 13px; font-weight: 600;">
                          {{ currentInstance ? (currentInstance.name || currentInstance.databaseName || `实例 #${currentInstance.id}`) : (instances.length > 0 ? '选择数据库实例' : (databaseInfo.databaseType || 'PostgreSQL')) }}
                        </span>
                        <el-icon v-if="!instanceLoading" style="font-size: 12px;"><ArrowDown /></el-icon>
                      </div>
                    </el-tag>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <div v-if="instances.length === 0" style="padding: 10px; color: #909399; font-size: 12px;">
                          暂无数据库实例
                        </div>
                        <el-dropdown-item
                          v-for="instance in instances"
                          :key="instance.id"
                          :command="instance"
                          :class="{ 'is-active': currentInstance && currentInstance.id === instance.id }"
                          :disabled="instanceLoading"
                          :style="currentInstance && currentInstance.id === instance.id ? 'background-color: var(--el-color-primary-light-9);' : ''"
                        >
                          <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                            <div style="flex: 1;">
                              <div style="font-weight: bold; font-size: 13px;">
                                {{ instance.name || instance.databaseName || `实例 #${instance.id}` }}
                              </div>
                              <div style="font-size: 11px; color: var(--el-text-color-secondary); margin-top: 2px;">
                                {{ instance.host }}:{{ instance.port }} / {{ instance.databaseName }}
                              </div>
                            </div>
                            <el-icon v-if="currentInstance && currentInstance.instanceId === instance.instanceId" style="color: var(--el-color-primary); font-size: 18px; font-weight: bold;"><Check /></el-icon>
                          </div>
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
              <el-descriptions :column="3" border>
                <el-descriptions-item label="数据库类型">
                  <el-tag type="success">{{ databaseInfo.databaseType || 'PostgreSQL' }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="版本">{{ databaseInfo.version || '-' }}</el-descriptions-item>
                <el-descriptions-item label="连接数">{{ databaseInfo.connectionCount || 0 }}</el-descriptions-item>
                <el-descriptions-item label="数据库名">{{ databaseInfo.databaseName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="字符集">{{ databaseInfo.charset || '-' }}</el-descriptions-item>
                <el-descriptions-item label="排序规则">{{ databaseInfo.collation || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>

            <!-- 表统计 -->
            <el-card class="database-tables-card" shadow="hover" v-loading="databaseLoading">
              <template #header>
                <span>表统计</span>
              </template>
              <el-table
                :data="databaseTables"
                style="width: 100%"
                border
                stripe
                max-height="300"
              >
                <el-table-column type="index" label="序号" width="60" align="center" />
                <el-table-column prop="tableName" label="表名" min-width="150" show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-link type="primary" @click="handleViewTableData(row)">
                      {{ row.tableName }}
                    </el-link>
                  </template>
                </el-table-column>
                <el-table-column prop="rowsCount" label="行数" width="100" align="center" />
                <el-table-column prop="totalSize" label="大小" width="100" align="center">
                  <template #default="{ row }">
                    {{ formatSize(row.totalSize) }}
                  </template>
                </el-table-column>
                <el-table-column prop="indexesCount" label="索引数" width="80" align="center" />
              </el-table>

              <el-empty v-if="!databaseLoading && databaseTables.length === 0" description="暂无表数据" />
            </el-card>
          </div>

          <!-- ========== SYSTEM类型：系统资源监控（重新设计 - 显示全部39个指标）========== -->
          <div v-else class="metrics-wrapper-v2">
            <div v-loading="metricsLoading">
            <!-- 操作栏 -->
            <div class="metrics-action-bar-v2">
              <div class="metrics-title-v2">
                <el-icon class="title-icon"><DataAnalysis /></el-icon>
                <div>
                  <span class="probe-name-v2">{{ currentProbe.name }}</span>
                  <span class="probe-key-v2">{{ currentProbe.probeKey }}</span>
                </div>
              </div>
              <el-button
                type="primary"
                size="default"
                :icon="Refresh"
                :loading="metricsRefreshing"
                :disabled="!isProbeOnline(currentProbe)"
                @click="handleRefreshMetrics"
                class="refresh-action-btn"
              >
                刷新数据
              </el-button>
            </div>

            <!-- 空状态 -->
            <el-empty v-if="!metricsLoading && latestMetrics.length === 0"
                      description="暂无监控数据"
                      :image-size="120"
                      class="metrics-empty-state" />

            <!-- 完整指标显示（39个指标全部可见） -->
            <div v-else class="metrics-complete-container">

              <!-- 第一层：关键指标横条 -->
              <div class="critical-metrics-row">
                <div class="critical-metric-card" :class="getMetricClass(getMetricValue('cpu.usage'), 90)">
                  <div class="metric-icon-small cpu-icon"><Monitor /></div>
                  <div class="metric-info">
                    <div class="metric-label-small">CPU</div>
                    <div class="metric-value-large">{{ formatMetricValue('cpu.usage') }}</div>
                    <div class="metric-bar-mini">
                      <div class="metric-bar-fill-mini" :style="{ width: (getMetricValue('cpu.usage') || 0) + '%' }"></div>
                    </div>
                  </div>
                </div>

                <div class="critical-metric-card" :class="getMetricClass(getMetricValue('memory.usage'), 85)">
                  <div class="metric-icon-small memory-icon"><Odometer /></div>
                  <div class="metric-info">
                    <div class="metric-label-small">内存</div>
                    <div class="metric-value-large">{{ formatMetricValue('memory.usage') }}</div>
                    <div class="metric-bar-mini">
                      <div class="metric-bar-fill-mini" :style="{ width: (getMetricValue('memory.usage') || 0) + '%' }"></div>
                    </div>
                  </div>
                </div>

                <div class="critical-metric-card" :class="getMetricClass(getMetricValue('disk.usage'), 80)">
                  <div class="metric-icon-small disk-icon"><Files /></div>
                  <div class="metric-info">
                    <div class="metric-label-small">磁盘</div>
                    <div class="metric-value-large">{{ formatMetricValue('disk.usage') }}</div>
                    <div class="metric-bar-mini">
                      <div class="metric-bar-fill-mini" :style="{ width: (getMetricValue('disk.usage') || 0) + '%' }"></div>
                    </div>
                  </div>
                </div>

                <div class="critical-metric-card network-card">
                  <div class="metric-icon-small network-icon"><Connection /></div>
                  <div class="metric-info">
                    <div class="metric-label-small">网络</div>
                    <div class="metric-value-dual">
                      <div class="network-value">
                        <span class="network-label">↓</span>
                        {{ formatRate(getMetricValue('network.rx.rate') || 0) }}
                      </div>
                      <div class="network-value">
                        <span class="network-label">↑</span>
                        {{ formatRate(getMetricValue('network.tx.rate') || 0) }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 第二层：详细指标网格 -->
              <div class="detailed-metrics-grid">

                <!-- CPU详细信息 -->
                <div class="metrics-section-card">
                  <div class="section-header">
                    <el-icon><Monitor /></el-icon>
                    <span class="section-title">CPU 详细信息</span>
                  </div>
                  <div class="metrics-detail-grid">
                    <div class="detail-item">
                      <span class="detail-label">核心数</span>
                      <span class="detail-value">{{ getMetricValue('cpu.cores') || '-' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">1分钟负载</span>
                      <span class="detail-value">{{ getMetricValue('cpu.load.1min') || '-' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">5分钟负载</span>
                      <span class="detail-value">{{ getMetricValue('cpu.load.5min') || '-' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">15分钟负载</span>
                      <span class="detail-value">{{ getMetricValue('cpu.load.15min') || '-' }}</span>
                    </div>
                  </div>
                </div>

                <!-- 内存详细信息 -->
                <div class="metrics-section-card">
                  <div class="section-header">
                    <el-icon><Odometer /></el-icon>
                    <span class="section-title">内存详细信息</span>
                  </div>
                  <div class="metrics-detail-grid">
                    <div class="detail-item">
                      <span class="detail-label">总内存</span>
                      <span class="detail-value">{{ formatSize(getMetricValue('memory.total')) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">已用内存</span>
                      <span class="detail-value">{{ formatSize(getMetricValue('memory.used')) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">可用内存</span>
                      <span class="detail-value">{{ formatSize(getMetricValue('memory.available')) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">使用率</span>
                      <span class="detail-value">{{ formatMetricValue('memory.usage') }}</span>
                    </div>
                  </div>
                </div>

                <!-- 磁盘详细信息 -->
                <div class="metrics-section-card">
                  <div class="section-header">
                    <el-icon><Files /></el-icon>
                    <span class="section-title">磁盘详细信息</span>
                  </div>
                  <div class="metrics-detail-grid">
                    <div class="detail-item">
                      <span class="detail-label">磁盘总量</span>
                      <span class="detail-value">{{ formatSize(getMetricValue('disk.total')) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">已用空间</span>
                      <span class="detail-value">{{ formatSize(getMetricValue('disk.used')) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">可用空间</span>
                      <span class="detail-value">{{ formatSize((getMetricValue('disk.total') || 0) - (getMetricValue('disk.used') || 0)) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">根分区使用率</span>
                      <span class="detail-value">{{ formatMetricValue('disk.usage') }}</span>
                    </div>
                  </div>
                </div>

                <!-- 网络详细信息 -->
                <div class="metrics-section-card">
                  <div class="section-header">
                    <el-icon><Connection /></el-icon>
                    <span class="section-title">网络详细信息</span>
                  </div>
                  <div class="metrics-detail-grid">
                    <div class="detail-item">
                      <span class="detail-label">累计接收</span>
                      <span class="detail-value">{{ formatSize(getMetricValue('network.rx.bytes')) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">累计发送</span>
                      <span class="detail-value">{{ formatSize(getMetricValue('network.tx.bytes')) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">接收错误</span>
                      <span class="detail-value" :class="{ 'error-value': (getMetricValue('network.rx.errors') || 0) > 0 }">
                        {{ getMetricValue('network.rx.errors') || 0 }}
                      </span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">发送错误</span>
                      <span class="detail-value" :class="{ 'error-value': (getMetricValue('network.tx.errors') || 0) > 0 }">
                        {{ getMetricValue('network.tx.errors') || 0 }}
                      </span>
                    </div>
                  </div>
                </div>

                <!-- JVM详细信息 -->
                <div class="metrics-section-card" v-if="getMetricValue('jvm.heap.used') || getMetricValue('jvm.thread.count')">
                  <div class="section-header">
                    <el-icon><DataBoard /></el-icon>
                    <span class="section-title">JVM 详细信息</span>
                  </div>
                  <div class="metrics-detail-grid">
                    <div class="detail-item">
                      <span class="detail-label">堆内存已用</span>
                      <span class="detail-value">{{ getMetricValue('jvm.heap.used') || 0 }} MB</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">堆内存最大</span>
                      <span class="detail-value">{{ getMetricValue('jvm.heap.max') || 0 }} MB</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">堆使用率</span>
                      <span class="detail-value">{{ getMetricValue('jvm.heap.usage') ? parseFloat(getMetricValue('jvm.heap.usage')).toFixed(2) + '%' : '-' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">线程数</span>
                      <span class="detail-value">{{ getMetricValue('jvm.thread.count') || 0 }} (峰值: {{ getMetricValue('jvm.thread.peak') || 0 }})</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">已加载类</span>
                      <span class="detail-value">{{ (getMetricValue('jvm.class.loaded') || 0).toLocaleString() }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">JVM总内存</span>
                      <span class="detail-value">{{ getMetricValue('jvm.runtime.totalMemory') || 0 }} MB</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">JVM空闲内存</span>
                      <span class="detail-value">{{ getMetricValue('jvm.runtime.freeMemory') || 0 }} MB</span>
                    </div>
                  </div>
                </div>

                <!-- OS详细信息 -->
                <div class="metrics-section-card" v-if="getMetricValue('os.process.count') || getMetricValue('os.thread.count')">
                  <div class="section-header">
                    <el-icon><Monitor /></el-icon>
                    <span class="section-title">系统详细信息</span>
                  </div>
                  <div class="metrics-detail-grid">
                    <div class="detail-item">
                      <span class="detail-label">进程总数</span>
                      <span class="detail-value">{{ getMetricValue('os.process.count') || 0 }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">线程总数</span>
                      <span class="detail-value">{{ getMetricValue('os.thread.count') || 0 }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">系统运行时间</span>
                      <span class="detail-value">{{ formatUptime(getMetricValue('os.uptime.seconds')) }}</span>
                    </div>
                  </div>
                </div>

                <!-- 进程详细信息 -->
                <div class="metrics-section-card" v-if="getMetricValue('process.cpu.usage') || getMetricValue('process.memory.resident')">
                  <div class="section-header">
                    <el-icon><DataLine /></el-icon>
                    <span class="section-title">进程详细信息</span>
                  </div>
                  <div class="metrics-detail-grid">
                    <div class="detail-item">
                      <span class="detail-label">进程CPU使用率</span>
                      <span class="detail-value">{{ getMetricValue('process.cpu.usage') ? parseFloat(getMetricValue('process.cpu.usage')).toFixed(2) + '%' : '-' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">进程常驻内存</span>
                      <span class="detail-value">{{ getMetricValue('process.memory.resident') || 0 }} MB</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">JVM运行时间</span>
                      <span class="detail-value">{{ formatUptime(getMetricValue('process.jvm.uptime.seconds')) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">进程ID</span>
                      <span class="detail-value">{{ getMetricValue('process.id') || '-' }}</span>
                    </div>
                  </div>
                </div>

              </div>

              <!-- 更新时间提示 -->
              <div class="metrics-update-time-v2">
                <el-icon><Clock /></el-icon>
                <span>上次更新: {{ formatTime(currentProbe.lastHeartbeat) }}</span>
              </div>
            </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 文件上传对话框 -->
    <el-dialog
      v-model="fileUploadDialogVisible"
      title="上传文件"
      width="480px"
      :close-on-click-modal="false"
      @closed="resetFileUpload"
    >
      <div style="margin-bottom: 12px; font-size: 13px; color: #64748b;">
        目标目录：<strong>{{ currentProbe ? getScanPath() : '-' }}</strong>
      </div>
      <el-upload
        ref="fileUploadRef"
        drag
        multiple
        :auto-upload="false"
        :on-change="handleFileUploadChange"
        :http-request="noopRequest"
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div style="font-size: 12px; color: #909399;">文件将保存到探针的扫描路径目录</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="fileUploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleFileUpload" :loading="fileUploading">
          上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 表数据查看对话框 -->
    <el-dialog
      v-model="tableDataVisible"
      :title="`表数据 - ${currentTableName}`"
      width="90%"
      destroy-on-close
      class="table-data-dialog"
    >
      <div v-loading="tableDataLoading">
        <!-- 搜索和过滤 -->
        <div class="table-data-toolbar">
          <el-input
            v-model="tableDataSearch"
            placeholder="输入关键词搜索..."
            clearable
            style="width: 300px; margin-right: 10px"
            @input="handleTableDataSearch"
            @clear="handleTableDataSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <el-select
            v-model="tableDataSearchColumn"
            placeholder="选择列"
            clearable
            style="width: 150px; margin-right: 10px"
          >
            <el-option
              v-for="column in tableDataColumns"
              :key="column.name"
              :label="column.name"
              :value="column.name"
            />
          </el-select>

          <el-button
            type="primary"
            :icon="Search"
            @click="handleTableDataSearch"
            :loading="tableDataLoading"
          >
            搜索
          </el-button>

          <el-button
            @click="handleTableDataReset"
            :disabled="!tableDataSearch && !tableDataSearchColumn"
          >
            重置
          </el-button>

          <el-button
            type="success"
            :icon="Download"
            @click="handleExportTableData"
            :disabled="tableDataRows.length === 0"
          >
            导出数据
          </el-button>

          <div style="flex: 1"></div>

          <el-text type="info" size="small">
            共 {{ tableDataTotal }} 条记录
            <span v-if="filteredCount !== null && filteredCount !== tableDataTotal">
              （搜索后 {{ filteredCount }} 条）
            </span>
          </el-text>
        </div>

        <!-- 数据表格 -->
        <el-table
          :data="tableDataRows"
          style="width: 100%"
          border
          stripe
          max-height="500"
          :default-sort="{ prop: 'id', order: 'ascending' }"
        >
          <el-table-column
            v-for="column in tableDataColumns"
            :key="column.name"
            :prop="column.name"
            :label="column.name"
            :min-width="100"
            show-overflow-tooltip
          />
        </el-table>

        <!-- 分页 -->

        <!-- 分页模式切换 -->
        <div style="margin-top: 20px; display: flex; justify-content: space-between; align-items: center;">
          <div>
            <el-switch
              v-model="useCursorPagination"
              active-text="游标分页"
              inactive-text="传统分页"
              @change="handleRefreshTableData"
              style="margin-right: 10px"
            />
            <el-tag v-if="orderByColumn" type="info" size="small">
              排序: {{ orderByColumn }} 降序
            </el-tag>
          </div>

          <!-- 游标分页：加载更多 -->
          <div v-if="useCursorPagination">
            <el-button
              type="primary"
              :loading="loadingMore"
              :disabled="!hasMore"
              @click="handleLoadMore"
            >
              {{ hasMore ? '加载更多' : '已加载全部' }}
            </el-button>
            <el-tag style="margin-left: 10px">
              已加载 {{ tableDataRows.length }} / 总计 {{ tableDataTotal }} 条
            </el-tag>
          </div>
        </div>
        <el-pagination
          v-if="!useCursorPagination && tableDataTotal > 0"
          v-model:current-page="tableDataPageNum"
          v-model:page-size="tableDataPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="tableDataTotal"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleTableDataSizeChange"
          @current-change="handleTableDataPageChange"
          style="margin-top: 20px; justify-content: center"
        />

        <el-empty v-if="!tableDataLoading && tableDataRows.length === 0" description="暂无数据" />
      </div>

      <template #footer>
        <el-button @click="tableDataVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleRefreshTableData" :loading="tableDataLoading">
          刷新
        </el-button>
      </template>
    </el-dialog>

    <!-- 探针表单对话框 -->
    <ProbeFormDialog
      v-model="formDialogVisible"
      :probe="currentProbe"
      @success="handleFormSuccess"
    />

    <!-- JSON导入对话框 -->
    <ProbeJsonImport
      v-model="jsonImportVisible"
      @success="handleImportSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, DataAnalysis, Clock, FolderOpened, Folder, Document, DataBoard, Monitor, Odometer, Files, Connection, DataLine, VideoPlay, Download, Plus, ArrowDown, Check, Loading, List, Operation, InfoFilled, Search, Upload } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { probeApi, exportJson } from '@/api/probe'
import { useStore } from '@/store'

const { state: storeState, getters: storeGetters } = useStore()
const hasPermission = (perm) => storeState.user.permissions.includes(perm) || storeGetters.isAdmin.value
import * as fileProbeApi from '@/api/fileProbe'
import { databaseProbeApi } from '@/api/databaseProbe'
import { exportExcelFile } from '@/utils/export'
import { getLatestMetrics, getProbeMetricsSummary } from '@/api/metrics'
import { formatRate, formatDate } from '@/utils'
import { isProbeOnline, isProbeOffline, validateProbeOnline } from '@/utils/probeStatus'
import ProbeFormDialog from '@/components/ProbeFormDialog.vue'
import ProbeJsonImport from '@/components/ProbeJsonImport.vue'

const router = useRouter()

// 提示去重机制 - 避免短时间内重复显示相同的提示
const messageHistory = ref(new Map())
const showMessageWithDeduplication = (message, type = 'info') => {
  const key = `${type}:${message}`
  const now = Date.now()
  const lastTime = messageHistory.value.get(key)

  // 如果相同提示在2秒内显示过，则忽略
  if (lastTime && now - lastTime < 2000) {
    return
  }

  messageHistory.value.set(key, now)

  // 清理过期的历史记录（超过5秒）
  messageHistory.value.forEach((timestamp, msgKey) => {
    if (now - timestamp > 5000) {
      messageHistory.value.delete(msgKey)
    }
  })

  switch (type) {
    case 'success':
      ElMessage.success(message)
      break
    case 'warning':
      ElMessage.warning(message)
      break
    case 'error':
      ElMessage.error(message)
      break
    case 'info':
    default:
      ElMessage.info(message)
      break
  }
}

// 响应式数据
const loading = ref(false)
const exporting = ref(false)
const tableData = ref([])
const metricsLoading = ref(false)
const metricsRefreshing = ref(false)
const latestMetrics = ref([])

// FILE 类型数据
const fileList = ref([])
const fileStatistics = ref(null)
const fileLoading = ref(false)
const fileScanning = ref(false)

// 文件上传
const fileUploadDialogVisible = ref(false)
const fileUploadRef = ref(null)
const fileUploadFiles = ref([])
const fileUploading = ref(false)

const filePagination = reactive({
  page: 1,
  size: 50,
  total: 0
})

// 文件视图模式：'table' 或 'tree'
const fileViewMode = ref('table')
const fileTreeData = ref([])
const fileTreeRef = ref(null)
const showFullPath = ref(false)

// DATABASE 类型数据
const databaseInfo = ref({})
const databaseTables = ref([])
const databaseLoading = ref(false)
const databaseRefreshing = ref(false)

	const instances = ref([]);
	const currentInstance = ref(null);
	const instanceLoading = ref(false);
// 数据库基本信息
const currentDatabaseInfo = ref({
	databaseType: '',
	databaseHost: '',
	databasePort: '',
	databaseName: ''
})

// 表数据查看对话框
const tableDataVisible = ref(false)
const tableDataLoading = ref(false)
const currentTableName = ref('')
const tableDataColumns = ref([])
const tableDataRows = ref([])
const tableDataTotal = ref(0)
const tableDataPageNum = ref(1)
const tableDataPageSize = ref(50)

// 表数据搜索和过滤
const tableDataSearch = ref('')
const tableDataSearchColumn = ref('')
const filteredCount = ref(null)  // 搜索后的记录数
const exportingData = ref(false)  // 正在导出

	// 游标分页支持
	const useCursorPagination = ref(true)  // 默认使用游标分页
	const nextCursor = ref(null)  // 下一个游标
	const hasMore = ref(true)  // 是否有更多数据
	const loadingMore = ref(false)  // 正在加载更多
	const orderByColumn = ref(null)  // 排序列

const queryForm = reactive({
  name: '',
  status: '',
  type: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const formDialogVisible = ref(false)
const detailVisible = ref(false)
const jsonImportVisible = ref(false)
const currentProbe = ref(null)

const rules = {
  type: [{ required: true, message: '请选择探针类型', trigger: 'change' }],
  name: [{ required: true, message: '请输入探针名称', trigger: 'blur' }],
  // probeKey 验证已移除，允许留空，留空则自动生成
  // hostIp 验证已移除，允许留空，留空则使用默认值 127.0.0.1
  // 文件探针专属验证
  scanPath: [
    {
      required: true,
      message: '请输入扫描路径',
      trigger: 'blur',
      validator: (_rule, value, callback) => {
        if (form.type === 'FILE' && !value) {
          callback(new Error('文件探针必须指定扫描路径'))
        } else {
          callback()
        }
      }
    }
  ]
}

// 计算属性
const hasFileProbes = computed(() => {
  return tableData.value.some(item => item.type === 'FILE')
})

const hasDatabaseProbes = computed(() => {
  return tableData.value.some(item => item.type === 'DATABASE')
})

const hasSystemProbes = computed(() => {
  return tableData.value.some(item => item.type !== 'FILE')
})

const isFileProbe = computed(() => {
  return queryForm.type === 'FILE'
})

// 监控queryForm.type的变化
watch(() => queryForm.type, (newVal, oldVal) => {

  // 如果在非用户操作的情况下type被改变，记录警告
  console.trace('[Watch] queryForm.type 变化调用栈')
})

const getStatusType = (status) => {
  // Handle null, undefined, or empty status
  if (!status) {
    return 'info'
  }

  const typeMap = {
    'online': 'success',
    'offline': 'info',
    'error': 'danger',
    'disabled': 'warning'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  if (!status) {
    return '未知'
  }

  const textMap = {
    'online': '在线',
    'offline': '离线',
    'error': '异常',
    'disabled': '已禁用'
  }
  return textMap[status] || status
}

const getStatusClass = (status) => {
  return `status-${status.toLowerCase()}`
}

const getStatusIconStyle = (status) => {
  if (status === 'online') {
    // 浅色背景模式：使用深绿色图标
    return {
      color: '#67c23a',
      fontSize: '16px',
      fontWeight: 'bold'
    }
  } else if (status === 'offline') {
    // 浅色背景模式：使用深灰色图标
    return {
      color: '#909399',
      fontSize: '16px',
      opacity: '0.8'
    }
  } else if (status === 'error') {
    // 浅色背景模式：使用深红色图标
    return {
      color: '#f56c6c',
      fontSize: '16px'
    }
  }
  return {
    fontSize: '16px'
  }
}

const getStatusIconClass = (status) => {
  const classMap = {
    'online': 'status-icon-online',
    'offline': 'status-icon-offline',
    'error': 'status-icon-error'
  }
  return classMap[status] || ''
}

const getTypeClass = (type) => {
  return `type-${type.toLowerCase()}`
}

const getTypeColor = (type) => {
  const colorMap = {
    SYSTEM: 'primary',
    FILE: 'danger',
    DATABASE: 'success'
  }
  return colorMap[type] || 'info'
}

const getTypeLabel = (type) => {
  const labelMap = {
    SYSTEM: '系统监控',
    FILE: '文件监控',
    DATABASE: '数据库'
  }
  return labelMap[type] || type
}

// 获取数据库类型名称
const getDatabaseTypeName = (config) => {
  // 优先从databaseInfo获取
  if (databaseInfo.value && databaseInfo.value.databaseType) {
    const typeMap = {
      'mysql': 'MySQL',
      'postgresql': 'PostgreSQL',
      'oracle': 'Oracle',
      'sqlserver': 'SQL Server',
      'mongodb': 'MongoDB',
      'redis': 'Redis'
    }
    const type = typeMap[databaseInfo.value.databaseType.toLowerCase()]
    if (type) return type
  }

  // 其次从config获取
  if (!config) return 'PostgreSQL'  // 默认返回PostgreSQL
  try {
    const cfg = typeof config === 'string' ? JSON.parse(config) : config
    const typeMap = {
      'MYSQL': 'MySQL',
      'POSTGRESQL': 'PostgreSQL',
      'ORACLE': 'Oracle',
      'SQLSERVER': 'SQL Server',
      'MONGODB': 'MongoDB',
      'REDIS': 'Redis',
      'DM': '达梦',
      'KINGBASE': '金仓',
      'H2': 'H2',
      'DERBY': 'Derby'
    }
    return typeMap[cfg.dbType] || cfg.dbType || 'PostgreSQL'  // 默认PostgreSQL
  } catch {
    return 'PostgreSQL'  // 解析失败也返回PostgreSQL
  }
}

// 获取数据库类型颜色
const getDatabaseTypeColor = (config) => {
  if (!config) return 'info'
  try {
    const cfg = typeof config === 'string' ? JSON.parse(config) : config
    const colorMap = {
      'MYSQL': 'success',
      'POSTGRESQL': 'success',
      'MONGODB': 'warning',
      'REDIS': 'danger',
      'ORACLE': 'primary',
      'DM': 'primary'
    }
    return colorMap[cfg.dbType] || 'info'
  } catch {
    return 'info'
  }
}

const formatSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
}

const formatJSONArray = (str) => {
  if (!str) return '-'
  try {
    const arr = JSON.parse(str)
    return Array.isArray(arr) ? arr.join(', ') : str
  } catch {
    return str
  }
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const tableRowClassName = ({ row, rowIndex }) => {
  if (row.status === 'error') {
    return 'error-row'
  }
  if (row.status === 'offline') {
    return 'offline-row'
  }
  return ''
}

// 获取进度条颜色
const getProgressColor = (value) => {
  if (value >= 90) return '#f56c6c'
  if (value >= 70) return '#e6a23c'
  return '#67c23a'
}

// 获取指标状态类（用于新设计）
const getMetricClass = (value, threshold) => {
  if (!value) return 'metric-normal'
  if (value >= threshold) return 'metric-critical'
  if (value >= threshold * 0.8) return 'metric-warning'
  return 'metric-normal'
}

// 格式化运行时间（用于新设计）
const formatUptime = (seconds) => {
  if (!seconds) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)

  if (days > 0) {
    return `${days}天 ${hours}小时`
  } else if (hours > 0) {
    return `${hours}小时 ${minutes}分钟`
  } else {
    return `${minutes}分钟`
  }
}

// API 调用
const fetchList = async () => {

  // 防御性检查：确保type字段不是意外的值
  if (queryForm.type !== '' && queryForm.type !== null && queryForm.type !== undefined &&
      queryForm.type !== 'FILE' && queryForm.type !== 'SYSTEM' && queryForm.type !== 'DATABASE' && queryForm.type !== 'CUSTOM') {
    console.warn('[刷新列表] 检测到异常的type值，重置为空:', queryForm.type)
    queryForm.type = ''
  }

  loading.value = true
  try {
    // 根据查询类型决定使用哪个API
    if (queryForm.type === 'FILE') {
      // 查询文件探针
      const { code, data } = await fileProbeApi.getPage({
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
        name: queryForm.name || undefined,
        status: queryForm.status || undefined
      })
      if (code === 200) {
        tableData.value = data.records || []
        pagination.total = data.total || 0
      }
    } else if (queryForm.type === '' || queryForm.type === undefined || queryForm.type === null) {
      // 没有指定类型，统一从 probe 表查询所有类型（包括 SYSTEM/DATABASE/FILE）
      const { code, data } = await probeApi.getList({
        pageNum: 1,
        pageSize: 1000,
        name: queryForm.name || undefined,
        status: queryForm.status || undefined
      })

      if (code === 200) {
        const allRecords = data.records || []
        pagination.total = allRecords.length
        const start = (pagination.pageNum - 1) * pagination.pageSize
        const end = start + pagination.pageSize
        tableData.value = allRecords.slice(start, end)
      }
    } else {
      // 查询系统探针（包括SYSTEM/CUSTOM）
      const { code, data } = await probeApi.getList({
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
        name: queryForm.name || undefined,
        status: queryForm.status || undefined,
        type: queryForm.type
      })
      if (code === 200) {
        tableData.value = data.records || []
        pagination.total = data.total || 0
        if (data.records && data.records.length > 0) {
        }
      }
    }

    // 调试：打印探针状态值
    if (tableData.value && tableData.value.length > 0) {
      tableData.value.forEach(probe => {
      })
    }
  } finally {
    loading.value = false
  }
}

// 事件处理
const handleSearch = () => {
  pagination.pageNum = 1
  fetchList()
}

const handleReset = () => {
  Object.assign(queryForm, {
    name: '',
    status: '',
    type: ''
  })
  handleSearch()
}

const handleExport = async () => {
  exporting.value = true
  try {
    const res = await probeApi.export(queryForm)
    exportExcelFile(res.data, '探针列表.xlsx')
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
  } finally {
    exporting.value = false
  }
}

// 导出JSON配置
const handleExportJson = async () => {
  exporting.value = true
  try {
    const res = await exportJson(queryForm)
    exportExcelFile(res.data, '探针配置.json')
    ElMessage.success('JSON配置导出成功')
  } catch (error) {
    console.error('JSON导出失败:', error)
  } finally {
    exporting.value = false
  }
}

// 处理快速命令
const handleQuickCommand = (command) => {
  if (command === 'quick') {
    currentProbe.value = null // 新增模式
    formDialogVisible.value = true
  } else if (command === 'import') {
    jsonImportVisible.value = true
  }
}

// 表单成功回调
const handleFormSuccess = () => {
  ElMessage.success('操作成功')
  handleSearch() // 刷新列表
}

// JSON导入成功回调
const handleImportSuccess = () => {
  ElMessage.success('探针导入成功')
  handleSearch() // 刷新列表
}

// 处理单元格点击事件，阻止操作列触发行点击
const handleCellClick = (row, column, cell, event) => {
  // 如果点击的是操作列，阻止事件冒泡
  if (column.label === '操作') {
    event.stopPropagation()
  }
}

const handleView = async (row) => {
  detailVisible.value = true
  metricsLoading.value = true

  // 检查探针是否离线，只显示一次提示
  const isOffline = isProbeOffline(row)

  try {
    // 根据探针类型获取完整详情数据（统一使用 probeApi，因为列表数据来自 probe 表）
    let detailData
    const { code, data } = await probeApi.getById(row.id)
    if (code === 200) {
      detailData = data
    }

    if (detailData) {
      currentProbe.value = detailData
    } else {
      currentProbe.value = { ...row }
    }

    // ⭐ 清空数据库实例信息，避免切换探针时使用上一个探针的实例
    if (row.type === 'DATABASE') {
      currentInstance.value = null
      console.log('[handleView] 已清空currentInstance，准备重新获取', row.probeKey)
    }

    // 获取最新监控数据时检查状态
    // 只有SYSTEM类型的探针才获取系统指标（CPU、内存等）
    if (row.type === 'SYSTEM' && isProbeOnline(row)) {
      await fetchLatestMetrics(row.id)
    } else if (row.type === 'SYSTEM') {
      // 离线的SYSTEM探针不获取指标数据
      latestMetrics.value = []
    }
    // DATABASE和FILE探针不需要获取系统指标

    // ✅ 自动刷新数据：如果探针在线，自动触发一次数据刷新
    if (!isOffline && isProbeOnline(currentProbe.value)) {
      console.log('[handleView] 探针在线，自动触发数据刷新...', row.type)

      // 等待500ms让UI先渲染探针信息
      await new Promise(resolve => setTimeout(resolve, 500))

      // 根据探针类型触发不同的刷新逻辑
      if (row.type === 'DATABASE') {
        // DATABASE探针：触发元数据采集
        try {
          await databaseProbeApi.triggerCollection(row.probeKey)
          console.log('[handleView] ✓ 数据库采集命令发送成功')

          // 等待采集完成并重新获取元数据
          console.log('[handleView] 等待元数据采集完成...')
          ElMessage({
            message: '正在采集元数据，请稍候...',
            type: 'info',
            duration: 3000,
            showClose: false
          })

          // 等待8秒让采集完成
          await new Promise(resolve => setTimeout(resolve, 8000))

          // 重新获取元数据和表统计
          console.log('[handleView] 重新获取数据库元数据...')
          await fetchDatabaseTables()

          ElMessage.success('元数据采集完成')
        } catch (error) {
          console.error('[handleView] 触发数据库采集失败:', error)
          ElMessage.error('元数据采集失败: ' + (error.message || '未知错误'))
        }
      } else if (row.type === 'SYSTEM') {
        // SYSTEM探针：指标已通过fetchLatestMetrics获取，无需额外刷新
        console.log('[handleView] SYSTEM探针指标已刷新')
      } else if (row.type === 'FILE') {
        // FILE探针：触发文件扫描
        try {
          await fileProbeApi.triggerScan(row.id)
          console.log('[handleView] ✓ 文件扫描命令发送成功')
        } catch (error) {
          console.error('[handleView] 触发文件扫描失败:', error)
        }
      }

      console.log('[handleView] 自动数据刷新完成')
    } else {
      console.log('[handleView] 探针离线，跳过自动数据刷新')
    }

    // 只在数据加载完成后显示一次离线提示
    if (isOffline) {
      showMessageWithDeduplication('探针已离线，部分功能不可用', 'warning')
    }
  } catch (error) {
    console.error('获取探针详情失败:', error)
    currentProbe.value = { ...row }
  } finally {
    metricsLoading.value = false
  }
}

// 获取最新监控数据
const fetchLatestMetrics = async (probeId) => {
  // 状态检查
  if (!currentProbe.value || !isProbeOnline(currentProbe.value)) {
    console.warn('[fetchLatestMetrics] 探针离线，跳过指标获取')
    latestMetrics.value = []
    return
  }

  metricsLoading.value = true
  try {
    // 使用新的API获取指标摘要，包含网络速率和CPU负载
    const { code, data } = await getProbeMetricsSummary(probeId)
    if (code === 200 && data) {
      // 将ProbeMetricsSummary转换为旧的格式以保持兼容性
      const metrics = [
        // CPU指标
        { metricName: 'cpu.usage', metricValue: data.cpuUsage, unit: '%' },
        { metricName: 'cpu.cores', metricValue: data.cpuCores, unit: '' },
        { metricName: 'cpu.load.1min', metricValue: data.cpuLoad1min, unit: '' },
        { metricName: 'cpu.load.5min', metricValue: data.cpuLoad5min, unit: '' },
        { metricName: 'cpu.load.15min', metricValue: data.cpuLoad15min, unit: '' },

        // 内存指标
        { metricName: 'memory.usage', metricValue: data.memoryUsage, unit: '%' },
        { metricName: 'memory.used', metricValue: data.memoryUsed, unit: 'B' },
        { metricName: 'memory.total', metricValue: data.memoryTotal, unit: 'B' },
        { metricName: 'memory.available', metricValue: data.memoryAvailable, unit: 'B' },

        // 磁盘指标
        { metricName: 'disk.usage', metricValue: data.diskUsage, unit: '%' },
        { metricName: 'disk.used', metricValue: data.diskUsed, unit: 'B' },
        { metricName: 'disk.total', metricValue: data.diskTotal, unit: 'B' },

        // 网络指标
        { metricName: 'network.rx.rate', metricValue: data.networkRxRate, unit: 'B/s' },
        { metricName: 'network.tx.rate', metricValue: data.networkTxRate, unit: 'B/s' },
        { metricName: 'network.rx.bytes', metricValue: data.networkRxBytes, unit: 'B' },
        { metricName: 'network.tx.bytes', metricValue: data.networkTxBytes, unit: 'B' },
        { metricName: 'network.rx.errors', metricValue: data.networkRxErrors, unit: '' },
        { metricName: 'network.tx.errors', metricValue: data.networkTxErrors, unit: '' },

        // JVM指标
        { metricName: 'jvm.heap.used', metricValue: data.jvmHeapUsed, unit: 'MB' },
        { metricName: 'jvm.heap.max', metricValue: data.jvmHeapMax, unit: 'MB' },
        { metricName: 'jvm.heap.usage', metricValue: data.jvmHeapUsage, unit: '%' },
        { metricName: 'jvm.thread.count', metricValue: data.jvmThreadCount, unit: '' },
        { metricName: 'jvm.thread.peak', metricValue: data.jvmThreadPeak, unit: '' },
        { metricName: 'jvm.class.loaded', metricValue: data.jvmClassLoaded, unit: '' },
        { metricName: 'jvm.total.memory', metricValue: data.jvmTotalMemory, unit: 'MB' },
        { metricName: 'jvm.free.memory', metricValue: data.jvmFreeMemory, unit: 'MB' },

        // OS指标
        { metricName: 'os.process.count', metricValue: data.osProcessCount, unit: '' },
        { metricName: 'os.thread.count', metricValue: data.osThreadCount, unit: '' },
        { metricName: 'os.uptime.seconds', metricValue: data.osUptimeSeconds, unit: '' },

        // 进程指标
        { metricName: 'process.cpu.usage', metricValue: data.processCpuUsage, unit: '%' },
        { metricName: 'process.memory.resident', metricValue: data.processMemoryResident, unit: 'MB' },
        { metricName: 'process.jvm.uptime.seconds', metricValue: data.processJvmUptime, unit: '' },
        { metricName: 'process.id', metricValue: data.processId, unit: '' }
      ].filter(m => m.metricValue !== null && m.metricValue !== undefined)
      latestMetrics.value = metrics
    }
  } catch (error) {
    console.error('获取监控数据失败:', error)
  } finally {
    metricsLoading.value = false
  }
}

// 刷新系统资源数据
const handleRefreshMetrics = async () => {
  console.log('%c========== [handleRefreshMetrics] 刷新系统资源数据 ==========', 'color: #409eff; font-weight: bold')

  if (!currentProbe.value?.id) {
    console.warn('%c✗ 跳过：没有currentProbe.id', 'color: #e6a23c;')
    return
  }

  console.log('%ccurrentProbe:', 'color: #67c23a; font-weight: bold', currentProbe.value)

  // 状态检查
  if (!validateProbeOnline(currentProbe.value, '刷新数据')) {
    console.warn('%c✗ 探针离线，无法刷新', 'color: #f56c6c; font-weight: bold')
    return
  }

  console.log('%c✓ 探针在线验证通过', 'color: #67c23a; font-weight: bold')

  metricsRefreshing.value = true
  try {
    console.log('%c调用 fetchLatestMetrics，probeId:', 'color: #67c23a;', currentProbe.value.id)
    await fetchLatestMetrics(currentProbe.value.id)
    console.log('%c✓ 系统资源数据已刷新', 'color: #67c23a; font-weight: bold')
    ElMessage.success('系统资源数据已刷新')
  } catch (error) {
    console.error('%c✗ 刷新失败:', 'color: #f56c6c; font-weight: bold', error)
  } finally {
    metricsRefreshing.value = false
    console.log('%c=======================================================', 'color: #409eff; font-weight: bold')
  }
}

// 点击卡片（可扩展为查看详情）
const handleMetricClick = (metricType) => {
  // 可以添加打开详情对话框的逻辑
}

// ========== FILE 类型方法 ==========

// 触发文件扫描
const handleFileScan = async () => {
  if (!currentProbe.value?.id || currentProbe.value.type !== 'FILE') return

  // 状态检查
  if (!validateProbeOnline(currentProbe.value, '触发文件扫描')) {
    return
  }

  fileScanning.value = true
  try {
    await fileProbeApi.triggerScan(currentProbe.value.id)
    ElMessage.success('文件扫描已启动')

    // 等待2秒后刷新文件列表
    setTimeout(() => {
      fetchFileList()
    }, 2000)
  } catch (error) {
    console.error('触发扫描失败:', error)
    console.error('触发扫描失败:', error)
  } finally {
    fileScanning.value = false
  }
}

// 文件上传功能
const getScanPath = () => {
  if (!currentProbe.value?.config) return '-'
  try {
    const config = typeof currentProbe.value.config === 'string' ? JSON.parse(currentProbe.value.config) : currentProbe.value.config
    return config.scanPath || config.path || '-'
  } catch { return '-' }
}

const showFileUploadDialog = () => {
  fileUploadDialogVisible.value = true
}

const noopRequest = () => {} // el-upload 需要 http-request 但我们手动上传

const handleFileUploadChange = (uploadFile, fileList) => {
  fileUploadFiles.value = fileList
}

const handleFileUpload = async () => {
  if (!fileUploadFiles.value.length) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  if (!currentProbe.value?.id) {
    ElMessage.error('未选择探针')
    return
  }

  fileUploading.value = true
  let successCount = 0
  let failCount = 0

  try {
    for (const uploadFile of fileUploadFiles.value) {
      try {
        const formData = new FormData()
        formData.append('file', uploadFile.raw)
        const res = await fileProbeApi.uploadToProbe(currentProbe.value.id, formData)
        if (res.code === 200) {
          successCount++
        } else {
          failCount++
        }
      } catch (e) {
        failCount++
      }
    }

    if (successCount > 0) {
      ElMessage.success(`成功上传 ${successCount} 个文件`)
      // 刷新文件列表
      await fetchFileList()
      fileUploadDialogVisible.value = false
    }
    if (failCount > 0) {
      ElMessage.warning(`${failCount} 个文件上传失败`)
    }
  } catch (e) {
    ElMessage.error('上传失败')
  } finally {
    fileUploading.value = false
  }
}

const resetFileUpload = () => {
  fileUploadFiles.value = []
  fileUploadRef.value?.clearFiles()
}

// 获取文件列表
const fetchFileList = async () => {
  if (!currentProbe.value?.id || currentProbe.value.type !== 'FILE') return

  // 状态检查（只记录日志，不显示提示，因为handleRowClick已经显示了通用提示）
  if (!isProbeOnline(currentProbe.value)) {
    console.warn('[fetchFileList] 探针离线，无法获取文件列表')
    fileList.value = []
    filePagination.total = 0
    fileTreeData.value = []
    return
  }

  fileLoading.value = true
  try {
    // 文件统计信息已通过 ProbeController.getById 包含在 currentProbe 中
    fileStatistics.value = currentProbe.value

    // 根据视图模式获取数据
    if (fileViewMode.value === 'tree') {
      // 树形视图：获取所有文件（不分页）
      try {
        // 获取所有文件记录
        const allFilesResponse = await fileProbeApi.getFiles(currentProbe.value.id, {
          pageNum: 1,
          pageSize: 1000  // 后端限制最大1000
        })

        if (allFilesResponse.code === 200 && allFilesResponse.data) {
          const allFiles = allFilesResponse.data.records || []
          // 构建树形结构
          fileTreeData.value = buildFileTree(allFiles)
          console.log('File tree built:', fileTreeData.value.length, 'root nodes')
        } else {
          fileTreeData.value = []
        }
      } catch (error) {
        console.error('获取文件树数据失败:', error)
        fileTreeData.value = []
      }
    } else {
      // 表格视图：分页获取
      const response = await fileProbeApi.getFiles(currentProbe.value.id, {
        pageNum: filePagination.page,
        pageSize: filePagination.size
      })

      if (response.code === 200 && response.data) {
        fileList.value = response.data.records || []
        filePagination.total = response.data.total || 0
      } else {
        fileList.value = []
        filePagination.total = 0
      }
    }
  } catch (error) {
    console.error('获取文件列表失败:', error)
    console.error('获取文件列表失败:', error)
  } finally {
    fileLoading.value = false
  }
}

// 下载文件
const handleDownloadFile = async (row) => {
  if (!row.id) {
    ElMessage.error('文件ID不存在')
    return
  }

  try {
    const response = await fileProbeApi.downloadFile(row.id)

    // 创建blob对象
    const blobData = response.data || response
    const blob = new Blob([blobData], { type: blobData.type || 'application/octet-stream' })

    // 创建下载链接
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.fileName || 'download'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success('下载成功')
  } catch (error) {
    console.error('下载文件失败:', error)
    console.error('下载文件失败:', error)
  }
}

// 删除文件
const handleDeleteFile = async (row) => {
  if (!row.id) {
    ElMessage.error('文件ID不存在')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认删除文件【${row.fileName}】吗？此操作不可恢复！`,
      '删除确认',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await fileProbeApi.deleteFile(row.id)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      // 刷新文件列表
      await fetchFileList()
    } else {
      console.error('删除失败:', error)
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除文件失败:', error)
      console.error('删除文件失败:', error)
    }
  }
}

// 文件分页处理
const handleFileSizeChange = (val) => {
  filePagination.size = val
  fetchFileList()
}

const handleFilePageChange = (val) => {
  filePagination.page = val
  fetchFileList()
}

// ========== 树形视图方法 ==========

// 树形组件配置
const treeProps = {
  label: 'fileName',
  children: 'children',
  isLeaf: (data) => data.fileType === 'FILE'
}

/**
 * 构建文件树结构
 * 将扁平的文件列表转换为树形结构
 */
const buildFileTree = (files) => {
  if (!files || files.length === 0) {
    return []
  }

  console.log('Building file tree from', files.length, 'files')

  // 创建所有节点的映射（使用 filePath 作为 key）
  const nodeMap = new Map()

  // 收集所有文件路径（用于判断父路径是否在列表中）
  const allPaths = new Set()

  // 第一步：为每个文件/目录创建节点，并收集所有路径
  files.forEach(file => {
    const path = file.filePath || ''

    // 跳过空路径
    if (!path) return

    // 记录路径
    allPaths.add(path)

    // 创建节点
    const node = {
      id: file.id,
      filePath: path,
      fileName: file.fileName,
      fileType: file.fileType,
      fileSize: file.fileSize,
      lastModified: file.lastModified,
      md5: file.md5,
      extension: file.extension,
      parentPath: file.parentPath,
      children: []
    }

    // 将节点添加到映射中
    nodeMap.set(path, node)
  })

  // 第二步：建立父子关系
  nodeMap.forEach(node => {
    const parentPath = node.parentPath

    // 如果父路径存在且在文件列表中，则建立关系
    if (parentPath && parentPath !== '' && parentPath !== '/') {
      const parentNode = nodeMap.get(parentPath)
      if (parentNode) {
        parentNode.children.push(node)
      }
    }
  })

  // 第三步：找出所有根节点
  // 根节点是那些父路径不存在于文件列表中的节点
  const rootNodes = []
  nodeMap.forEach(node => {
    const parentPath = node.parentPath

    // 判断是否为根节点：
    // 1. 父路径为空、null 或 '/'
    // 2. 父路径不在文件列表中
    const isRoot = !parentPath ||
                   parentPath === '' ||
                   parentPath === '/' ||
                   !allPaths.has(parentPath)

    if (isRoot) {
      rootNodes.push(node)
      console.log('Found root node:', node.fileName, 'parentPath:', node.parentPath)
    }
  })

  console.log('Total root nodes:', rootNodes.length)

  // 按名称排序（目录优先）
  return rootNodes.sort((a, b) => {
    // 目录优先
    if (a.fileType === 'DIRECTORY' && b.fileType === 'FILE') return -1
    if (a.fileType === 'FILE' && b.fileType === 'DIRECTORY') return 1
    // 同类型按名称排序
    return a.fileName.localeCompare(b.fileName)
  })
}

/**
 * 懒加载树节点
 * 当展开目录节点时加载其子节点
 */
const loadTreeNode = (node, resolve) => {
  if (node.data.fileType === 'FILE') {
    // 文件节点没有子节点
    resolve([])
    return
  }

  // 目录节点，加载子节点
  const children = node.data.children || []

  // 如果有子节点，直接返回
  if (children.length > 0) {
    resolve(children)
    return
  }

  // 如果没有子节点，尝试从数据库加载
  // 这里可以添加异步加载逻辑
  resolve([])
}

/**
 * 树节点点击处理
 */
const handleTreeNodeClick = (data, node) => {
  console.log('Tree node clicked:', data.fileName, data.filePath)

  // 可以在这里添加选中节点的处理逻辑
  // 例如：显示文件详情、更新面包屑导航等
}

/**
 * 显示文件详情
 */
const handleShowFileDetails = (data) => {
  console.log('Show file details:', data)

  ElMessageBox.alert(
    `
    <div style="text-align: left;">
      <p><strong>文件名：</strong>${data.fileName}</p>
      <p><strong>路径：</strong>${data.filePath}</p>
      <p><strong>类型：</strong>${data.fileType === 'DIRECTORY' ? '目录' : '文件'}</p>
      ${data.fileType === 'FILE' ? `
        <p><strong>大小：</strong>${formatSize(data.fileSize)}</p>
        <p><strong>修改时间：</strong>${formatTime(data.lastModified)}</p>
        ${data.md5 ? `<p><strong>MD5：</strong>${data.md5}</p>` : ''}
        ${data.extension ? `<p><strong>扩展名：</strong>${data.extension}</p>` : ''}
      ` : ''}
      ${data.parentPath ? `<p><strong>父路径：</strong>${data.parentPath}</p>` : ''}
    </div>
    `,
    '文件详情',
    {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '关闭'
    }
  )
}

// ========== DATABASE 类型方法 ==========


// 刷新数据库详情（先触发采集，然后查询显示）
const handleRefreshDatabase = async () => {
  console.log('%c========== [handleRefreshDatabase] 刷新数据库详情 ==========', 'color: #409eff; font-weight: bold')

  if (!currentProbe.value?.probeKey || currentProbe.value.type !== 'DATABASE') {
    console.warn('%c✗ 跳过：不是数据库探针或没有probeKey', 'color: #e6a23c;')
    return
  }

  console.log('%ccurrentProbe:', 'color: #67c23a; font-weight: bold', currentProbe.value)

  // 状态检查
  if (!validateProbeOnline(currentProbe.value, '刷新数据库详情')) {
    console.warn('%c✗ 探针离线，无法刷新', 'color: #f56c6c; font-weight: bold')
    return
  }

  console.log('%c✓ 探针在线验证通过', 'color: #67c23a; font-weight: bold')

  databaseRefreshing.value = true
  try {
    const probeKey = currentProbe.value.probeKey

    // 步骤1: 发送采集命令
    console.log('%c步骤1: 发送采集命令', 'color: #e6a23c;')
    console.log('%c  probeKey:', 'color: #67c23a;', probeKey)
    console.log('%c  API URL:', 'color: #67c23a;', `/database-probes/${probeKey}/collect`)

    const collectResponse = await databaseProbeApi.triggerCollection(probeKey)
    console.log('%c  采集命令响应:', 'color: #e6a23c;', collectResponse)

    if (collectResponse.code !== 200) {
      console.error('%c  ✗ 采集命令发送失败:', 'color: #f56c6c; font-weight: bold', collectResponse)
      throw new Error(collectResponse.message || '发送采集命令失败')
    }

    console.log('%c  ✓ 采集命令发送成功', 'color: #67c23a; font-weight: bold')
    ElMessage.info('采集指令已发送，3秒后自动刷新...')

    // 步骤2: 等待3秒让Agent采集数据
    console.log('%c步骤2: 等待3秒让Agent采集数据...', 'color: #e6a23c;')
    await new Promise(resolve => setTimeout(resolve, 3000))
    console.log('%c  ✓ 等待完成', 'color: #67c23a;')

    // 步骤3: 查询并显示数据
    console.log('%c步骤3: 查询并显示数据', 'color: #e6a23c;')
    await fetchDatabaseTables()

    console.log('%c✓ 刷新成功', 'color: #67c23a; font-weight: bold')
    ElMessage.success('数据库详情已刷新')
  } catch (error) {
    console.error('%c✗ [刷新] 失败:', 'color: #f56c6c; font-weight: bold', error)
    console.error('刷新失败:', error)
  } finally {
    databaseRefreshing.value = false
    console.log('%c=======================================================', 'color: #409eff; font-weight: bold')
  }
}

// 防止重复调用数据库实例列表
let isFetchingDatabaseInstances = false

// 获取数据库实例列表
const fetchDatabaseInstances = async () => {
  // 防止重复调用
  if (isFetchingDatabaseInstances) {
    console.warn('%c⚠️ fetchDatabaseInstances 已经在执行中，跳过重复调用', 'color: #e6a23c;')
    return
  }

  try {
    console.log('%c========== [ProbeList] fetchDatabaseInstances 被调用 ==========', 'color: #409eff; font-weight: bold')

    if (!currentProbe.value?.probeKey || currentProbe.value.type !== 'DATABASE') {
      console.warn('%c✗ 跳过：不是数据库探针或没有probeKey', 'color: #e6a23c;')
      return
    }

    instanceLoading.value = true
    isFetchingDatabaseInstances = true

    console.log('%cprobeKey:', 'color: #67c23a; font-weight: bold', currentProbe.value.probeKey)
    console.log('%c开始调用API...', 'color: #e6a23c;')

    const response = await databaseProbeApi.getInstances(currentProbe.value.probeKey)

    console.log('%cAPI完整响应:', 'color: #e6a23c;', response)
    console.log('%cresponse.code:', 'color: #e6a23c;', response.code)
    console.log('%cresponse.data:', 'color: #e6a23c;', response.data)

    if (response.code === 200 && response.data) {
      const oldInstances = instances.value
      instances.value = response.data.instances || []
      const newCount = instances.value.length

      console.log('%c✓ 获取成功: 实例数量从', 'color: #67c23a; font-weight: bold', oldInstances.length, '更新为', newCount)
      console.log('%c实例列表:', 'color: #67c23a;', instances.value)
      console.log('%cinstances.value.length:', 'color: #67c23a; font-weight: bold', instances.value.length)

      // 自动设置第一个实例为当前实例
      if (!currentInstance.value && instances.value.length > 0) {
        currentInstance.value = instances.value[0]
        console.log('%c✓ 自动设置第一个实例为当前实例:', 'color: #67c23a; font-weight: bold', currentInstance.value.name || currentInstance.value.databaseName)
      }
    } else {
      console.error('%c✗ API返回错误:', 'color: #f56c6c; font-weight: bold', response.message)
      console.log('%c将instances设置为空数组', 'color: #f56c6c;')
      instances.value = []
    }
  } catch (error) {
    console.error('%c✗ 获取数据库实例异常:', 'color: #f56c6c; font-weight: bold', error)
    console.log('%c将instances设置为空数组', 'color: #f56c6c;')
    instances.value = []
  } finally {
    instanceLoading.value = false
    isFetchingDatabaseInstances = false // 清除标志位
    console.log('%c========== fetchDatabaseInstances 结束 ==========', 'color: #909399')
  }
}

// 切换数据库实例
const handleInstanceSwitch = async (instance) => {
  console.log('%c========== [ProbeList] 切换数据库实例 ==========', 'color: #409eff; font-weight: bold')
  console.log('%c选择的实例:', 'color: #67c23a;', instance)
  console.log('%c实例字段:', 'color: #e6a23c;', Object.keys(instance))

  if (!instance || (!instance.id && !instance.instanceId)) {
    console.warn('%c✗ 无效的实例', 'color: #f56c6c; font-weight: bold')
    return
  }

  // 使用 id 或 instanceId 作为实例标识
  const instanceId = instance.id || instance.instanceId

  // 如果点击的是当前实例，不做任何操作
  if (currentInstance.value && (currentInstance.value.id === instanceId || currentInstance.value.instanceId === instanceId)) {
    console.log('%c当前已是此实例，无需切换', 'color: #e6a23c;')
    return
  }

  instanceLoading.value = true

  try {
    console.log('%c调用后端API切换实例: instanceId=', 'color: #67c23a; font-weight: bold', instanceId)


    // 调用后端API切换实例并触发采集
    const response = await databaseProbeApi.switchInstance(currentProbe.value.probeKey, instanceId)

    console.log('%c后端API响应:', 'color: #e6a23c;', response)

    if (response.code === 200) {
      // 更新当前实例
      currentInstance.value = instance

      console.log('%c✓ 后端已切换实例，等待采集完成', 'color: #67c23a; font-weight: bold')
      console.log('%c实例详情:', 'color: #67c23a;', {
        id: instance.id,
        name: instance.name,
        databaseName: instance.databaseName,
        host: instance.host,
        port: instance.port,
        probeKey: instance.probeKey,
      })

      console.log('%ccurrentProbe.probeKey已更新为:', 'color: #f39c12; font-weight: bold', currentProbe.value.probeKey)
      // 使用 name 或 databaseName 作为显示名称
      const displayName = instance.name || instance.databaseName || instance.id
      ElMessage.success(`✓ 已切换到数据库：${displayName}`)

      // 等待2秒让Agent完成采集
      await new Promise(resolve => setTimeout(resolve, 2000))

      // 刷新数据库信息和表统计
      await fetchDatabaseTables()
    } else {
      console.error('%c✗ 后端API返回错误:', 'color: #f56c6c; font-weight: bold', response.message)
      console.error('切换实例失败:', error)
    }
  } catch (error) {
    console.error('%c✗ 切换实例失败:', 'color: #f56c6c; font-weight: bold', error)
    console.error('切换实例失败:', error)
  } finally {
    instanceLoading.value = false
    console.log('%c=========================================================', 'color: #909399')
  }
}

// 获取数据库表统计
const fetchDatabaseTables = async () => {
  console.log('%c========== [fetchDatabaseTables] 开始获取数据库表统计 ==========', 'color: #409eff; font-weight: bold')

  if (!currentProbe.value?.probeKey || currentProbe.value.type !== 'DATABASE') {
    console.warn('%c✗ 跳过：不是数据库探针或没有probeKey', 'color: #e6a23c;')
    return
  }

  console.log('%ccurrentProbe:', 'color: #67c23a; font-weight: bold', currentProbe.value)
  console.log('%ccurrentInstance:', 'color: #67c23a; font-weight: bold', currentInstance.value)

  // 如果currentInstance为空，先获取实例列表
  if (!currentInstance.value) {
    console.warn('%c⚠️  currentInstance为空，先获取实例列表', 'color: #e6a23c;')
    await fetchDatabaseInstances()
    console.log('%c✓ 实例列表已获取，currentInstance =', 'color: #67c23a;', currentInstance.value)
  }

  // 状态检查（只记录日志，不显示提示，因为handleRowClick已经显示了通用提示）
  if (!isProbeOnline(currentProbe.value)) {
    console.warn('%c✗ 探针离线，无法获取数据库详情', 'color: #f56c6c; font-weight: bold')
    databaseInfo.value = {
      version: '-',
      connectionCount: 0,
      databaseName: '-',
      charset: '-',
      collation: '-',
      url: '-'
    }
    databaseTables.value = []
    return
  }

  console.log('%c✓ 探针在线验证通过', 'color: #67c23a; font-weight: bold')
  databaseLoading.value = true

  try {
    // 1. 获取数据库元数据
    console.log('%c步骤1: 调用getMetadata API', 'color: #e6a23c;')
    console.log('%c  probeKey:', 'color: #67c23a;', currentProbe.value.probeKey)
    console.log('%c  instanceId:', 'color: #67c23a;', currentInstance.value?.id || currentInstance.value?.instanceId)
    console.log('%c  databaseName:', 'color: #67c23a;', currentInstance.value?.databaseName)
    console.log('%c  API URL:', 'color: #67c23a;', `/database-probes/${currentProbe.value.probeKey}/metadata`)

    // 使用 databaseName 而不是 id，因为后端需要真实的数据库名来过滤
    const databaseName = currentInstance.value?.databaseName
    const metadataResponse = await databaseProbeApi.getMetadata(currentProbe.value.probeKey, databaseName)
    console.log('%c  getMetadata API响应:', 'color: #e6a23c;', metadataResponse)

    if (metadataResponse.code === 200 && metadataResponse.data) {
      const metadata = metadataResponse.data
      console.log('%c✓ metadata.code = 200', 'color: #67c23a;')
      console.log('%c  metadata.data keys:', 'color: #67c23a;', Object.keys(metadata))
      console.log('%c  完整metadata.data:', 'color: #67c23a;', metadata)

      // 更新数据库信息（从嵌套结构中提取）
      const dbInfo = metadata.databaseInfo || {}
      const performance = metadata.performance || {}
      databaseInfo.value = {
        databaseType: dbInfo.type || dbInfo.databaseType || '-',
        version: dbInfo.version || '-',
        connectionCount: performance.connectionCount || 0,
        databaseName: dbInfo.databaseName || '-',
        charset: dbInfo.charset || '-',
        collation: dbInfo.collation || '-',
        url: dbInfo.url || '-'
      }
      console.log('%c  databaseInfo已更新:', 'color: #67c23a;', databaseInfo.value)

      // 更新表统计（如果有）
      if (metadata.tableStats && Array.isArray(metadata.tableStats)) {
        console.log('%c  找到 tableStats，数量:', 'color: #67c23a;', metadata.tableStats.length)
        console.log('%c  tableStats内容:', 'color: #67c23a;', metadata.tableStats)

        databaseTables.value = metadata.tableStats.map(table => ({
          tableName: table.tableName || '-',
          rowsCount: table.rowCount || 0,
          totalSize: table.dataSize || 0,
          indexesCount: table.indexSize || 0
        }))
        console.log('%c  databaseTables已更新，数量:', 'color: #67c23a;', databaseTables.value.length)
        console.log('%c  databaseTables内容:', 'color: #67c23a;', databaseTables.value)
      } else {
        console.warn('%c  ⚠️ metadata中没有tableStats字段或为空数组', 'color: #e6a23c;')
      }

    } else {
      console.warn('[数据库详情] 未找到元数据，可能需要触发采集')
      console.warn('  metadataResponse.code:', metadataResponse.code)
      console.warn('  metadataResponse.data:', metadataResponse.data)

      // 显示提示信息
      databaseInfo.value = {
        version: '-',
        connectionCount: 0,
        databaseName: '-',
        charset: '-',
        collation: '-',
        url: '-'
      }
    }

    // 2. 如果元数据中没有表统计，单独获取表列表
    if (!databaseTables.value || databaseTables.value.length === 0) {
      console.log('%c步骤2: 调用getTables API（备用方案）', 'color: #e6a23c;')
      console.log('%c  probeKey:', 'color: #67c23a;', currentProbe.value.probeKey)
      console.log('%c  databaseName:', 'color: #67c23a;', currentInstance.value?.databaseName)

      // 获取当前数据库名用于过滤表数据
      // 使用 databaseName 而不是 id，因为后端需要真实的数据库名来过滤
      const databaseName = currentInstance.value?.databaseName

      const tablesResponse = await databaseProbeApi.getTables(currentProbe.value.probeKey, {
        pageNum: 1,
        pageSize: 100,
        instanceId: databaseName  // 传递真实的数据库名用于过滤
      })

      console.log('%c  getTables API响应:', 'color: #e6a23c;', tablesResponse)

      if (tablesResponse.code === 200 && tablesResponse.data) {
        console.log('%c  ✓ getTables.code = 200', 'color: #67c23a;')
        console.log('%c  tablesResponse.data:', 'color: #67c23a;', tablesResponse.data)
        console.log('%c  records数量:', 'color: #67c23a;', tablesResponse.data.records?.length || 0)

        const records = tablesResponse.data.records || []
        console.log('%c  records内容:', 'color: #67c23a;', records)

        databaseTables.value = records.map(table => ({
          tableName: table.tableName || '-',
          rowsCount: table.rowCount || 0,
          totalSize: table.dataSize || 0,
          indexesCount: table.indexSize || 0
        }))
        console.log('%c  databaseTables已更新，数量:', 'color: #67c23a;', databaseTables.value.length)
        console.log('%c  更新后的databaseTables:', 'color: #67c23a;', databaseTables.value)
      } else {
        console.error('%c  ✗ getTables API返回错误:', 'color: #f56c6c; font-weight: bold', tablesResponse)
      }
    }


    console.log('%c✓ fetchDatabaseTables 完成', 'color: #67c23a; font-weight: bold')
    console.log('%c  最终databaseTables数量:', 'color: #67c23a;', databaseTables.value.length)
    console.log('%c=======================================================', 'color: #409eff; font-weight: bold')
  } catch (error) {
    console.error('%c✗ [数据库详情] 获取失败:', 'color: #f56c6c; font-weight: bold', error)
    console.error('获取数据库详情失败:', error)
  } finally {
    databaseLoading.value = false
  }

}

// 查看表数据
const handleViewTableData = async (table) => {
  currentTableName.value = table.tableName
  tableDataVisible.value = true
  tableDataPageNum.value = 1
  await fetchTableData()
}

// 获取表数据
const fetchTableData = async () => {
  if (!currentProbe.value?.probeKey || !currentTableName.value) return

  tableDataLoading.value = true
  try {
    // 构建请求参数
    const params = {
      pageNum: tableDataPageNum.value,
      pageSize: tableDataPageSize.value
    }

	    // ⭐ 关键修复：添加 databaseName 参数
	    if (currentInstance.value?.databaseName) {
	      params.databaseName = currentInstance.value.databaseName;
	      console.log("[表数据] 添加 databaseName 参数:", currentInstance.value.databaseName);
	    } else {
	      console.warn("[表数据] ⚠️ 未找到 databaseName，查询可能失败");
	    }

	    console.log("[表数据] 最终请求参数:", params);


    // 如果有搜索条件，添加到请求参数（服务端过滤）
    if (tableDataSearch.value && tableDataSearchColumn.value) {
      params.filters = {
        [tableDataSearchColumn.value]: tableDataSearch.value
      }
      console.log('[表数据] 使用服务端过滤:', params.filters)
    }

    const response = await databaseProbeApi.getTableData(
      currentProbe.value.probeKey,
      currentTableName.value,
      params
    )

    if (response.code === 200 && response.data) {
      tableDataColumns.value = response.data.columns || []
      tableDataRows.value = response.data.rows || []
      tableDataTotal.value = response.data.total || 0
    } else {
      console.error('获取表数据失败:', response)
    }
  } catch (error) {
    console.error('[表数据] 获取失败:', error)
    ElMessage.error('获取表数据失败: ' + (error.message || '未知错误'))
  } finally {
    tableDataLoading.value = false
  }
}

// 刷新表数据
const handleRefreshTableData = async () => {
  // 重置游标分页状态
  nextCursor.value = null
  hasMore.value = true
  await fetchTableData()
}

// 加载更多数据（游标分页）
const handleLoadMore = async () => {
  if (!hasMore.value || loadingMore.value) return

  loadingMore.value = true
  try {
    // 构建请求参数
    const params = {
      pageNum: 1,  // 游标分页不使用pageNum
      pageSize: tableDataPageSize.value,
      useCursorPagination: true,
      cursor: nextCursor.value
    }

    if (orderByColumn.value) {
      params.orderByColumn = orderByColumn.value
    }

    // 如果有搜索条件，保持过滤
    if (tableDataSearch.value && tableDataSearchColumn.value) {
      params.filters = {
        [tableDataSearchColumn.value]: tableDataSearch.value
      }
    }

    console.log('[加载更多] 请求参数:', params)

    const response = await databaseProbeApi.getTableData(
      currentProbe.value.probeKey,
      currentTableName.value,
      params
    )

    if (response.code === 200 && response.data) {
      const newRows = response.data.rows || []

      // 追加新数据
      tableDataRows.value = [...tableDataRows.value, ...newRows]

      // 更新游标状态
      hasMore.value = response.data.hasMore !== undefined ? response.data.hasMore : false
      nextCursor.value = response.data.nextCursor || null

      console.log('[加载更多] 成功加载', newRows.length, '条记录, 剩余:', hasMore.value)
    } else {
      console.error('[加载更多] 失败:', response)
    }
  } catch (error) {
    console.error('[加载更多] 异常:', error)
    ElMessage.error('加载更多失败: ' + (error.message || '未知错误'))
  } finally {
    loadingMore.value = false
  }
}

// 表数据分页大小变化
const handleTableDataSizeChange = async (size) => {
  tableDataPageSize.value = size
  tableDataPageNum.value = 1
  await fetchTableData()
}

// 表数据页码变化
const handleTableDataPageChange = async (page) => {
  // 验证页码范围
  const maxPage = Math.ceil(tableDataTotal.value / tableDataPageSize.value)
  if (page > 10000) {
    ElMessage.warning(`页码不能超过10000（当前数据总页数: ${maxPage || 0}）`)
    return
  }
  if (maxPage > 0 && page > maxPage) {
    ElMessage.warning(`页码超出范围（当前共${maxPage}页，请选择第1-${maxPage}页）`)
    return
  }

  tableDataPageNum.value = page
  await fetchTableData()
}

// 表数据搜索（服务端过滤）
const handleTableDataSearch = async () => {
  // 服务端过滤，需要重新请求
  tableDataPageNum.value = 1
  await fetchTableData()

  if (tableDataSearch.value || tableDataSearchColumn.value) {
    filteredCount.value = tableDataTotal.value
  } else {
    filteredCount.value = null
  }
}

// 重置表数据搜索
const handleTableDataReset = async () => {
  tableDataSearch.value = ''
  tableDataSearchColumn.value = ''
  filteredCount.value = null

  // 重置游标分页状态
  nextCursor.value = null
  hasMore.value = true
  orderByColumn.value = null
  // 重置到第一页并重新加载数据
  tableDataPageNum.value = 1
  await fetchTableData()
}

// 导出表数据
const handleExportTableData = async () => {
  try {
    // 检查数据量
    if (tableDataTotal.value > 100000) {
      ElMessageBox.confirm(
        `该表共有 ${tableDataTotal.value} 条记录，导出可能需要较长时间和较大内存。是否继续？`,
        '大数据量导出警告',
        {
          confirmButtonText: '继续导出',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).then(() => {
        exportAllTableData()
      }).catch(() => {
        // 用户取消
      })
      return
    }

    await exportAllTableData()
  } catch (error) {
    console.error('[导出数据] 失败:', error)
    ElMessage.error('导出数据失败: ' + error.message)
  }
}

// 实际执行导出
const exportAllTableData = async () => {
  exportingData.value = true
  try {
    ElMessage.info('正在获取全部数据，请稍候...')

    // 构建请求参数
    const params = {
      pageNum: 1,
      pageSize: 1000  // 后端限制最大1000
    }

    // 如果有搜索条件，应用到导出
    if (tableDataSearch.value && tableDataSearchColumn.value) {
      params.filters = {
        [tableDataSearchColumn.value]: tableDataSearch.value
      }
      ElMessage.info(`应用过滤条件: ${tableDataSearchColumn.value} LIKE ${tableDataSearch.value}`)
    }

    // 获取所有数据
    const response = await databaseProbeApi.getTableData(
      currentProbe.value.probeKey,
      currentTableName.value,
      params
    )

    if (response.code === 200 && response.data) {
      const allData = response.data.rows || []

      if (allData.length === 0) {
        ElMessage.warning('没有数据可导出')
        return
      }

      ElMessage.success(`获取到 ${allData.length} 条记录，正在导出...`)

      // 转换为CSV格式
      const headers = tableDataColumns.value.map(col => col.name).join(',')
      const rows = allData.map(row => {
        return tableDataColumns.value.map(col => {
          const value = row[col.name]
          if (value == null || value === undefined) return ''
          if (typeof value === 'object') return JSON.stringify(value)
          return String(value).replace(/"/g, '""')  // 转义双引号
        }).join(',')
      })

      const csv = [headers, ...rows].join('\n')

      // 创建Blob并下载
      const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${currentTableName.value}_${new Date().getTime()}.csv`
      link.click()
      URL.revokeObjectURL(url)

      ElMessage.success(`成功导出 ${allData.length} 条记录`)
    } else {
      ElMessage.error('获取数据失败: ' + response.message)
    }
  } catch (error) {
    console.error('[导出数据] 失败:', error)
    ElMessage.error('导出数据失败: ' + error.message)
  } finally {
    exportingData.value = false
  }
}

// 格式化单元格值
const formatCellValue = (value) => {
  if (value === null || value === undefined) {
    return '-'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}


// ========== 监控详情对话框打开 ==========

// 监控 currentProbe 变化，根据探针类型加载相应数据
watch(() => currentProbe.value?.id, async (newId, oldId) => {
  if (newId && newId !== oldId) {
    // 关闭对话框时清空数据
    if (!newId) {
      fileList.value = []
      fileStatistics.value = null
      databaseInfo.value = {}
      databaseTables.value = []
      return
    }

    // 根据探针类型加载相应数据
    if (currentProbe.value.type === 'FILE') {
      fetchFileList()
    } else if (currentProbe.value.type === 'DATABASE') {
      console.log('%c========== [Watch] 检测到DATABASE类型探针，开始加载 ==========', 'color: #409eff; font-weight: bold')
      console.log('%ccurrentProbe.value:', 'color: #67c23a;', currentProbe.value)
      // 先加载数据库实例列表
      await fetchDatabaseInstances()
      // 再加载表数据
      await fetchDatabaseTables()
    } else if (currentProbe.value.type === 'SYSTEM') {
      fetchLatestMetrics(newId)
    }
  }
})

// 监控文件视图模式变化
watch(() => fileViewMode.value, (newMode, oldMode) => {
  if (newMode !== oldMode && currentProbe.value?.type === 'FILE') {
    // 切换视图模式时重新加载文件列表
    console.log('File view mode changed:', oldMode, '->', newMode)
    fetchFileList()
  }
})

// 获取指标值
const getMetricValue = (metricName) => {
  const metric = latestMetrics.value.find(m => m.metricName === metricName)
  return metric ? metric.metricValue : null
}

// 获取指标单位
const getMetricUnit = (metricName) => {
  const metric = latestMetrics.value.find(m => m.metricName === metricName)
  return metric ? metric.unit : ''
}

// 格式化指标显示
const formatMetricValue = (metricName, defaultValue = '-') => {
  const value = getMetricValue(metricName)
  if (value === null || value === undefined) return defaultValue
  const unit = getMetricUnit(metricName)
  return `${value}${unit ? ' ' + unit : ''}`
}

const handleEdit = (row) => {
  currentProbe.value = { ...row } // 编辑模式，传入探针数据
  formDialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除探针【${row.name}】吗？此操作不可恢复！`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // 根据探针类型调用不同的API
    let result
    if (row.type === 'FILE') {
      result = await fileProbeApi.deleteProbe(row.id)
    } else {
      result = await probeApi.delete(row.id)
    }

    // 检查删除结果
    const isSuccess = result && result.code === 200

    if (isSuccess) {
      ElMessage.success('删除成功')
      // 从列表中移除
      const index = tableData.value.findIndex(item => item.id === row.id)
      if (index > -1) {
        tableData.value.splice(index, 1)
        pagination.total--
      }
    } else {
      ElMessage.warning(`删除失败: ${result?.message || '未知错误'}`)
    }

  } catch (error) {
    if (error !== 'cancel') {
      const msg = error?.response?.data?.message || error.message || '删除失败'
      ElMessage.error(msg)
    }
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()


    // 处理 probeKey：如果为空，自动生成
    if (!form.probeKey || form.probeKey.trim() === '') {
      const timestamp = Date.now().toString(36).substring(Math.max(0, Date.now().toString(36).length - 6))
      const random = Math.random().toString(36).substring(2, 5)
      const suffix = form.type ? form.type.toLowerCase() : 'probe'
      form.probeKey = `AGENT-${suffix}-${timestamp}-${random}`
    }

    submitLoading.value = true

    // 处理 hostIp：如果为空，使用默认值 127.0.0.1
    const finalHostIp = (form.hostIp && form.hostIp.trim) || '127.0.0.1'

    // 根据探针类型调用不同的API
    let result
    if (form.type === 'FILE') {
      if (isEdit.value) {
        result = await fileProbeApi.update(form.id, form)
        ElMessage.success('更新成功')
      } else {
        result = await fileProbeApi.create(form)
        ElMessage.success('创建成功')
      }
    } else {
      if (isEdit.value) {
        // 构建更新数据对象，只包含必要的字段
        const updateData = {
          id: form.id,
          name: form.name,
          probeKey: form.probeKey,
          type: form.type,
          hostIp: finalHostIp,
          port: form.port,
          collectInterval: form.collectInterval
        }

        // 记录更新前的type
        const originalType = tableData.value.find(p => p.id === form.id)?.type

        result = await probeApi.update(form.id, updateData)

        // 立即查询验证
        const verifyResult = await probeApi.getById(form.id)
        if (verifyResult.code === 200) {
          if (verifyResult.data.type === updateData.type) {
          } else {
            console.error('[更新探针] ❌ 类型更新失败！期望:', updateData.type, '实际:', verifyResult.data.type)
          }
        }

        ElMessage.success('更新成功')
      } else {
        // 构建创建数据对象，只包含必要的字段
        const createData = {
          name: form.name,
          probeKey: form.probeKey,
          type: form.type,
          hostIp: finalHostIp,
          port: form.port,
          collectInterval: form.collectInterval
        }
        result = await probeApi.create(createData)
        if (result && result.code === 200) {
          ElMessage.success('创建成功')
        } else {
          console.error('[创建探针] ❌ 创建失败，返回结果:', result)
          console.error('创建失败:', result?.message)
          throw new Error(result?.message || '创建失败')
        }
      }
    }

    dialogVisible.value = false

    // 更新成功后，强制刷新列表以确保显示最新数据
    if (isEdit.value) {
      await fetchList()
    } else {
      // 创建成功后，重置筛选条件并跳转到第一页，确保新创建的探针可见
      queryForm.name = ''
      queryForm.status = ''
      queryForm.type = ''
      pagination.pageNum = 1
      await fetchList()
    }
  } catch (error) {
    console.error('[创建探针] 创建失败:', error)
    if (error !== 'cancel') {
      console.error(isEdit.value ? '更新失败:' : '创建失败:', error)
    }
  } finally {
    submitLoading.value = false
  }
}

// 探针控制相关状态
const controlLoading = ref({})

// 处理探针控制
const handleControl = async (row, command) => {
  const commandMap = {
    start: '启动',
    stop: '停止',
    restart: '重启'
  }

  const commandText = commandMap[command]

  // 统一的离线探针检查 - 所有类型的探针都需要Agent在线才能控制
  if (row.status === 'offline' && command === 'start') {

    // 动态提取Agent代码，并判断是否需要Agent控制
    // 支持格式：
    // - AGENT-database-xxx 或 TEST-AGENT-001-database-xxx（通过Agent管理）
    // - probe-database-xxx（直接管理，不通过Agent）
    const shouldUseAgentControl = (probeKey) => {
      if (!probeKey || !probeKey.includes('-')) {
        return false
      }

      const PROBE_TYPES = ['file', 'database', 'system', 'http', 'ping', 'port']
      const parts = probeKey.split('-')

      // 找到探针类型的位置
      for (let i = 1; i < parts.length; i++) {
        const currentPart = parts[i].toLowerCase()
        if (PROBE_TYPES.includes(currentPart)) {
          // 提取agent code（探针类型之前的部分）
          const agentCode = parts.slice(0, i).join('-')
          // 只有当agent code包含"AGENT"时才通过Agent控制
          return agentCode.toUpperCase().includes('AGENT')
        }
      }

      return false
    }

    const extractAgentCode = (probeKey) => {
      if (!probeKey || !probeKey.includes('-')) {
        return null
      }

      const PROBE_TYPES = ['file', 'database', 'system', 'http', 'ping', 'port']
      const parts = probeKey.split('-')

      // 找到探针类型的位置
      for (let i = 1; i < parts.length; i++) {
        const currentPart = parts[i].toLowerCase()
        if (PROBE_TYPES.includes(currentPart)) {
          // 提取agent code（探针类型之前的所有部分）
          const agentCode = parts.slice(0, i).join('-')
          console.log(`[extractAgentCode] probeKey=${probeKey}, 找到探针类型位置=${i}, agentCode=${agentCode}`)
          return agentCode || null
        }
      }

      console.warn(`[extractAgentCode] 无法从probeKey提取Agent代码: ${probeKey}`)
      return null
    }

    // 只有通过Agent管理的探针才检查Agent状态
    if (shouldUseAgentControl(row.probeKey)) {
      const agentCode = extractAgentCode(row.probeKey)
      console.log(`[handleControl] ${commandText}探针 - Agent状态检查: probeKey=${row.probeKey}, extractedAgentCode=${agentCode}`)

      try {
        console.log(`[handleControl] ${commandText}探针 - 调用getAgentStatus API: agentCode=${agentCode}`)
        const statusResult = await probeApi.getAgentStatus(agentCode)
        console.log(`[handleControl] ${commandText}探针 - Agent状态API返回: code=${statusResult.code}, status=${statusResult.data?.status}, message=${statusResult.data?.message}`)

        if (statusResult.code === 200 && statusResult.data) {
          const agentOnline = statusResult.data.status === 'online'

          if (!agentOnline) {
            const message = statusResult.data.message || 'Agent程序离线'
            ElMessageBox.alert(
              `Agent程序【${agentCode}】当前离线，无法${commandText}探针。请确保Agent程序正在运行。\n\n${message}`,
              'Agent离线',
              {
                type: 'warning',
                confirmButtonText: '我知道了'
              }
            )
            return
          }
        }
      } catch (error) {
        console.warn(`[handleControl] ${commandText}探针 - 查询Agent状态失败:`, error)
        // 即使查询失败，也允许尝试启动探针
        try {
          await ElMessageBox.confirm(
            `无法确认Agent程序状态，是否继续${commandText}探针？\n\nprobeKey: ${row.probeKey}`,
            'Agent状态未知',
            {
              type: 'info',
              confirmButtonText: '继续启动',
              cancelButtonText: '取消'
            }
          )
        } catch {
          // 用户取消
          return
        }
      }
    }
  }

  // 设置加载状态
  controlLoading.value[row.probeKey] = true
  console.log(`[${commandText}探针] 开始执行: probeKey=${row.probeKey}, command=${command}`)

    try {
      let result

      // 调用相应的探针控制API
      switch (command) {
        case 'start':
          console.log(`[${commandText}探针] 调用启动API: probeKey=${row.probeKey}`)
          result = await probeApi.startProbe(row.probeKey)
          break
        case 'stop':
          console.log(`[${commandText}探针] 调用停止API: probeKey=${row.probeKey}`)
          result = await probeApi.stopProbe(row.probeKey)
          break
        case 'restart':
          console.log(`[${commandText}探针] 调用重启API: probeKey=${row.probeKey}`)
          result = await probeApi.restartProbe(row.probeKey)
          break
        default:
          throw new Error(`未知的命令: ${command}`)
      }

      console.log(`[${commandText}探针] API响应:`, result)

      if (result.code === 200) {
        console.log(`[${commandText}探针] ✅ 成功: probeKey=${row.probeKey}`)
        ElMessage.success(`${commandText}探针成功`)
        // 刷新列表以更新状态
        await fetchList()
      } else {
        console.warn(`[${commandText}探针] ❌ 失败: probeKey=${row.probeKey}, code=${result.code}, message=${result.message}`)
        console.error(`${commandText}探针失败:`, error)
      }
    } catch (error) {
      console.error(`[${commandText}探针] ❌ 异常: probeKey=${row.probeKey}`, error)
      console.error(`${commandText}探针失败:`, error)
    } finally {
      // 清除加载状态
      controlLoading.value[row.probeKey] = false
      console.log(`[${commandText}探针] 执行完成: probeKey=${row.probeKey}`)
    }
}


/**
 * 从探针key中提取Agent代码
 * 例如：AGENT-database-xyz -> AGENT
 *       AGENT-file-abc -> AGENT
 *       AGENT-system-123 -> AGENT
 *
 * @param {string} probeKey - 探针标识
 * @returns {string|null} - Agent代码，如果无法提取则返回null
 */
// 注释：extractAgentCodeFromProbeKey 函数已废弃
// 原因：Agent上报心跳时使用的code是固定的 "AGENT"（从agent配置读取）
// 而不是从探针key中提取的 "AGENT-database" 或 "AGENT-file"
// 现在直接使用主Agent代码 "AGENT" 进行检查

// 处理更多操作下拉菜单命令
const handleMoreAction = (command, row) => {
  switch (command) {
    case 'delete':
      handleDelete(row)
      break
  }
}

const handleSizeChange = (val) => {
  pagination.pageSize = val
  pagination.pageNum = 1
  fetchList()
}

const handleCurrentChange = (val) => {
  pagination.pageNum = val
  fetchList()
}

const handleSortChange = ({ prop, order }) => {
  // 实现排序逻辑
  // TODO: 实现排序功能
  console.log('排序字段:', prop, '排序方向:', order)
}

// 生命周期
onMounted(() => {
  // 从URL参数获取初始值（如果有）
  const urlParams = new URLSearchParams(window.location.search)
  const urlType = urlParams.get('type')
  const urlName = urlParams.get('name')
  const urlStatus = urlParams.get('status')

  if (urlType) queryForm.type = urlType
  if (urlName) queryForm.name = urlName
  if (urlStatus) queryForm.status = urlStatus

  // 强制重置queryForm，确保没有任何意外的值
  if (!urlType) queryForm.type = ''
  if (!urlName) queryForm.name = ''
  if (!urlStatus) queryForm.status = ''
  pagination.pageNum = 1
  pagination.pageSize = 10


  // 稍微延迟，确保Vue的响应式系统已经更新
  nextTick(() => {
    fetchList()
  })
})

// 监听对话框打开/关闭，优化数据加载
// 采用请求式更新策略：只在打开时加载一次，刷新按钮手动更新
watch(detailVisible, (newVal, oldVal) => {
  // 对话框关闭时，清空旧的监控数据，确保下次打开显示最新数据
  if (!newVal && oldVal && latestMetrics.value.length > 0) {
    latestMetrics.value = []
  }
})
</script>

<style scoped lang="scss">
.probe-list-container {
  padding: 0;
  // 使用现代网格系统优化间距
  --spacing-xs: 8px;
  --spacing-sm: 12px;
  --spacing-md: 16px;
  --spacing-lg: 20px;
  --spacing-xl: 24px;

  // 优化颜色系统，提升对比度
  --text-primary: #1f2937;
  --text-secondary: #6b7280;
  --text-tertiary: #9ca3af;
  --border-color: #e5e7eb;
  --bg-card: #ffffff;
  --bg-hover: #f9fafb;
  --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
  --shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);

  // 添加微妙的渐变背景
  background: linear-gradient(135deg, #ffffff 0%, #f9fafb 100%);

  .page-title {
    display: flex;
    align-items: center;
    font-size: 18px;
    font-weight: 700;
    color: var(--text-primary);
    letter-spacing: -0.025em;

    .el-icon {
      font-size: 24px;
      margin-right: 10px;
      color: #3b82f6;
      // 添加柔和的图标背景
      padding: 8px;
      background: rgba(59, 130, 246, 0.1);
      border-radius: 8px;
    }
  }

  // 优化按钮组布局
  .page-actions {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
    align-items: center;

    .el-button {
      min-height: 40px;
      padding: 0 20px;
      font-weight: 500;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

      &:hover {
        transform: translateY(-1px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      }

      &:active {
        transform: translateY(0);
      }

      &.touch-target {
        min-width: 44px;
        min-height: 44px;
      }

      .el-icon {
        margin-right: 6px;
        font-size: 16px;
      }
    }

    .el-divider--vertical {
      height: 32px;
      margin: 0 8px;
    }

    @media (max-width: 768px) {
      width: 100%;
      flex-direction: column;
      align-items: stretch;

      .el-divider--vertical {
        display: none;
      }

      .el-button {
        width: 100%;
      }

      .el-button-group {
        width: 100%;
        display: flex;

        .el-button {
          flex: 1;
        }
      }
    }
  }
}

.search-card {
  margin-bottom: var(--spacing-md);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);

  // 优化卡片内边距，提升呼吸感
  :deep(.el-card__body) {
    padding: var(--spacing-md) var(--spacing-lg);
  }

  // 添加悬停效果
  &:hover {
    box-shadow: var(--shadow-md);
    transition: box-shadow 0.2s ease;
  }
}

.search-form {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  align-items: center;

  :deep(.el-form-item) {
    margin-bottom: 0;
    margin-right: var(--spacing-md);
  }

  // 优化输入框样式
  :deep(.el-input__wrapper) {
    border-radius: 8px;
    transition: all 0.2s ease;

    &:hover {
      box-shadow: 0 0 0 1px #3b82f6 inset;
    }
  }

  :deep(.el-select .el-input__wrapper) {
    border-radius: 8px;
  }
}

.table-card {
  margin-bottom: var(--spacing-md);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
  overflow: hidden; // 防止表格圆角溢出

  // 优化表格容器padding
  :deep(.el-card__body) {
    padding: var(--spacing-lg);
  }

  // 表格底部间距优化
  :deep(.el-table) {
    margin-bottom: var(--spacing-lg);
    border-radius: 8px;
    overflow: hidden;
  }

  // 分页组件优化
  :deep(.el-pagination) {
    padding-top: var(--spacing-md);
    display: flex;
    justify-content: flex-end;
    gap: var(--spacing-sm);
  }

  // 响应式表格容器 - 防止移动端溢出
  @media (max-width: 768px) {
    :deep(.el-card__body) {
      padding: var(--spacing-sm);
    }

    // 添加水平滚动容器
    .table-responsive-wrapper {
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
      margin: 0 calc(-1 * var(--spacing-sm));
      padding: 0 var(--spacing-sm);

      &::-webkit-scrollbar {
        height: 6px;
      }

      &::-webkit-scrollbar-thumb {
        background: #d1d5db;
        border-radius: 3px;

        &:hover {
          background: #9ca3af;
        }
      }
    }
  }
}

.probe-name {
  display: flex;
  align-items: center;
  min-width: 0; /* 允许flex子元素收缩 */
  width: 100%;

  .type-icon {
    font-size: 18px;
    margin-right: 6px;
    flex-shrink: 0; /* 图标不收缩 */

    &.type-system {
      color: #409eff;
    }

    &.type-application {
      color: #67c23a;
    }

    &.type-network {
      color: #e6a23c;
    }

    &.type-custom {
      color: #909399;
    }

    &.type-file {
      color: #f56c6c;
    }
  }

  .name-text {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    display: inline-block;
    max-width: 100%;
  }
}

.probe-key-tag {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;

  :deep(.el-tag__content) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    display: inline-block;
    max-width: 100%;
  }
}

.status-icon {
  margin-right: 6px;
  vertical-align: middle;
  display: inline-flex;
  align-items: center;
}

/* 状态图标颜色 - 使用!important确保覆盖Element Plus默认样式 */
.status-icon-online {
  color: #10b981 !important;  /* 更鲜艳的绿色 - 在线 */
  font-size: 16px;
  font-weight: bold;
  filter: drop-shadow(0 1px 2px rgba(16, 185, 129, 0.3));
}

.status-icon-offline {
  color: #6b7280 !important;  /* 更深的中性灰 - 离线 */
  font-size: 16px;
  opacity: 0.9;
}

.status-icon-error {
  color: #ef4444 !important;  /* 更鲜艳的红色 - 异常 */
  font-size: 16px;
  filter: drop-shadow(0 1px 2px rgba(239, 68, 68, 0.3));
}

.heartbeat-time {
  font-size: 13px;
  color: #606266;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

/* 操作按钮布局优化 - 并排图标按钮 */
.action-buttons-inline {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  padding: 4px 0;
}

/* 紧凑型操作按钮布局 - 优化宽度协调性 */
.action-buttons-compact {
  display: flex;
  align-items: center;
  gap: 2px;  // 减少按钮间距从4px到2px
  justify-content: center;
  padding: 2px 0;  // 减少垂直padding
  margin: 0 auto;  // 居中对齐
  width: fit-content;  // 宽度自适应内容
}

.action-buttons-compact .el-button.is-link {
  padding: 4px 6px;  // 优化padding，更紧凑
  height: 32px;  // 固定高度
  width: 32px;  // 固定宽度，确保按钮大小一致
  min-width: 32px;  // 最小宽度
  border-radius: 6px;  // 圆角
  transition: all 0.2s ease;  // 平滑过渡
  display: inline-flex;  // flex布局
  align-items: center;  // 垂直居中
  justify-content: center;  // 水平居中
}

.action-buttons-compact .el-button.is-link .el-icon {
  font-size: 15px;  // 稍微减小图标尺寸
  display: block;  // 块级显示
}

// 悬停效果 - 更明显
.action-buttons-compact .el-button.is-link:hover {
  transform: scale(1.1);  // 轻微放大
  background-color: rgba(59, 130, 246, 0.1);  // 添加背景色
}

// 危险按钮悬停效果
.action-buttons-compact .el-button.is-link.is-danger:hover {
  background-color: rgba(239, 68, 68, 0.1);  // 红色背景
}

// 成功/主色按钮悬停效果
.action-buttons-compact .el-button.is-link:not(.is-danger):hover {
  background-color: rgba(16, 185, 129, 0.1);  // 绿色背景
}

.control-buttons-group {
  display: inline-flex;
  align-items: center;
  margin-left: 2px;
  margin-right: 2px;
}

.action-btn-inline {
  width: 34px !important;
  height: 34px !important;
  min-width: 34px !important;
  padding: 0 !important;
  border-radius: 6px;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
}

.action-btn-inline .el-icon {
  font-size: 14px;
}

/* 悬停效果 */
.action-btn-inline:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.action-btn-inline:active {
  transform: translateY(0);
}

/* 按钮颜色变体 */
.action-btn-inline.action-btn-info {
  color: var(--el-color-info);
  border-color: var(--el-color-info);
}

.action-btn-inline.action-btn-info:hover {
  background-color: var(--el-color-info);
  color: #fff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.4);
}

.action-btn-inline.action-btn-success {
  color: var(--el-color-success);
  border-color: var(--el-color-success);
}

.action-btn-inline.action-btn-success:hover {
  background-color: var(--el-color-success);
  color: #fff;
  box-shadow: 0 2px 8px rgba(103, 194, 58, 0.4);
}

.action-btn-inline.action-btn-warning {
  color: var(--el-color-warning);
  border-color: var(--el-color-warning);
}

.action-btn-inline.action-btn-warning:hover {
  background-color: var(--el-color-warning);
  color: #fff;
  box-shadow: 0 2px 8px rgba(230, 162, 60, 0.4);
}

.action-btn-inline.action-btn-danger {
  color: var(--el-color-danger);
  border-color: var(--el-color-danger);
}

.action-btn-inline.action-btn-danger:hover {
  background-color: var(--el-color-danger);
  color: #fff;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.4);
}

/* 分隔线 */
.action-divider {
  width: 1px;
  height: 24px;
  background-color: var(--border-color);
  margin: 0 4px;
}

/* 删除操作项特殊样式 - 已移除下拉菜单 */
.action-delete-item {
  /* 保留以备将来使用 */
}

/* 空值样式 */
.text-muted {
  color: var(--text-tertiary);
  font-size: 13px;
}

/* 下拉菜单优化 - 更紧凑协调 */
:deep(.el-dropdown-menu) {
  padding: 6px 0;  // 减少垂直padding
  min-width: 140px;  // 减少最小宽度
  border-radius: 8px;  // 圆角
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);  // 阴影
}

:deep(.el-dropdown-menu__item) {
  padding: 8px 12px;  // 优化padding，更紧凑
  font-size: 13px;
  line-height: 1.5;  // 行高
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.2s ease;  // 平滑过渡
}

:deep(.el-dropdown-menu__item .el-icon) {
  font-size: 14px;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;  // 防止图标收缩
}

:deep(.el-dropdown-menu__item span) {
  flex: 1;  // 文字占据剩余空间
}

:deep(.el-dropdown-menu__item:hover) {
  background-color: #eff6ff;  // 更明显的悬停背景
  transform: translateX(2px);  // 轻微向右移动
}

// 禁用状态样式
:deep(.el-dropdown-menu__item.is-disabled) {
  opacity: 0.5;  // 半透明
  cursor: not-allowed;  // 禁止光标
}

/* 确保图标颜色一致 */
:deep(.el-dropdown-menu__item:hover .el-icon) {
  color: inherit;
}

/* 分隔线样式 */
:deep(.el-dropdown-menu__item--divided) {
  margin-top: 4px;
}

:deep(.el-dropdown-menu__item--divided::before) {
  height: 1px;
  margin: 0;
  background-color: var(--border-color);
}

// 下拉菜单出现动画
:deep(.el-dropdown__popper) {
  &.el-popper[x-placement^="bottom"] {
    .el-popper__arrow::before {
      border-bottom-color: #fff;  // 箭头颜色
    }
  }
}

/* 响应式优化 - 移动优先方法 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--spacing-md);
    padding: var(--spacing-md);

    .page-title {
      font-size: 16px;

      .el-icon {
        font-size: 20px;
      }
    }

    .page-actions {
      width: 100%;
      justify-content: flex-start;

      .el-button-group {
        width: 100%;

        .el-button {
          flex: 1;
        }
      }
    }
  }

  .search-form {
    flex-direction: column;
    align-items: stretch;

    :deep(.el-form-item) {
      width: 100%;
      margin-right: 0;
      margin-bottom: var(--spacing-sm);
    }

    :deep(.el-input),
    :deep(.el-select) {
      width: 100%;
    }
  }

  .action-buttons-inline {
    gap: 4px;
  }

  .action-btn-inline {
    width: 28px !important;
    height: 28px !important;
    min-width: 28px !important;
  }

  .action-btn-inline .el-icon {
    font-size: 12px;
  }

  .action-divider {
    height: 16px;
  }

  /* 表格字体大小自适应 */
  .probe-name .name-text {
    font-size: 13px;
  }

  .probe-key-tag {
    font-size: 12px;
    max-width: 120px;
  }

  .type-icon {
    font-size: 16px !important;
  }

  /* 表格卡片移动端优化 */
  .table-card {
    :deep(.el-card__body) {
      padding: var(--spacing-sm);
    }

    :deep(.el-table) {
      font-size: 13px;

      .el-table__cell {
        padding: 10px 6px;
      }

      // 移动端操作列进一步优化
      .action-column .el-table__cell {
        padding: 8px 4px;  // 更紧凑的padding
        min-width: 120px;  // 移动端最小宽度
      }
    }
  }

  // 移动端操作按钮进一步缩小
  .action-buttons-compact .el-button.is-link {
    width: 28px;  // 减小宽度
    height: 28px;  // 减小高度
    min-width: 28px;
    padding: 3px 4px;

    .el-icon {
      font-size: 13px;  // 减小图标
    }
  }
}

/* 超小屏幕优化 */
@media (max-width: 576px) {
  .probe-list-container {
    --spacing-xs: 6px;
    --spacing-sm: 8px;
    --spacing-md: 10px;
    --spacing-lg: 12px;
    --spacing-xl: 16px;
  }

  .page-header {
    margin-bottom: var(--spacing-sm);
  }

  .probe-name .name-text {
    font-size: 12px;
  }

  .probe-key-tag {
    font-size: 11px;
    max-width: 100px;
  }

  .type-icon {
    font-size: 14px !important;
    margin-right: 4px !important;
  }

  // 指标卡片在小屏幕上全宽显示
  .metric-card-compact {
    margin-bottom: var(--spacing-sm);
  }
}

/* 中等屏幕优化 */
@media (min-width: 769px) and (max-width: 1024px) {
  .probe-name .name-text {
    font-size: 14px;
  }

  .probe-key-tag {
    font-size: 13px;
  }

  // 优化表格列宽
  .table-card {
    :deep(.el-table) {
      .el-table__cell {
        padding: 12px 8px;
      }
    }
  }
}

/* 大屏幕优化 */
@media (min-width: 1440px) {
  .probe-list-container {
    // 在大屏幕上使用卡片式布局
    .table-card {
      :deep(.el-card__body) {
        padding: var(--spacing-xl);
      }
    }

    // 提升表格可读性
    :deep(.el-table) {
      font-size: 15px;

      .el-table__cell {
        padding: 16px 12px;
      }
    }
  }
}

.dialog-form {
  :deep(.el-slider) {
    .el-slider__runway {
      background-color: #e4e7ed;
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

// ========== 详情对话框样式优化 ==========
:deep(.probe-detail-dialog) {
  .el-dialog__header {
    padding: var(--spacing-lg) var(--spacing-xl);
    border-bottom: 1px solid var(--border-color);
    background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
    margin: 0;
  }

  .el-dialog__title {
    font-size: 18px;
    font-weight: 700;
    color: var(--text-primary);
  }

  .el-dialog__body {
    padding: var(--spacing-lg) var(--spacing-xl);
    overflow: visible;
  }

  .el-dialog__footer {
    padding: var(--spacing-md) var(--spacing-xl);
    border-top: 1px solid var(--border-color);
  }

  // 响应式优化
  @media (max-width: 768px) {
    width: 95% !important;

    .el-dialog__header {
      padding: var(--spacing-md);
    }

    .el-dialog__body {
      padding: var(--spacing-md);
    }

    .el-dialog__footer {
      padding: var(--spacing-sm) var(--spacing-md);
    }
  }
}

// ========== 详情对话框 Tabs 样式 ==========
:deep(.probe-detail-tabs) {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);

  .el-tabs__header {
    background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
    margin: 0;
    border-bottom: 1px solid var(--border-color);
  }

  .el-tabs__nav-wrap {
    padding: 0 var(--spacing-md);
  }

  .el-tabs__item {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-secondary);
    padding: 0 var(--spacing-md);
    height: 48px;
    line-height: 48px;
    border-bottom: 2px solid transparent;

    &:hover {
      color: var(--text-primary);
    }

    &.is-active {
      color: #3b82f6;
      border-bottom-color: #3b82f6;
    }
  }

  .el-tabs__content {
    padding: var(--spacing-lg);
  }

  // 移动端优化
  @media (max-width: 768px) {
    .el-tabs__nav-wrap {
      padding: 0 var(--spacing-sm);
    }

    .el-tabs__item {
      font-size: 13px;
      padding: 0 var(--spacing-sm);
      height: 44px;
      line-height: 44px;
    }

    .el-tabs__content {
      padding: var(--spacing-md);
    }
  }
}

// ========== 移动端详情对话框特殊优化 ==========
@media (max-width: 768px) {
  :deep(.probe-detail-dialog) {
    // 进一步减小对话框宽度
    .el-dialog {
      width: 95% !important;
      margin: 0 auto;
    }

    // 优化descriptions在移动端的显示
    .probe-basic-info {
      .el-descriptions {
        .el-descriptions__body {
          .el-descriptions__table {
            .el-descriptions__cell {
              display: block;  // 移动端改为块级显示
              padding: 8px 0;
            }

            .el-descriptions__label {
              display: block;
              width: 100%;
              margin-bottom: 4px;
              text-align: left;
            }

            .el-descriptions__content {
              display: block;
              width: 100%;
              padding-left: 0;
            }
          }
        }
      }
    }

    // 优化卡片在移动端的间距
    .database-info-card,
    .database-tables-card,
    .file-stats-card,
    .file-list-card {
      &:not(:first-child) {
        margin-top: var(--spacing-sm);
      }
    }

    // 优化统计项在移动端的显示
    .file-stats-card {
      .stat-item {
        padding: var(--spacing-sm);
      }

      .stat-value {
        font-size: 24px;  // 移动端减小字号
      }
    }
  }
}

// ========== 超小屏幕优化 (< 576px) ==========
@media (max-width: 576px) {
  :deep(.probe-detail-dialog) {
    .el-dialog__body {
      padding: var(--spacing-sm);
    }

    .probe-basic-info {
      .el-descriptions__cell {
        padding: 8px 0;
      }
    }
  }
}

// ========== 基本信息 Descriptions 样式 ==========
:deep(.probe-basic-info) {
  .el-descriptions__label {
    font-weight: 600;
    color: var(--text-secondary);
    font-size: 14px;
  }

  .el-descriptions__content {
    color: var(--text-primary);
    font-weight: 500;
    font-size: 14px;
  }

  .el-descriptions__cell {
    padding: 12px 16px;
  }

  .el-tag {
    font-weight: 600;
    font-size: 12px;
  }

  // 响应式优化
  @media (max-width: 768px) {
    .el-descriptions__label {
      font-size: 13px;
    }

    .el-descriptions__content {
      font-size: 13px;
    }

    .el-descriptions__cell {
      padding: 10px 12px;
    }
  }
}

// ========== 详情对话框中卡片的统一样式和过渡效果 ==========
.database-info-card,
.database-tables-card,
.file-stats-card,
.file-list-card {
  // 确保对话框内的卡片有适当的间距
  &:not(:first-child) {
    margin-top: var(--spacing-md);
  }

  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    box-shadow: var(--shadow-lg);
    transform: translateY(-2px);
  }
}

// ========== 详情对话框内边距和布局优化 ==========
.metrics-wrapper,
.file-browser-wrapper,
.database-detail-wrapper {
  // 优化容器内边距
  padding: 0;
  margin: 0;

  // 第一个子元素不需要上边距
  > *:first-child {
    margin-top: 0;
  }

  // 所有直接子元素之间保持一致间距
  > * {
    margin-top: var(--spacing-lg);
  }
}

// ========== 对话框空状态优化 ==========
:deep(.el-empty) {
  padding: var(--spacing-xl) 0;

  .el-empty__description {
    color: var(--text-secondary);
    font-size: 14px;
    font-weight: 500;
  }

  .el-empty__image {
    svg {
      fill: var(--text-tertiary);
      opacity: 0.6;
    }
  }
}

:deep(.el-table) {
  // 错误行样式 - 更柔和的背景
  .error-row {
    background-color: #fef2f2 !important;
    border-left: 3px solid #ef4444;

    &:hover {
      background-color: #fee2e2 !important;
    }
  }

  // 离线行样式 - 更柔和的背景
  .offline-row {
    background-color: #f9fafb !important;
    border-left: 3px solid #6b7280;

    &:hover {
      background-color: #f3f4f6 !important;
    }
  }

  // 表格行优化
  .el-table__row {
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

    // 添加微妙的边框效果
    border-bottom: 1px solid var(--border-color);
  }

  // 悬停效果 - 更明显且平滑
  .el-table__row:hover {
    background-color: #eff6ff !important;
    box-shadow: 0 2px 8px rgba(59, 130, 246, 0.05);
  }

  // 操作列光标保持默认
  .action-column {
    cursor: default;

    // 优化操作列单元格样式
    .el-table__cell {
      padding: 12px 8px;  // 减少水平padding
      min-width: 140px;  // 确保最小宽度
    }
  }

  // 表格单元格内边距优化
  .el-table__cell {
    padding: 16px 12px;
    font-size: 14px;
  }

  // 表头优化
  th.el-table__cell {
    padding: 16px 12px;
    background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
    font-weight: 700;
    color: var(--text-primary);
    cursor: default;
    border-bottom: 2px solid var(--border-color);
    letter-spacing: -0.01em;
  }

  // 表格行过渡动画
  .el-table__body tr {
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  }

  // 表格容器圆角
  .el-table__body-wrapper {
    border-radius: 0 0 12px 12px;
  }

  .el-table__header-wrapper {
    border-radius: 12px 12px 0 0;
  }

  // 空状态优化
  .el-table__empty-block {
    padding: 40px 0;

    .el-table__empty-text {
      color: var(--text-secondary);
      font-size: 15px;
      font-weight: 500;
    }
  }

  // 加载状态优化
  .el-loading-mask {
    background-color: rgba(255, 255, 255, 0.85);
    backdrop-filter: blur(4px);

    .el-loading-spinner {
      .circular {
        stroke: #3b82f6;
      }

      .el-loading-text {
        color: #3b82f6;
        font-weight: 600;
        margin-top: 12px;
      }
    }
  }

  // Tooltip样式优化
  .el-tooltip__popper {
    &.is-dark {
      background-color: rgba(0, 0, 0, 0.85);
      backdrop-filter: blur(8px);
      border-radius: 6px;
      padding: 6px 10px;
      font-size: 12px;
      font-weight: 500;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
    }
  }

  // 无障碍访问 - 焦点样式
  .el-table__row:focus-visible {
    outline: 2px solid #3b82f6;
    outline-offset: -2px;
  }
}

// 响应式表格优化
@media (max-width: 768px) {
  :deep(.el-table) {
    .el-table__cell {
      padding: 12px 8px;
      font-size: 13px;
    }

    th.el-table__cell {
      padding: 12px 8px;
      font-size: 12px;
    }

    // 移动端操作列优化
    .action-column {
      min-width: 120px;  // 移动端操作列宽度

      .el-table__cell {
        padding: 8px 4px;
      }
    }
  }

  // 移动端操作按钮进一步缩小
  .action-buttons-compact {
    gap: 1px;  // 进一步减少间距
    padding: 1px 0;

    .el-button.is-link {
      width: 26px;  // 更小
      height: 26px;
      min-width: 26px;
      padding: 2px 3px;

      .el-icon {
        font-size: 12px;
      }
    }
  }

  // 移动端下拉菜单优化
  :deep(.el-dropdown-menu) {
    min-width: 120px;

    .el-dropdown-menu__item {
      padding: 6px 10px;
      font-size: 12px;

      .el-icon {
        font-size: 12px;
        width: 14px;
        height: 14px;
      }
    }
  }
}

// 超小屏幕（< 576px）进一步优化
@media (max-width: 576px) {
  :deep(.el-table) {
    .action-column {
      min-width: 110px;  // 超小屏幕操作列宽度

      .el-table__cell {
        padding: 6px 2px;
      }
    }
  }

  .action-buttons-compact {
    .el-button.is-link {
      width: 24px;  // 最小尺寸
      height: 24px;
      min-width: 24px;
      padding: 2px;

      .el-icon {
        font-size: 11px;
      }
    }
  }
}

// 系统资源样式 - 紧凑型适配屏幕
.metrics-wrapper {
  // 自适应容器
}

// 操作栏 - 统一样式
.metrics-action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
  margin-bottom: var(--spacing-md);

  .metrics-title {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);

    .title-icon {
      font-size: 18px;
      color: #3b82f6;
      padding: 8px;
      background: rgba(59, 130, 246, 0.1);
      border-radius: 8px;
    }

    .probe-name {
      font-size: 14px;
      font-weight: 500;
      color: var(--text-secondary);
    }
  }

  .refresh-action-btn {
    min-width: 100px;
    font-weight: 600;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
    }

    &:active {
      transform: translateY(0);
    }
  }

  // 移动端优化
  @media (max-width: 768px) {
    flex-direction: column;
    gap: var(--spacing-sm);
    padding: var(--spacing-sm) var(--spacing-md);

    .metrics-title {
      font-size: 14px;

      .title-icon {
        font-size: 16px;
      }
    }

    .refresh-action-btn {
      width: 100%;
    }
  }
}

// 空状态优化
.metrics-empty-state {
  padding: 60px 0;
}

// 网格容器 - 优化响应式布局
.metrics-grid-container {
  padding: var(--spacing-md) 0;
  margin: 0 calc(-1 * var(--spacing-md));  // 抵消container padding
  width: calc(100% + 2 * var(--spacing-md));  // 补偿宽度

  @media (max-width: 768px) {
    padding: var(--spacing-sm) 0;
    margin: 0 calc(-1 * var(--spacing-sm));
    width: calc(100% + 2 * var(--spacing-sm));
  }
}

.metrics-grid-row {
  margin: 0 !important;

  // 优化列间距
  :deep(.el-col) {
    margin-bottom: var(--spacing-md);
  }
}

// 移动端指标卡片优化
@media (max-width: 768px) {
  .metrics-grid-row {
    :deep(.el-col) {
      margin-bottom: var(--spacing-sm);
    }
  }

  .metric-card-compact {
    margin-bottom: var(--spacing-sm);
  }
}

.metrics-content {
  padding: 8px;
}

.metrics-grid {
  margin: 0 !important;
}

// 紧凑型卡片 - 现代化优化版
.metric-card-compact {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: var(--spacing-md);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  height: 100%;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  position: relative;
  overflow: hidden;

  // 添加微妙的渐变背景
  background: linear-gradient(135deg, #ffffff 0%, #f9fafb 100%);

  // 顶部装饰条
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: linear-gradient(90deg, #3b82f6 0%, #60a5fa 100%);
    opacity: 0;
    transition: opacity 0.3s ease;
  }

  // 悬停效果 - 更优雅的动画
  &:hover {
    box-shadow: 0 10px 25px -5px rgba(59, 130, 246, 0.15), 0 8px 10px -6px rgba(59, 130, 246, 0.1);
    border-color: #3b82f6;
    transform: translateY(-4px) scale(1.02);

    &::before {
      opacity: 1;
    }

    .metric-icon {
      transform: scale(1.1) rotate(5deg);
    }
  }

  &:active {
    transform: translateY(-2px) scale(1.01);
    box-shadow: 0 4px 12px -3px rgba(59, 130, 246, 0.15);
  }

  // 焦点样式 - 无障碍访问
  &:focus-visible {
    outline: 2px solid #3b82f6;
    outline-offset: 2px;
  }
}

// 卡片类型特定颜色 - 现代化调色板
.metric-card-cpu {
  border-left: 4px solid #3b82f6;

  .metric-icon {
    color: #3b82f6;
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.1) 0%, rgba(59, 130, 246, 0.05) 100%);
  }

  .metric-value-small {
    color: #3b82f6;
    text-shadow: 0 2px 4px rgba(59, 130, 246, 0.1);
  }

  &:hover {
    border-left-color: #2563eb;
  }
}

.metric-card-memory {
  border-left: 4px solid #10b981;

  .metric-icon {
    color: #10b981;
    background: linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(16, 185, 129, 0.05) 100%);
  }

  .metric-value-small {
    color: #10b981;
    text-shadow: 0 2px 4px rgba(16, 185, 129, 0.1);
  }

  &:hover {
    border-left-color: #059669;
  }
}

.metric-card-disk {
  border-left: 4px solid #f59e0b;

  .metric-icon {
    color: #f59e0b;
    background: linear-gradient(135deg, rgba(245, 158, 11, 0.1) 0%, rgba(245, 158, 11, 0.05) 100%);
  }

  .metric-value-small {
    color: #f59e0b;
    text-shadow: 0 2px 4px rgba(245, 158, 11, 0.1);
  }

  &:hover {
    border-left-color: #d97706;
  }
}

.metric-card-network {
  border-left: 4px solid #8b5cf6;

  .metric-icon {
    color: #8b5cf6;
    background: linear-gradient(135deg, rgba(139, 92, 246, 0.1) 0%, rgba(139, 92, 246, 0.05) 100%);
  }

  &:hover {
    border-left-color: #7c3aed;
  }
}

.metric-card-load {
  border-left: 4px solid #ef4444;

  .metric-icon {
    color: #ef4444;
    background: linear-gradient(135deg, rgba(239, 68, 68, 0.1) 0%, rgba(239, 68, 68, 0.05) 100%);
  }

  .load-value {
    color: #ef4444;
    font-weight: 700;
    text-shadow: 0 2px 4px rgba(239, 68, 68, 0.1);
  }

  &:hover {
    border-left-color: #dc2626;
  }
}

.metric-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;

  .metric-icon {
    font-size: 20px;
    margin-right: 8px;
    padding: 8px;
    border-radius: 8px;
    transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  .metric-title {
    font-size: 14px;
    font-weight: 700;
    color: var(--text-primary);
    letter-spacing: -0.01em;
  }
}

.metric-card-compact:hover .metric-icon {
  transform: scale(1.15) rotate(5deg);
}

.metric-value-small {
  font-size: 28px;
  font-weight: 800;
  margin-bottom: 12px;
  line-height: 1;
  letter-spacing: -0.02em;
  // 添加微妙的文字阴影
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}

.metric-footer {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);

  .metric-label {
    font-size: 12px;
    color: var(--text-secondary);
    font-weight: 500;
    letter-spacing: 0.01em;
  }
}

.metric-value-group-compact {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  justify-content: center;
}

.metric-value-item-compact {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
  border-radius: 8px;
  border: 1px solid var(--border-color);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    background: linear-gradient(135deg, #eff6ff 0%, #ffffff 100%);
    border-color: #3b82f6;
    transform: translateX(4px);
    box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1);
  }

  .label {
    font-size: 13px;
    color: var(--text-secondary);
    font-weight: 600;
  }

  .value {
    font-size: 15px;
    font-weight: 700;
    color: var(--text-primary);
  }
}

// 更新时间提示
.metrics-update-time {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: var(--spacing-sm) var(--spacing-md);
  margin-top: var(--spacing-md);
  font-size: 13px;
  color: var(--text-secondary);
  background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-weight: 500;

  .el-icon {
    font-size: 14px;
    color: #3b82f6;
  }
}

// 保持原有样式兼容
.metrics-container {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  padding: 16px;
}

.metric-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  transition: all 0.3s;

  &:hover {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    border-color: #409eff;
  }
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
  margin-bottom: 12px;
}

.metric-value-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-value-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;

  .label {
    font-size: 13px;
    color: #606266;
  }

  .value {
    font-size: 16px;
    font-weight: 600;
    color: #409eff;
  }
}

// 标签页中的刷新按钮（移除，已不再使用）

// ========== FILE 类型样式 - 现代化优化 ==========
.file-browser-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  padding: 0;
}

.browser-action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);

  .action-title {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);

    .el-icon {
      font-size: 18px;
      color: #3b82f6;
      padding: 8px;
      background: rgba(59, 130, 246, 0.1);
      border-radius: 8px;
    }

    .probe-name {
      color: var(--text-secondary);
      font-size: 14px;
      font-weight: 500;
    }
  }

  .scan-btn {
    min-width: 110px;
    font-weight: 600;
  }
}

.file-stats-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);

  :deep(.el-card__header) {
    background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
    border-bottom: 1px solid var(--border-color);
    padding: var(--spacing-sm) var(--spacing-lg);
    font-weight: 700;
    font-size: 15px;
    color: var(--text-primary);
  }

  :deep(.el-card__body) {
    padding: var(--spacing-lg);
  }

  // 优化统计项布局
  :deep(.el-row) {
    margin: 0 calc(-1 * var(--spacing-sm) / 2) !important;  // 抵消gutter
  }

  .stat-item {
    text-align: center;
    padding: var(--spacing-md);
    background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
    border-radius: 12px;
    border: 1px solid var(--border-color);
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;

    &:hover {
      transform: translateY(-4px);
      box-shadow: var(--shadow-lg);
      border-color: #3b82f6;
    }

    .stat-label {
      font-size: 12px;
      color: var(--text-secondary);
      margin-bottom: var(--spacing-sm);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .stat-value {
      font-size: 32px;
      font-weight: 800;
      color: #3b82f6;
      letter-spacing: -0.02em;
      line-height: 1;
      text-shadow: 0 2px 4px rgba(59, 130, 246, 0.1);
    }
  }
}

.file-list-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
  overflow: hidden;

  :deep(.el-card__header) {
    background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
    border-bottom: 1px solid var(--border-color);
    padding: var(--spacing-sm) var(--spacing-lg);
    font-weight: 700;
    font-size: 15px;
    color: var(--text-primary);
  }

  :deep(.el-card__body) {
    padding: 0;
  }

  .file-name-cell {
    display: flex;
    align-items: center;
    gap: 10px;

    .file-icon {
      font-size: 18px;
      color: #6b7280;
      padding: 6px;
      background: rgba(107, 114, 128, 0.1);
      border-radius: 6px;
    }
  }

  // 文件列表头部
  .file-list-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .view-switcher {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  // 树形视图容器
  .file-tree-container {
    padding: var(--spacing-md);
    min-height: 300px;
    max-height: 600px;
    overflow-y: auto;

    :deep(.el-tree) {
      background: transparent;

      .el-tree-node__content {
        height: 40px;
        padding: 0 8px;
        border-radius: 6px;
        transition: all 0.2s;

        &:hover {
          background: rgba(64, 158, 255, 0.05);
        }
      }

      .el-tree-node.is-current > .el-tree-node__content {
        background: rgba(64, 158, 255, 0.1);
        color: var(--el-color-primary);
      }
    }

    // 自定义树节点样式
    .custom-tree-node {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 14px;
      padding: 0 8px;
      gap: 12px;

      .node-icon {
        color: #f59e0b;
        flex-shrink: 0;
      }

      .node-name {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-weight: 500;
      }

      .node-size {
        font-size: 12px;
        color: var(--text-secondary);
        flex-shrink: 0;
        padding: 2px 8px;
        background: rgba(103, 194, 58, 0.1);
        border-radius: 4px;
      }

      .node-path {
        font-size: 12px;
        color: var(--text-placeholder);
        flex-shrink: 0;
        max-width: 300px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .node-actions {
        display: none;
        gap: 4px;
        flex-shrink: 0;
      }
    }

    // 悬停时显示操作按钮
    :deep(.el-tree-node__content:hover) .custom-tree-node .node-actions {
      display: flex;
    }
  }

  // 分页容器
  .pagination-container {
    padding: var(--spacing-md);
    display: flex;
    justify-content: flex-end;
    border-top: 1px solid var(--border-color);
  }
}

// ========== DATABASE 类型样式 - 现代化优化 ==========
.database-detail-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  padding: 0;
}

.database-action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);

  .action-title {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);

    .el-icon {
      font-size: 18px;
      color: #10b981;
      padding: 8px;
      background: rgba(16, 185, 129, 0.1);
      border-radius: 8px;
    }

    .probe-name {
      color: var(--text-secondary);
      font-size: 14px;
      font-weight: 500;
    }
  }

  .action-buttons {
    display: flex;
    gap: 8px;
  }
}

.database-info-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);

  :deep(.el-card__header) {
    background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
    border-bottom: 1px solid var(--border-color);
    padding: var(--spacing-sm) var(--spacing-lg);
    font-weight: 700;
    font-size: 15px;
    color: var(--text-primary);
  }

  :deep(.el-card__body) {
    padding: var(--spacing-lg);
  }

  // 优化descriptions布局
  :deep(.el-descriptions) {
    .el-descriptions__label {
      font-weight: 600;
      color: var(--text-secondary);
    }

    .el-descriptions__content {
      color: var(--text-primary);
      font-weight: 500;
    }
  }
}

.database-tables-card {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
  overflow: hidden;

  :deep(.el-card__header) {
    background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
    border-bottom: 1px solid var(--border-color);
    padding: var(--spacing-sm) var(--spacing-lg);
    font-weight: 700;
    font-size: 15px;
    color: var(--text-primary);
  }

  :deep(.el-card__body) {
    padding: 0;
  }

  // 优化表格样式
  :deep(.el-table) {
    font-size: 14px;

    .el-table__cell {
      padding: 12px 8px;
    }

    th.el-table__cell {
      background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
      font-weight: 700;
      color: var(--text-primary);
    }
  }
}

// ========== 通用样式增强 ==========
.probe-name {
  color: #909399;
  font-size: 14px;
  font-weight: 400;
  margin-left: 8px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

/* 数据库选择器样式 */
.database-type-selector {
  display: inline-flex;
  align-items: center;
  user-select: none;
  transition: all 0.3s;
  border-radius: 4px;

  &:hover {
    opacity: 0.85;
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(103, 194, 58, 0.3);
  }

  &:active {
    transform: translateY(0);
  }

  .is-loading {
    animation: rotating 1s linear infinite;
  }

  .el-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  // Element Plus tag样式覆盖
  &.el-tag {
    background-color: var(--el-color-success-light-9);
    border-color: var(--el-color-success-light-7);
    color: var(--el-color-success);

    .el-tag__content {
      display: flex;
      align-items: center;
    }
  }
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

// ========== 重新设计的探针详情页样式 ==========
.metrics-wrapper-v2 {
  padding: 0;
}

.metrics-action-bar-v2 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.metrics-title-v2 {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #ffffff;
}

.probe-name-v2 {
  font-size: 18px;
  font-weight: 600;
  display: block;
}

.probe-key-v2 {
  font-size: 12px;
  opacity: 0.85;
  font-family: 'Courier New', monospace;
}

.metrics-complete-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.critical-metrics-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.critical-metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: 2px solid transparent;
  transition: all 0.3s ease;
}

.critical-metric-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.critical-metric-card.metric-warning {
  border-color: #fbbf24;
}

.critical-metric-card.metric-critical {
  border-color: #f87171;
}

.metric-icon-small {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #ffffff;
}

.cpu-icon {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
}

.memory-icon {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.disk-icon {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.network-icon {
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%);
}

.metric-info {
  flex: 1;
}

.metric-label-small {
  font-size: 11px;
  color: #64748b;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.metric-value-large {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1;
  margin-bottom: 8px;
}

.metric-bar-mini {
  width: 100%;
  height: 6px;
  background: #e2e8f0;
  border-radius: 3px;
  overflow: hidden;
}

.metric-bar-fill-mini {
  height: 100%;
  background: linear-gradient(90deg, #10b981 0%, #34d399 100%);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.metric-warning .metric-bar-fill-mini {
  background: linear-gradient(90deg, #f59e0b 0%, #fbbf24 100%);
}

.metric-critical .metric-bar-fill-mini {
  background: linear-gradient(90deg, #ef4444 0%, #f87171 100%);
}

.metric-value-dual {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.network-value {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.network-label {
  font-size: 16px;
  font-weight: 700;
}

.network-label:first-child {
  color: #3b82f6;
}

.network-label:last-child {
  color: #10b981;
}

.detailed-metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.metrics-section-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e2e8f0;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid #f1f5f9;
}

.section-header .el-icon {
  font-size: 20px;
  color: #6366f1;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.metrics-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 8px;
  font-size: 13px;
}

.detail-label {
  color: #64748b;
  font-weight: 500;
}

.detail-value {
  color: #1e293b;
  font-weight: 600;
  text-align: right;
}

.detail-value.error-value {
  color: #dc2626;
  font-weight: 700;
}

.metrics-update-time-v2 {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: #f1f5f9;
  border-radius: 8px;
  font-size: 13px;
  color: #64748b;
  justify-content: center;
}

@media (max-width: 1024px) {
  .critical-metrics-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .detailed-metrics-grid {
    grid-template-columns: 1fr;
  }
}

// 表数据工具栏样式
.table-data-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px;
  background: var(--bg-hover);
  border-radius: 8px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .metrics-action-bar-v2 {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }

  .critical-metrics-row {
    grid-template-columns: 1fr;
  }

  .metrics-detail-grid {
    grid-template-columns: 1fr;
  }

  .table-data-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}

// ===================================
// 开关控件样式优化 - 与设置页面保持一致
// ===================================
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
    border-color: var(--color-primary, #3B82F6) !important;
    box-shadow: 0 0 0 1px var(--color-primary, #3B82F6) !important;
    background-image: none !important;
  }

  .el-switch__action {
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  }

  &:hover:not(.is-disabled) {
    .el-switch__core {
      border-color: var(--color-primary-light, #93C5FD);
    }
  }
}

// 单选框和复选框样式优化
:deep(.el-radio),
:deep(.el-checkbox) {
  background: #ffffff !important;

  &.is-checked {
    background: #ffffff !important;
  }

  &:hover {
    background: var(--bg-secondary, #F1F5F9) !important;
  }
}

:deep(.el-radio__input.is-checked .el-radio__inner) {
  background: var(--color-primary, #3B82F6) !important;
  border-color: var(--color-primary, #3B82F6) !important;
}

:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background: var(--color-primary, #3B82F6) !important;
  border-color: var(--color-primary, #3B82F6) !important;
}
</style>
