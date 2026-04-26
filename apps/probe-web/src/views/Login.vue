<template>
  <div class="login-page" role="main">
    <!-- 跳过动画链接 - 无障碍优化 -->
    <a href="#login-form" class="skip-animation" aria-label="跳过动画，直接登录">
      跳过动画
    </a>

    <!-- 单卡片容器 -->
    <div class="login-card" role="region" aria-label="登录">
      <!-- 左侧动画区域 -->
      <div class="left-section" aria-hidden="true" aria-label="装饰性动画区域">
        <!-- 顶部 Logo -->
        <div class="top-logo">
          <div class="logo-icon">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="currentColor"/>
            </svg>
          </div>
          <span class="logo-text">数据探针管理系统</span>
        </div>

        <!-- 四角色区域 -->
        <div class="characters-container">
        <!-- 橙色半圆形角色 - 最前层 -->
        <div
          ref="orangeRef"
          class="character orange-character"
          :style="orangeStyle"
        >
          <!-- 眼睛 - 只有瞳孔 -->
          <div
            class="eyes orange-eyes"
            :style="orangeEyesStyle"
          >
            <div
              class="pupil orange-pupil"
              :style="getPupilStyle(12, 5, '#2D2D2D', orangePupilPos)"
            />
            <div
              class="pupil orange-pupil"
              :style="getPupilStyle(12, 5, '#2D2D2D', orangePupilPos)"
            />
          </div>
        </div>

        <!-- 黄色矩形角色 - 前层 -->
        <div
          ref="yellowRef"
          class="character yellow-character"
          :style="yellowStyle"
        >
          <!-- 眼睛 - 只有瞳孔 -->
          <div
            class="eyes yellow-eyes"
            :style="yellowEyesStyle"
          >
            <div
              class="pupil yellow-pupil"
              :style="getPupilStyle(12, 5, '#2D2D2D', yellowPupilPos)"
            />
            <div
              class="pupil yellow-pupil"
              :style="getPupilStyle(12, 5, '#2D2D2D', yellowPupilPos)"
            />
          </div>
          <!-- 嘴巴 -->
          <div
            class="mouth yellow-mouth"
            :style="yellowMouthStyle"
          />
        </div>

        <!-- 黑色矩形角色 - 中间层 -->
        <div
          ref="blackRef"
          class="character black-character"
          :style="blackStyle"
        >
          <!-- 眼睛 -->
          <div
            class="eyes black-eyes"
            :style="blackEyesStyle"
          >
            <div
              class="eye-ball"
              :style="getEyeBallStyle(16, 6, 4, 'white', '#2D2D2D', blackLeftEyePos)"
            />
            <div
              class="eye-ball"
              :style="getEyeBallStyle(16, 6, 4, 'white', '#2D2D2D', blackRightEyePos)"
            />
          </div>
        </div>

        <!-- 紫色矩形角色 - 后层 -->
        <div
          ref="purpleRef"
          class="character purple-character"
          :style="purpleStyle"
        >
          <!-- 眼睛 -->
          <div
            class="eyes purple-eyes"
            :style="purpleEyesStyle"
          >
            <div
              class="eye-ball"
              :style="getEyeBallStyle(18, 7, 5, 'white', '#2D2D2D', purpleLeftEyePos)"
            />
            <div
              class="eye-ball"
              :style="getEyeBallStyle(18, 7, 5, 'white', '#2D2D2D', purpleRightEyePos)"
            />
          </div>
        </div>
      </div>
      </div>

      <!-- 右侧登录表单区域 -->
      <div class="right-section" id="login-form" role="region" aria-labelledby="login-title">
      <div class="login-wrapper">
        <!-- 标题 -->
        <div class="header">
          <h1 id="login-title" class="title">欢迎回来!</h1>
          <p class="subtitle">请输入您的登录信息</p>
        </div>

        <!-- 登录表单 -->
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          class="login-form"
          role="form"
          aria-labelledby="login-title"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <label for="username" class="form-label">用户名</label>
            <el-input
              id="username"
              v-model="loginForm.username"
              size="large"
              placeholder="请输入用户名"
              :prefix-icon="User"
              clearable
              autocomplete="username"
              aria-required="true"
              :aria-invalid="!!errors.username"
              :aria-describedby="errors.username ? 'username-error' : undefined"
              @input="handleUsernameFocus"
              @keyup.enter="handleLogin"
            />
            <span v-if="errors.username" id="username-error" class="error-hint" role="alert">
              {{ errors.username }}
            </span>
          </el-form-item>

          <el-form-item prop="password">
            <label for="password" class="form-label">密码</label>
            <el-input
              id="password"
              v-model="loginForm.password"
              type="password"
              size="large"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              clearable
              show-password
              autocomplete="current-password"
              aria-required="true"
              :aria-invalid="!!errors.password"
              :aria-describedby="errors.password ? 'password-error' : undefined"
              @input="handlePasswordInput"
              @keyup.enter="handleLogin"
            />
            <span v-if="errors.password" id="password-error" class="error-hint" role="alert">
              {{ errors.password }}
            </span>
          </el-form-item>

          <el-form-item>
            <div class="form-options">
              <el-checkbox
                v-model="rememberMe"
                :aria-label="'记住登录状态' + (rememberMe ? '，已启用' : '，已禁用')"
                @change="handleRememberMeChange"
              >
                记住我（30天）
              </el-checkbox>
            </div>
          </el-form-item>

          <el-form-item v-if="errorMessage">
            <div
              class="error-message"
              role="alert"
              aria-live="assertive"
            >
              {{ errorMessage }}
            </div>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              :aria-label="loading ? '登录中，请稍候' : '登录系统'"
              class="login-button touch-target"
              @click="handleLogin"
            >
              {{ loading ? '登录中...' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/user'

const router = useRouter()
const loginFormRef = ref()

// Template refs
const purpleRef = ref()
const blackRef = ref()
const yellowRef = ref()
const orangeRef = ref()

// State
const loading = ref(false)
const rememberMe = ref(false)
const errorMessage = ref('')
const errors = ref({
  username: '',
  password: ''
})

// 鼠标位置
const mouseX = ref(0)
const mouseY = ref(0)

// 输入状态
const isTyping = ref(false)

// 眨眼状态
const isPurpleBlinking = ref(false)
const isBlackBlinking = ref(false)

// 角色互相看对方
const isLookingAtEachOther = ref(false)

// 登录表单
const loginForm = reactive({
  username: '',
  password: ''
})

// 表单验证规则
const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, message: '用户名长度不能少于3位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}

// 计算角色位置
const calculatePosition = (charRef) => {
  if (!charRef.value) return { faceX: 0, faceY: 0, bodySkew: 0 }

  const rect = charRef.value.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 3

  const deltaX = mouseX.value - centerX
  const deltaY = mouseY.value - centerY

  const faceX = Math.max(-15, Math.min(15, deltaX / 20))
  const faceY = Math.max(-10, Math.min(10, deltaY / 30))
  const bodySkew = Math.max(-6, Math.min(6, -deltaX / 120))

  return { faceX, faceY, bodySkew }
}

// 计算瞳孔位置
const calculatePupilPosition = (maxDistance, forceLookX, forceLookY) => {
  if (forceLookX !== undefined && forceLookY !== undefined) {
    return { x: forceLookX, y: forceLookY }
  }

  // 使用窗口中心作为参考点
  const centerX = window.innerWidth / 2
  const centerY = window.innerHeight / 2

  const deltaX = mouseX.value - centerX
  const deltaY = mouseY.value - centerY
  const distance = Math.min(Math.sqrt(deltaX ** 2 + deltaY ** 2) / 30, maxDistance)

  const angle = Math.atan2(deltaY, deltaX)
  const x = Math.cos(angle) * distance
  const y = Math.sin(angle) * distance

  return { x, y }
}

// 角色位置
const purplePos = computed(() => calculatePosition(purpleRef))
const blackPos = computed(() => calculatePosition(blackRef))
const yellowPos = computed(() => calculatePosition(yellowRef))
const orangePos = computed(() => calculatePosition(orangeRef))

// 紫色角色样式
const purpleStyle = computed(() => {
  const isTypingPassword = loginForm.password.length > 0

  return {
    transform: isTypingPassword
      ? `skewX(${purplePos.value.bodySkew - 12}deg) translateX(40px)`
      : `skewX(${purplePos.value.bodySkew}deg)`,
    height: isTypingPassword ? '340px' : '240px'
  }
})

const purpleEyesStyle = computed(() => {
  // 限制眼睛移动范围，防止超出身体
  const clampedFaceX = Math.max(-12, Math.min(12, purplePos.value.faceX))
  return {
    left: isLookingAtEachOther.value ? '55px' : `${45 + clampedFaceX}px`,
    top: isLookingAtEachOther.value ? '65px' : `${40 + purplePos.value.faceY}px`
  }
})

const purpleLeftEyePos = computed(() => {
  if (isLookingAtEachOther.value) {
    return { x: 3, y: 4 }
  }
  return calculatePupilPosition(5)
})

const purpleRightEyePos = computed(() => purpleLeftEyePos.value)

// 黑色角色样式
const blackStyle = computed(() => {
  return {
    transform: isLookingAtEachOther.value
      ? `skewX(${blackPos.value.bodySkew * 1.5 + 10}deg) translateX(20px)`
      : `skewX(${blackPos.value.bodySkew}deg)`
  }
})

const blackEyesStyle = computed(() => {
  // 限制眼睛移动范围，防止超出身体
  const clampedFaceX = Math.max(-10, Math.min(10, blackPos.value.faceX))
  return {
    left: isLookingAtEachOther.value ? '32px' : `${26 + clampedFaceX}px`,
    top: isLookingAtEachOther.value ? '12px' : `${32 + blackPos.value.faceY}px`
  }
})

const blackLeftEyePos = computed(() => {
  if (isLookingAtEachOther.value) {
    return { x: 0, y: -4 }
  }
  return calculatePupilPosition(4)
})

const blackRightEyePos = computed(() => blackLeftEyePos.value)

// 黄色角色样式
const yellowStyle = computed(() => {
  return {
    transform: `skewX(${yellowPos.value.bodySkew}deg)`
  }
})

const yellowEyesStyle = computed(() => {
  // 限制眼睛移动范围，防止超出身体
  const clampedFaceX = Math.max(-10, Math.min(5, yellowPos.value.faceX))
  return {
    left: `${48 + clampedFaceX}px`,
    top: `${40 + yellowPos.value.faceY}px`
  }
})

const yellowMouthStyle = computed(() => {
  // 限制嘴巴移动范围，防止超出身体
  const clampedFaceX = Math.max(-10, Math.min(5, yellowPos.value.faceX))
  return {
    left: `${45 + clampedFaceX}px`,
    top: `${110 + yellowPos.value.faceY}px`
  }
})

const yellowPupilPos = computed(() => {
  return calculatePupilPosition(5)
})

// 橙色角色样式
const orangeStyle = computed(() => {
  return {
    transform: `skewX(${orangePos.value.bodySkew}deg)`
  }
})

const orangeEyesStyle = computed(() => {
  // 限制眼睛移动范围，防止超出身体
  const clampedFaceX = Math.max(-10, Math.min(8, orangePos.value.faceX))
  return {
    left: `${82 + clampedFaceX}px`,
    top: `${55 + orangePos.value.faceY}px`
  }
})

const orangePupilPos = computed(() => {
  return calculatePupilPosition(5)
})

// 获取眼球样式
const getEyeBallStyle = (size, _pupilSize, _maxDistance, eyeColor, _pupilColor, _pupilPos) => {
  const isBlinking = size === 18 ? isPurpleBlinking.value : isBlackBlinking.value

  return {
    width: `${size}px`,
    height: isBlinking ? '2px' : `${size}px`,
    backgroundColor: eyeColor,
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    transition: 'all 0.15s ease-out'
  }
}

// 获取瞳孔样式
const getPupilStyle = (size, _maxDistance, pupilColor, pupilPos) => {
  return {
    width: `${size}px`,
    height: `${size}px`,
    backgroundColor: pupilColor,
    borderRadius: '50%',
    transform: `translate(${pupilPos.x}px, ${pupilPos.y}px)`,
    transition: 'transform 0.1s ease-out'
  }
}

// 眨眼定时器
const scheduleBlink = (setBlinking) => {
  const blinkTimeout = setTimeout(() => {
    setBlinking.value = true
    setTimeout(() => {
      setBlinking.value = false
      scheduleBlink(setBlinking)
    }, 150)
  }, Math.random() * 4000 + 3000)

  return blinkTimeout
}

// 输入框焦点处理
const handleUsernameFocus = () => {
  isTyping.value = true
  errors.value.username = ''
}

const handlePasswordInput = () => {
  errors.value.password = ''
}

// 处理"记住我"复选框变化
const handleRememberMeChange = (checked) => {
  if (!checked) {
    // 用户取消勾选，清除已保存的用户名
    localStorage.removeItem('rememberedUsername')
    localStorage.removeItem('rememberExpiry')
  }
}

// 登录处理
const handleLogin = async () => {
  try {
    await loginFormRef.value.validate()

    loading.value = true
    errorMessage.value = ''


    const response = await login({
      username: loginForm.username,
      password: loginForm.password
    })


    if (response.code === 200) {
      ElMessage.success('登录成功')

      localStorage.setItem('token', response.data.accessToken)
      localStorage.setItem('refreshToken', response.data.refreshToken)
      localStorage.setItem('userInfo', JSON.stringify(response.data.userInfo))
      localStorage.setItem('isLogin', 'true')
      localStorage.setItem('username', response.data.userInfo.username)

      // 处理"记住我"功能
      if (rememberMe.value) {
        // 只保存用户名（不保存密码）
        localStorage.setItem('rememberedUsername', loginForm.username)
        const expiryDate = new Date()
        expiryDate.setDate(expiryDate.getDate() + 30)
        localStorage.setItem('rememberExpiry', expiryDate.toISOString())
      } else {
        // 清除记住的用户名
        localStorage.removeItem('rememberedUsername')
        localStorage.removeItem('rememberExpiry')
      }

      setTimeout(() => {
        router.push('/probes')
      }, 300)
    } else {
      errorMessage.value = response.message || '登录失败，请检查用户名和密码'
    }
  } catch (error) {
    if (error.errors) {
      // 表单验证错误
      return
    }
    console.error('登录失败:', error)
    errorMessage.value = '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// 鼠标移动事件
const handleMouseMove = (event) => {
  mouseX.value = event.clientX
  mouseY.value = event.clientY
}

// 互相看对方动画
watch(isTyping, (newValue) => {
  if (newValue) {
    isLookingAtEachOther.value = true
    setTimeout(() => {
      isLookingAtEachOther.value = false
    }, 800)
  }
})

// 定时器引用
let purpleBlinkTimer = null
let blackBlinkTimer = null

onMounted(() => {

  // 加载记住的用户名
  const rememberedUsername = localStorage.getItem('rememberedUsername')
  const rememberExpiry = localStorage.getItem('rememberExpiry')


  // 检查是否过期
  if (rememberedUsername && rememberExpiry) {
    const expiryDate = new Date(rememberExpiry)
    const now = new Date()

    if (now < expiryDate) {
      // 未过期，加载用户名并勾选复选框
      loginForm.username = rememberedUsername
      rememberMe.value = true
    } else {
      // 已过期，清除存储
      localStorage.removeItem('rememberedUsername')
      localStorage.removeItem('rememberExpiry')
    }
  } else if (rememberedUsername) {
    // 兼容旧版本：只有用户名没有过期时间
    loginForm.username = rememberedUsername
    rememberMe.value = true
    const expiryDate = new Date()
    expiryDate.setDate(expiryDate.getDate() + 30)
    localStorage.setItem('rememberExpiry', expiryDate.toISOString())
  }

  // 清除旧版本遗留的密码存储
  localStorage.removeItem('rememberedPassword')

  // 添加鼠标移动监听
  window.addEventListener('mousemove', handleMouseMove)

  // 启动眨眼动画
  purpleBlinkTimer = scheduleBlink(isPurpleBlinking)
  blackBlinkTimer = scheduleBlink(isBlackBlinking)
})

onUnmounted(() => {
  // 清理事件监听器
  window.removeEventListener('mousemove', handleMouseMove)

  // 清理定时器
  if (purpleBlinkTimer) clearTimeout(purpleBlinkTimer)
  if (blackBlinkTimer) clearTimeout(blackBlinkTimer)
})
</script>

<script>
export default {
  name: 'LoginPage'
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
  padding: 24px;
}

// 单卡片容器
.login-card {
  display: grid;
  grid-template-columns: 1fr;
  background: var(--bg-card);
  border-radius: 32px;
  overflow: hidden;
  width: 90vw;
  max-width: 1000px;
  box-shadow: var(--shadow-xl);
  animation: cardSlideIn 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
  position: relative;
  transition: all 0.3s ease;
}

@media (min-width: 1024px) {
  .login-card {
    grid-template-columns: 1.2fr 0.8fr;
    min-height: 600px;
  }
}

@keyframes cardSlideIn {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

// 左侧动画区域
.left-section {
  background: linear-gradient(135deg, var(--bg-hover) 0%, var(--bg-active) 100%);
  padding: 32px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 320px;
  position: relative;
  transition: background 0.3s ease;
}

// 顶部 Logo
.top-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.logo-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #6C3FF5 0%, #8B5CF6 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  filter: drop-shadow(0 4px 12px rgba(108, 63, 245, 0.4));
}

.logo-text {
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.5px;
  transition: color 0.3s ease;
}

// 角色容器
.characters-container {
  position: relative;
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: linear-gradient(135deg, var(--primary-light) 0%, rgba(139, 92, 246, 0.1) 100%);
  border-radius: 24px;
  padding: 24px;
  overflow: hidden;
  box-shadow: inset 0 2px 20px rgba(0, 0, 0, 0.1);
  transition: background 0.3s ease;
}

// 角色基础样式
.character {
  position: absolute;
  bottom: 0;
  transition: all 0.7s ease-in-out;
  transform-origin: bottom center;
}

// 紫色角色
.purple-character {
  left: 12%;
  width: 25%;
  height: 240px;
  background-color: #6C3FF5;
  border-radius: 16px 16px 0 0;
  z-index: 1;
  box-shadow: 0 15px 40px rgba(108, 63, 245, 0.4);
}

// 黑色角色
.black-character {
  left: 40%;
  width: 18%;
  height: 190px;
  background-color: #1a1a1a;
  border-radius: 14px 14px 0 0;
  z-index: 2;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.4);
}

// 橙色角色
.orange-character {
  left: 3%;
  width: 32%;
  height: 120px;
  background-color: #FF9B6B;
  border-radius: 120px 120px 0 0;
  z-index: 3;
  box-shadow: 0 10px 25px rgba(255, 155, 107, 0.4);
}

// 黄色角色
.yellow-character {
  left: 55%;
  width: 20%;
  height: 140px;
  background-color: #E8D754;
  border-radius: 70px 70px 0 0;
  z-index: 4;
  box-shadow: 0 10px 25px rgba(232, 215, 84, 0.4);
}

// 眼睛容器
.eyes {
  position: absolute;
  display: flex;
  gap: 32px;
  transition: all 0.7s ease-in-out;
}

.purple-eyes {
  gap: 24px;
}

.black-eyes {
  gap: 16px;
}

.orange-eyes,
.yellow-eyes {
  gap: 24px;
}

// 瞳孔
.pupil {
  transition: transform 0.1s ease-out;
}

// 嘴巴
.mouth {
  position: absolute;
  background-color: #2D2D2D;
  border-radius: 9999px;
  transition: all 0.2s ease-out;
}

.yellow-mouth {
  width: 60px;
  height: 4px;
}

// 右侧登录表单区
.right-section {
  background: var(--bg-card);
  padding: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  transition: background 0.3s ease;
}

.login-wrapper {
  width: 100%;
}

// 标题
.header {
  text-align: center;
  margin-bottom: 28px;
}

.title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

// 登录表单
.login-form {
  .el-form-item {
    margin-bottom: 16px;
  }
}

.form-label {
  display: block;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  letter-spacing: 0.3px;
}

.error-message {
  padding: 14px 16px;
  font-size: 14px;
  color: #FF6B6B;
  background-color: rgba(255, 107, 107, 0.1);
  border: 1px solid rgba(255, 107, 107, 0.3);
  border-radius: 12px;
  backdrop-filter: blur(10px);
}

.login-button {
  width: 100%;
  height: 48px;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, #6C3FF5 0%, #8B5CF6 100%);
  border: none;
  border-radius: 12px;
  color: white;
  letter-spacing: 0.5px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 35px rgba(108, 63, 245, 0.4);
  }

  &:active {
    transform: translateY(0);
  }
}

// 表单选项布局优化
.form-options {
  display: flex;
  justify-content: center;
  width: 100%;
}

// Element Plus 样式覆盖
:deep(.el-input__wrapper) {
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background-color: var(--input-bg);
  box-shadow: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    border-color: var(--border-hover);
    background-color: var(--input-bg-hover);
  }

  &.is-focus {
    border-color: var(--primary);
    box-shadow: 0 0 0 4px var(--primary-light);
  }
}

:deep(.el-input__inner) {
  color: var(--text-primary);
  font-size: 15px;

  &::placeholder {
    color: var(--text-tertiary);
  }
}

:deep(.el-checkbox__label) {
  font-size: 14px;
  color: var(--text-secondary);
}

:deep(.el-checkbox__inner) {
  background-color: var(--input-bg);
  border-color: var(--border-color);

  &:hover {
    border-color: var(--border-hover);
  }
}

:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: var(--primary);
  border-color: var(--primary);
}

:deep(.el-button--large) {
  padding: 14px 28px;
}

:deep(.el-button.is-link) {
  color: var(--primary);
  font-weight: 500;

  &:hover {
    color: var(--primary-hover);
  }
}

// ===================================
// UI/UX 优化 (v2.0)
// ===================================

// 跳过动画链接
.skip-animation {
  position: absolute;
  top: -40px;
  left: 0;
  background: var(--primary-500, #409eff);
  color: white;
  padding: 8px 16px;
  z-index: 9999;
  transition: top 0.3s;
  text-decoration: none;
  font-weight: 500;

  &:focus {
    top: 0;
  }
}

// 错误提示优化
.error-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-danger, #f56c6c);
}

// 表单输入框焦点状态优化
:deep(.el-input__wrapper) {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    box-shadow: 0 0 0 1px var(--el-border-color-hover) inset;
  }

  &.is-focus {
    box-shadow: 0 0 0 1px var(--el-color-primary) inset,
                0 0 0 4px rgba(64, 158, 255, 0.1);
  }
}

// 登录按钮触摸目标优化
.login-button.touch-target {
  min-width: 44px;
  min-height: 44px;

  &:focus-visible {
    outline: 2px solid var(--primary-500, #409eff);
    outline-offset: 2px;
  }
}

// 减少动画偏好支持
@media (prefers-reduced-motion: reduce) {
  .login-card {
    animation: none !important;
  }

  .character {
    transition: none !important;
    animation: none !important;
  }

  .login-button {
    transition: none !important;

    &:hover {
      transform: none !important;
    }
  }
}

// 响应式优化
@media (max-width: 768px) {
  .login-page {
    padding: 16px;
  }

  .login-card {
    width: 100vw;
    border-radius: 16px;

    @media (min-width: 1024px) {
      grid-template-columns: 1fr;
      min-height: auto;
    }
  }

  .left-section {
    min-height: 200px;
    padding: 20px;

    @media (min-width: 1024px) {
      display: none;
    }
  }

  .right-section {
    padding: 24px 20px;
  }

  .title {
    font-size: 24px;
  }

  .login-button {
    height: 52px;
    font-size: 16px;
  }

  // 移动端隐藏动画
  @media (max-width: 1024px) {
    .left-section {
      display: none;
    }

    .login-card {
      grid-template-columns: 1fr;
    }
  }
}

@media (max-width: 480px) {
  .title {
    font-size: 20px;
  }

  .subtitle {
    font-size: 13px;
  }

  .form-label {
    font-size: 13px;
  }

  :deep(.el-input__inner) {
    font-size: 14px;
  }
}

// 高对比度模式支持
@media (prefers-contrast: high) {
  .login-card {
    border: 2px solid var(--text-primary);
  }

  .form-label {
    font-weight: 600;
  }

  .login-button {
    border-width: 2px;
  }
}

// 打印样式
@media print {
  .login-page {
    display: none;
  }
}
</style>
