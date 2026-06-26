/**
 * 主题切换组合式函数
 *
 * 支持亮色/暗黑主题切换，使用VueUse的useDark实现。
 * 主题偏好会自动持久化到localStorage，下次打开自动恢复。
 *
 * 使用方式：
 * const { isDark, toggleTheme } = useTheme()
 */

import { useDark, useToggle } from '@vueuse/core'

/** 是否为暗黑模式（自动从localStorage恢复偏好） */
export const isDark = useDark({
  storageKey: 'admin_theme',
  valueDark: 'dark',
  valueLight: 'light',
})

/** 切换主题的函数 */
export const toggleTheme = useToggle(isDark)
