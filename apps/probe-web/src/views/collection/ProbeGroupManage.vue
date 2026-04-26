<template>
  <div class="probe-group-manage-container">
    <!-- 页面标题和操作 -->
    <div class="page-header">
      <div class="page-title">
        <el-icon><FolderOpened /></el-icon>
        <span>探针分组管理</span>
      </div>
      <div class="page-actions">
        <el-button type="primary" @click="handleCreateGroup">
          <el-icon><Plus /></el-icon>
          新增分组
        </el-button>
      </div>
    </div>

    <div class="content-container">
      <!-- 左侧分组树 -->
      <el-card class="tree-card" shadow="hover">
        <template #header>
          <span>分组树</span>
        </template>
        <el-tree
          ref="treeRef"
          :data="groupTree"
          :props="treeProps"
          node-key="id"
          default-expand-all
          highlight-current
          @node-click="handleNodeClick"
        >
          <template #default="{ node, data }">
            <span class="custom-tree-node">
              <el-icon><Folder /></el-icon>
              <span class="node-label">{{ node.label }}</span>
            </span>
          </template>
        </el-tree>
      </el-card>

      <!-- 右侧内容区 -->
      <div class="right-content">
        <!-- 当前分组信息 -->
        <el-card class="info-card" shadow="hover" v-if="currentGroup.id">
          <template #header>
            <div class="info-header">
              <span>分组信息</span>
              <div class="info-actions">
                <el-button type="primary" size="small" @click="handleEditGroup">
                  <el-icon><Edit /></el-icon>
                  编辑
                </el-button>
                <el-button type="danger" size="small" @click="handleDeleteGroup">
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </div>
            </div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="分组名称">{{ currentGroup.groupName }}</el-descriptions-item>
            <el-descriptions-item label="分组编码">{{ currentGroup.groupCode }}</el-descriptions-item>
            <el-descriptions-item label="父分组ID">{{ currentGroup.parentId || '无' }}</el-descriptions-item>
            <el-descriptions-item label="排序">{{ currentGroup.sortOrder }}</el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">{{ currentGroup.description || '无' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 分组下的探针列表 -->
        <el-card class="table-card" shadow="hover" v-if="currentGroup.id">
          <template #header>
            <div class="table-header">
              <span>探针列表</span>
              <el-button type="success" size="small" @click="handleAddProbes">
                <el-icon><Plus /></el-icon>
                添加探针
              </el-button>
            </div>
          </template>
          <el-table
            :data="groupProbes"
            style="width: 100%"
            v-loading="loadingProbes"
            border
            stripe
            max-height="400"
          >
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="name" label="探针名称" width="180" />
            <el-table-column prop="probeKey" label="探针标识" width="200" />
            <el-table-column prop="type" label="类型" width="120" align="center">
              <template #default="{ row }">
                <el-tag size="small">{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="hostIp" label="主机IP" width="140" align="center" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'online' ? 'success' : 'info'" size="small">
                  {{ row.status === 'online' ? '在线' : row.status === 'offline' ? '离线' : row.status === 'error' ? '异常' : row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template #default="{ row }">
                <el-button type="danger" size="small" @click="handleRemoveProbe(row)">
                  移除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>

    <!-- 创建/编辑分组对话框 -->
    <el-dialog
      v-model="groupDialogVisible"
      :title="groupDialogTitle"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="groupForm" :rules="groupRules" ref="groupFormRef" label-width="100px">
        <el-form-item label="分组名称" prop="groupName">
          <el-input v-model="groupForm.groupName" placeholder="请输入分组名称" />
        </el-form-item>
        <el-form-item label="分组编码" prop="groupCode">
          <el-input v-model="groupForm.groupCode" placeholder="请输入分组编码" :disabled="isEditGroup" />
        </el-form-item>
        <el-form-item label="父分组" prop="parentId">
          <el-tree-select
            v-model="groupForm.parentId"
            :data="parentGroupOptions"
            :props="{ label: 'groupName', value: 'id' }"
            placeholder="请选择父分组"
            check-strictly
            clearable
          />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="groupForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="groupForm.description" type="textarea" :rows="3" placeholder="请输入分组描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitGroup" :loading="submittingGroup">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加探针对话框 -->
    <el-dialog
      v-model="probeDialogVisible"
      title="添加探针到分组"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="probeForm" ref="probeFormRef" label-width="100px">
        <el-form-item label="选择探针">
          <el-select
            v-model="probeForm.probeIds"
            multiple
            placeholder="请选择要添加的探针"
            style="width: 100%"
          >
            <el-option
              v-for="probe in availableProbes"
              :key="probe.id"
              :label="`${probe.name} (${probe.probeKey})`"
              :value="probe.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="probeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitAddProbes" :loading="addingProbes">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderOpened, Plus, Edit, Delete, Folder } from '@element-plus/icons-vue'
import { getGroupTree, getGroup, createGroup, updateGroup, deleteGroup, getGroupProbes, addProbesToGroup, removeProbeFromGroup } from '@/api/probeGroup'
import { probeApi } from '@/api/probe'

// 响应式数据
const loadingProbes = ref(false)
const groupTree = ref([])
const currentGroup = ref({})
const groupProbes = ref([])
const availableProbes = ref([])

// 树相关
const treeRef = ref(null)
const treeProps = {
  children: 'children',
  label: 'groupName'
}

// 分组对话框相关
const groupDialogVisible = ref(false)
const groupDialogTitle = computed(() => isEditGroup.value ? '编辑分组' : '新增分组')
const isEditGroup = ref(false)
const submittingGroup = ref(false)
const groupFormRef = ref(null)
const groupForm = reactive({
  id: null,
  groupName: '',
  groupCode: '',
  parentId: null,
  sortOrder: 0,
  description: ''
})

const groupRules = {
  groupName: [{ required: true, message: '请输入分组名称', trigger: 'blur' }],
  groupCode: [{ required: true, message: '请输入分组编码', trigger: 'blur' }]
}

const parentGroupOptions = computed(() => {
  const buildTreeOptions = (nodes, level = 0) => {
    let result = []
    nodes.forEach(node => {
      const option = { ...node }
      option.groupName = '  '.repeat(level) + option.groupName
      result.push(option)
      if (node.children && node.children.length > 0) {
        result = result.concat(buildTreeOptions(node.children, level + 1))
      }
    })
    return result
  }
  return [{ id: 0, groupName: '根分组' }, ...buildTreeOptions(groupTree.value)]
})

// 探针对话框相关
const probeDialogVisible = ref(false)
const addingProbes = ref(false)
const probeFormRef = ref(null)
const probeForm = reactive({
  probeIds: []
})

// 获取分组树
const fetchGroupTree = async () => {
  try {
    const { data } = await getGroupTree()
    groupTree.value = data || []
  } catch (error) {
    ElMessage.error('获取分组树失败')
  }
}

// 获取所有可用探针
const fetchAvailableProbes = async () => {
  try {
    const { data } = await probeApi.getList({ pageNum: 1, pageSize: 1000 })
    availableProbes.value = (data.records || []).filter(p => {
      // 过滤掉已在当前分组的探针
      return !groupProbes.value.some(gp => gp.id === p.id)
    })
  } catch (error) {
    ElMessage.error('获取探针列表失败')
  }
}

// 节点点击
const handleNodeClick = async (data) => {
  currentGroup.value = data
  await fetchGroupProbes(data.id)
}

// 获取分组下的探针
const fetchGroupProbes = async (groupId) => {
  loadingProbes.value = true
  try {
    const { data } = await getGroupProbes(groupId)
    groupProbes.value = data || []
  } catch (error) {
    ElMessage.error('获取探针列表失败')
  } finally {
    loadingProbes.value = false
  }
}

// 新增分组
const handleCreateGroup = () => {
  isEditGroup.value = false
  Object.assign(groupForm, {
    id: null,
    groupName: '',
    groupCode: '',
    parentId: 0,
    sortOrder: 0,
    description: ''
  })
  groupDialogVisible.value = true
}

// 编辑分组
const handleEditGroup = () => {
  isEditGroup.value = true
  Object.assign(groupForm, currentGroup.value)
  groupDialogVisible.value = true
}

// 提交分组表单
const handleSubmitGroup = async () => {
  await groupFormRef.value.validate()
  submittingGroup.value = true
  try {
    if (isEditGroup.value) {
      await updateGroup(groupForm.id, groupForm)
      ElMessage.success('更新成功')
    } else {
      await createGroup(groupForm)
      ElMessage.success('创建成功')
    }
    groupDialogVisible.value = false
    fetchGroupTree()
  } catch (error) {
    ElMessage.error(isEditGroup.value ? '更新失败' : '创建失败')
  } finally {
    submittingGroup.value = false
  }
}

// 删除分组
const handleDeleteGroup = () => {
  ElMessageBox.confirm(
    `确定要删除分组"${currentGroup.value.groupName}"吗？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await deleteGroup(currentGroup.value.id)
      ElMessage.success('删除成功')
      currentGroup.value = {}
      groupProbes.value = []
      fetchGroupTree()
    } catch (error) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

// 添加探针
const handleAddProbes = async () => {
  probeForm.probeIds = []
  await fetchAvailableProbes()
  probeDialogVisible.value = true
}

// 提交添加探针
const handleSubmitAddProbes = async () => {
  if (probeForm.probeIds.length === 0) {
    ElMessage.warning('请选择要添加的探针')
    return
  }
  addingProbes.value = true
  try {
    await addProbesToGroup(currentGroup.value.id, probeForm.probeIds)
    ElMessage.success('添加成功')
    probeDialogVisible.value = false
    fetchGroupProbes(currentGroup.value.id)
  } catch (error) {
    ElMessage.error('添加失败')
  } finally {
    addingProbes.value = false
  }
}

// 移除探针
const handleRemoveProbe = (row) => {
  ElMessageBox.confirm(
    `确定要从分组中移除探针"${row.name}"吗？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await removeProbeFromGroup(currentGroup.value.id, row.id)
      ElMessage.success('移除成功')
      fetchGroupProbes(currentGroup.value.id)
    } catch (error) {
      ElMessage.error('移除失败')
    }
  }).catch(() => {})
}

// 初始化
onMounted(() => {
  fetchGroupTree()
})
</script>

<style scoped>
.probe-group-manage-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 20px;
  font-weight: bold;
}

.content-container {
  display: flex;
  gap: 20px;
}

.tree-card {
  width: 280px;
  min-height: 500px;
}

.right-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.info-card {
  margin-bottom: 0;
}

.info-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-actions {
  display: flex;
  gap: 10px;
}

.table-card {
  margin-bottom: 0;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.custom-tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
}

.node-label {
  font-size: 14px;
}
</style>
