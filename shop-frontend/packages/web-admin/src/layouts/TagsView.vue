<template>
  <!-- 标签页导航栏：玻璃胶囊化标签 -->
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
/* 标签页容器：玻璃白背景 + 底部分隔线 */
.tags-view {
  height: 38px;
  background: var(--color-glass-strong);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid var(--color-border);
  padding: 0 12px;
  display: flex;
  align-items: center;
}

.tags-list {
  display: flex;
  gap: 6px;
  white-space: nowrap;
}

/* 标签项：胶囊形 + 玻璃边框 + 平滑过渡 */
.tag-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-tag);
  cursor: pointer;
  color: var(--color-text-secondary);
  background: var(--color-glass);
  transition: all 0.25s var(--ease-out);
}

.tag-item:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--gradient-brand-soft);
  transform: translateY(-1px);
}

/* 激活态：翡翠青绿渐变背景 + 白字 + 发光阴影 */
.tag-item.active {
  background: var(--gradient-brand);
  color: #fff;
  border-color: transparent;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.35);
}

/* 关闭按钮：圆形 */
.tag-close {
  font-size: 12px;
  border-radius: 50%;
  padding: 2px;
  transition: all 0.2s var(--ease-out);
}

.tag-close:hover {
  background: rgba(0, 0, 0, 0.12);
  transform: rotate(90deg);
}

/* 激活态下关闭按钮 hover：白底深色 */
.tag-item.active .tag-close:hover {
  background: rgba(255, 255, 255, 0.35);
}

/* 暗色模式：玻璃背景更深 */
html.dark .tags-view {
  background: rgba(30, 41, 59, 0.85);
}
</style>
