/**
 * web-admin 环境验证测试
 * <p>
 * 验证 Vitest + happy-dom 环境在管理后台可用。
 * 后续 F-C 系列任务会补充各管理页面的组件测试。
 * </p>
 */
import { describe, it, expect } from 'vitest'
import { ref, computed } from 'vue'

describe('web-admin 测试环境验证', () => {
  it('Vue 响应式 API 应正常工作', () => {
    const count = ref(0)
    const double = computed(() => count.value * 2)

    count.value = 5
    expect(double.value).toBe(10)
  })

  it('happy-dom 应提供 DOM API', () => {
    const div = document.createElement('div')
    div.textContent = '管理员后台'
    div.classList.add('admin-title')
    document.body.appendChild(div)

    expect(div.textContent).toBe('管理员后台')
    expect(div.classList.contains('admin-title')).toBe(true)
    expect(document.querySelector('.admin-title')).not.toBeNull()

    // 清理
    document.body.removeChild(div)
  })

  it('JSON 序列化/反序列化应正常工作', () => {
    const adminInfo = {
      id: 1,
      username: 'admin',
      roles: ['admin', 'operator'],
      permissions: ['user:list', 'user:edit'],
    }
    const json = JSON.stringify(adminInfo)
    const parsed = JSON.parse(json)

    expect(parsed.username).toBe('admin')
    expect(parsed.roles).toContain('admin')
    expect(parsed.permissions).toHaveLength(2)
  })
})
