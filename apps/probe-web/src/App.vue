<template>
  <div id="app">
    <a href="#main-content" class="skip-to-content">跳到主内容</a>
    <div v-if="isLoginPage" class="login-page-wrapper">
      <router-view />
    </div>
    <MainLayout v-else />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import MainLayout from './layouts/MainLayout.vue'

const route = useRoute()
const isLoginPage = computed(() => route.path === '/login')
</script>

<style>
#app {
  height: 100vh;
  width: 100%;
  overflow: hidden;
}

.login-page-wrapper {
  height: 100vh;
  width: 100%;
}

.skip-to-content {
  position: absolute;
  top: -100px;
  left: 0;
  background: #3B82F6;
  color: #fff;
  padding: 8px 16px;
  z-index: 9999;
  text-decoration: none;
  font-weight: 600;
  border-radius: 0 0 4px 0;
}

.skip-to-content:focus {
  top: 0;
}

html, body {
  margin: 0;
  padding: 0;
  overflow: hidden;
}

/* Fade transition for router-view */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
