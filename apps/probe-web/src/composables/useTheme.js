/**
 * Theme composable for managing dark/light mode
 * Provides reactive theme state and toggle functionality
 */
import { ref } from 'vue'

// Shared reactive state - shared across all components using this composable
const isDarkMode = ref(false)

/**
 * Theme composable
 * @returns {Object} Theme state and methods
 */
export function useTheme() {
  /**
   * Toggle between light and dark theme
   */
  const toggleTheme = () => {
    isDarkMode.value = !isDarkMode.value

    // Apply theme to DOM
    if (isDarkMode.value) {
      document.documentElement.classList.add('dark')
      document.documentElement.classList.add('dark-mode')
      document.body.style.backgroundColor = '#1a1a1a'
      document.body.style.color = '#e5e5e5'
    } else {
      document.documentElement.classList.remove('dark')
      document.documentElement.classList.remove('dark-mode')
      document.body.style.backgroundColor = '#f0f2f5'
      document.body.style.color = '#303133'
    }

    // Persist to localStorage
    localStorage.setItem('app_theme', isDarkMode.value ? 'dark' : 'light')

    // Dispatch event for other components
    window.dispatchEvent(new CustomEvent('theme-change', {
      detail: { theme: isDarkMode.value ? 'dark' : 'light' }
    }))
  }

  return {
    isDarkMode,
    toggleTheme
  }
}
