/**
 * 权限指令 v-permission
 *
 * 用于按钮级别的权限控制，没有权限的按钮会自动从DOM中移除。
 *
 * 使用方式：
 * <el-button v-permission="['admin:user:add']">新增</el-button>
 * <el-button v-permission="['admin:user:edit', 'admin:user:delete']">操作</el-button>
 *
 * 原理：在元素挂载时，检查当前管理员是否拥有指定权限，
 * 如果没有就从父节点中移除该元素（相当于按钮不存在）。
 */

import type { Directive, DirectiveBinding } from 'vue'
import { useAdminStore } from '@/stores/admin'

export const permissionDirective: Directive = {
  /**
   * 元素挂载时检查权限
   * @param el DOM元素
   * @param binding 指令绑定值，如 v-permission="['admin:user:add']"
   */
  mounted(el: HTMLElement, binding: DirectiveBinding<string[]>) {
    const { value } = binding

    if (value && Array.isArray(value) && value.length > 0) {
      const adminStore = useAdminStore()
      const hasPermission = adminStore.hasAnyPermission(value)

      if (!hasPermission) {
        // 没有权限，从DOM中移除元素
        el.parentNode?.removeChild(el)
      }
    }
  },
}

/**
 * 注册权限指令到Vue应用
 * @param app Vue应用实例
 */
export function setupPermissionDirective(app: any) {
  app.directive('permission', permissionDirective)
}
