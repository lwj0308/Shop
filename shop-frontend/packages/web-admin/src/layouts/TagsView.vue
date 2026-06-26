<template>
  <!-- 标签页导航栏 -->
  <div class="tags-view">
    <el-scrollbar>
      <div class="tags-list">
        <div
          v-for="tag in visitedViews"
          :key="tag.path"
          class="tag-item"
          :class="{ active: isActive(tag) }"
          @click="router.push(tag.path)"
        >
          <span>{{ tag.title }}</span>
          <el-icon
            v-if="!tag.affix"
            class="tag-close"
            @click.stop="closeTag(tag)"
          >
            <Close />
          </el-icon>
        </div>
      </div>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
/**
 * 标签页导航
 *
 * 记录用户访问过的页面，可以快速切换。
 * 仪表盘标签固定不可关闭，其他标签可以关闭。
 */

import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Close } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

/** 标签项接口 */
interface TagView {
  path: string
  title: string
  affix?: boolean  // 是否固定（不可关闭）
}

/** 已访问的标签列表 */
const visitedViews = ref<TagView[]>([
  { path: '/dashboard', title: '仪表盘', affix: true },
])

/**
 * 判断标签是否为当前激活状态
 */
function isActive(tag: TagView) {
  return tag.path === route.path
}

/**
 * 关闭标签
 * 如果关闭的是当前标签，自动跳转到上一个标签
 */
function closeTag(tag: TagView) {
  const index = visitedViews.value.findIndex(v => v.path === tag.path)
  if (index === -1) return

  visitedViews.value.splice(index, 1)

  // 如果关闭的是当前页面，跳转到最后一个标签
  if (isActive(tag)) {
    const lastView = visitedViews.value[visitedViews.value.length - 1]
    if (lastView) {
      router.push(lastView.path)
    }
  }
}

/**
 * 添加标签
 * 路由变化时自动添加新标签
 */
function addTag() {
  if (route.meta?.title && !route.meta?.hidden) {
    const exists = visitedViews.value.some(v => v.path === route.path)
    if (!exists) {
      visitedViews.value.push({
        path: route.path,
        title: route.meta.title as string,
      })
    }
  }
}

// 监听路由变化，自动添加标签
watch(() => route.path, () => {
  addTag()
}, { immediate: true })
</script>

<style scoped>
.tags-view {
  height: 34px;
  background: var(--color-card);
  border-bottom: 1px solid var(--color-border);
  padding: 0 8px;
  display: flex;
  align-items: center;
}

.tags-list {
  display: flex;
  gap: 4px;
  white-space: nowrap;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--color-border);
  border-radius: 3px;
  cursor: pointer;
  color: var(--color-text-secondary);
  background: var(--color-card);
}

.tag-item:hover {
  color: var(--color-primary);
}

.tag-item.active {
  background-color: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}

.tag-close {
  font-size: 12px;
  border-radius: 50%;
}

.tag-close:hover {
  background: rgba(0, 0, 0, 0.1);
}

.tag-item.active .tag-close:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>
