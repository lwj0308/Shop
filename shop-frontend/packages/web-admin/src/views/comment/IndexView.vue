<template>
  <!-- 管理员评价管理页 -->
  <div class="comment-list">
    <!-- 搜索区：按评分类型筛选评价 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="评分类型">
          <el-select v-model="queryForm.scoreType" placeholder="全部" style="width: 140px">
            <el-option
              v-for="opt in commentScoreTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 评价列表表格 -->
    <el-card>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="商品名称" min-width="160">
          <template #default="{ row }">
            <!-- 优先展示商品名称，没有则展示商品ID方便管理员定位 -->
            <span v-if="row.productName">{{ row.productName }}</span>
            <span v-else style="color: var(--el-text-color-secondary)">商品ID：{{ row.productId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户" min-width="140">
          <template #default="{ row }">
            <!-- 匿名评价显示"匿名用户"+标签，保护用户隐私 -->
            <div class="user-cell">
              <span>{{ getDisplayName(row) }}</span>
              <el-tag v-if="row.isAnonymous === 1" type="info" size="small">匿名</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="160">
          <template #default="{ row }">
            <!-- 只读的星级评分，直观显示用户打分 -->
            <el-rate :model-value="row.score" disabled show-score text-color="#ff9900" />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="220">
          <template #default="{ row }">
            <!-- 评价内容可能较长，超出部分用省略号，hover 显示完整内容 -->
            <div class="content-cell">{{ row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column label="评价图片" width="120">
          <template #default="{ row }">
            <!-- 评价图片支持点击放大预览，没有图片则显示占位符 -->
            <div v-if="row.images && row.images.length" class="image-cell">
              <el-image
                :src="row.images[0]"
                :preview-src-list="row.images"
                :preview-teleported="true"
                fit="cover"
                class="comment-image"
              />
              <!-- 多张图片时显示总数 -->
              <span v-if="row.images.length > 1" class="image-count">共{{ row.images.length }}张</span>
            </div>
            <span v-else style="color: var(--el-text-color-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="商家回复" min-width="200">
          <template #default="{ row }">
            <!-- 已回复展示回复内容，未回复显示占位符 -->
            <div v-if="row.reply" class="reply-cell">{{ row.reply }}</div>
            <span v-else style="color: var(--el-text-color-placeholder)">未回复</span>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <!-- 删除和回复两个操作，删除需二次确认 -->
            <el-button type="primary" text size="small" @click="openReplyDialog(row)">
              {{ row.reply ? '修改回复' : '回复' }}
            </el-button>
            <el-button type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 回复评价对话框 -->
    <el-dialog v-model="dialogVisible" title="回复评价" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="评价内容">
          <!-- 展示原评价内容，方便管理员参考后回复 -->
          <div class="origin-content">{{ currentComment?.content }}</div>
        </el-form-item>
        <el-form-item label="回复内容" prop="reply">
          <!-- 管理员输入的回复内容，提交后会展示给用户看 -->
          <el-input
            v-model="form.reply"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请输入回复内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleReply">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 管理员评价管理页
 *
 * 功能说明（小白版）：
 * 1. 查看全平台商品评价（既包括平台商品，也包括各个商家商品的评价）
 * 2. 支持按"评分类型"筛选（全部/好评/中评/差评）
 * 3. 管理员可以"删除"违规评价
 * 4. 管理员可以"回复"用户评价（已回复的可修改回复）
 *
 * 数据来源：通过 shop-admin 服务的 /admin/manage/comment/* 接口，
 *          内部再通过 Feign 转发到 shop-product 服务的评价表查询。
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getAdminCommentList,
  deleteComment,
  adminReplyComment,
  CommentScoreType,
  commentScoreTypeOptions,
  type CommentInfo,
} from '@shop/shared'

/** 加载状态（表格 loading 动画） */
const loading = ref(false)
/** 评价列表数据 */
const tableData = ref<CommentInfo[]>([])
/** 总条数（分页用） */
const total = ref(0)

/** 查询参数（搜索区表单） */
const queryForm = reactive({
  // 默认查"全部"，对应 commentScoreTypeOptions 里的 ALL 选项
  scoreType: CommentScoreType.ALL as CommentScoreType,
  pageNum: 1,
  pageSize: 10,
})

/** 回复对话框显示状态 */
const dialogVisible = ref(false)
/** 提交中状态（防止重复点击） */
const submitting = ref(false)
/** 表单引用（用于校验和重置） */
const formRef = ref<FormInstance>()
/** 当前正在回复的评价（用于对话框展示原评价内容，以及提交时拿到评价ID） */
const currentComment = ref<CommentInfo>()

/** 回复表单 */
const form = reactive({
  reply: '',
})

/** 表单校验规则：回复内容必填且不能全是空格 */
const formRules: FormRules = {
  reply: [
    {
      validator: (_rule, value: string, callback) => {
        // 回复内容不能为空，也不能全是空格
        if (!value || !value.trim()) {
          callback(new Error('请输入回复内容'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

/** 点击搜索：重置到第1页并查询 */
function handleSearch() {
  queryForm.pageNum = 1
  loadData()
}

/** 重置搜索条件并重新查询 */
function handleReset() {
  queryForm.scoreType = CommentScoreType.ALL
  queryForm.pageNum = 1
  loadData()
}

/** 加载评价列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await getAdminCommentList({
      pageNum: queryForm.pageNum,
      pageSize: queryForm.pageSize,
      scoreType: queryForm.scoreType,
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error) || '加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 获取用户展示名称
 * 匿名评价显示"匿名用户"，非匿名显示昵称（没有昵称则显示用户ID）
 */
function getDisplayName(row: CommentInfo): string {
  if (row.isAnonymous === 1) return '匿名用户'
  return row.userNickname || `用户${row.userId}`
}

/** 打开回复对话框，把当前评价记录下来并预填已有回复 */
function openReplyDialog(row: CommentInfo) {
  currentComment.value = row
  // 如果已有回复，预填到输入框，方便管理员在原回复基础上修改
  form.reply = row.reply || ''
  dialogVisible.value = true
}

/** 提交回复评价 */
async function handleReply() {
  // 先做表单校验，校验失败则不提交
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // currentComment 一定存在（打开对话框时已赋值），这里做类型收窄保证 TS 不报错
  const comment = currentComment.value
  if (!comment) return

  submitting.value = true
  try {
    await adminReplyComment(comment.id, form.reply.trim())
    ElMessage.success('回复成功')
    dialogVisible.value = false
    // 回复成功后刷新列表，让表格展示最新回复内容
    loadData()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error) || '回复失败')
  } finally {
    submitting.value = false
  }
}

/** 删除评价（弹确认框，避免误操作） */
async function handleDelete(row: CommentInfo) {
  // 弹确认框，提示用户评价删除后不可恢复
  try {
    await ElMessageBox.confirm(
      `确定要删除该评价吗？删除后用户将无法再看到此评价，且操作不可恢复。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    // 用户点了取消，啥也不做
    return
  }

  try {
    await deleteComment(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error) || '删除失败')
  }
}

/** 格式化时间：去掉 T 符号，只保留到分钟 */
function formatTime(time: string): string {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

/** 从 catch 捕获的 error 中提取后端返回的错误消息 */
function getErrorMessage(error: unknown): string {
  if (error instanceof Error) return error.message
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const msg = (error as { message: unknown }).message
    return typeof msg === 'string' ? msg : ''
  }
  return ''
}

/** 页面加载时获取评价列表 */
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.comment-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card :deep(.el-card__body) {
  padding-bottom: 0;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
/* 用户单元格：昵称和匿名标签横向排列 */
.user-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
/* 评价内容单元格：限制高度并隐藏溢出，避免长内容撑高表格行 */
.content-cell {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-all;
}
/* 图片单元格：缩略图 + 数量提示纵向排列 */
.image-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
/* 评价图片缩略图固定大小 */
.comment-image {
  width: 50px;
  height: 50px;
  border-radius: 4px;
  cursor: pointer;
}
.image-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
/* 商家回复单元格：限制行数，避免长回复撑高 */
.reply-cell {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-all;
}
/* 对话框中原评价内容展示区 */
.origin-content {
  background-color: var(--el-fill-color-light);
  padding: 10px 12px;
  border-radius: 4px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}
</style>
