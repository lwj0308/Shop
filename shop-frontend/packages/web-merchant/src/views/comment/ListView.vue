<template>
  <!-- 评价管理页面：商家查看和回复用户评价 -->
  <div class="comment-list">
    <!-- 搜索区：按回复状态筛选 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="回复状态">
          <el-select v-model="queryForm.hasReply" placeholder="全部" clearable style="width: 150px">
            <el-option label="全部" :value="undefined" />
            <el-option label="已回复" :value="true" />
            <el-option label="未回复" :value="false" />
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
        <el-table-column label="商品" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.productName || '-' }}</template>
        </el-table-column>
        <!-- 用户列：展示昵称；匿名评价（isAnonymous=1）时附加「匿名」标签 -->
        <el-table-column label="用户" min-width="120">
          <template #default="{ row }">
            <span>{{ row.userNickname || '匿名用户' }}</span>
            <el-tag
              v-if="row.isAnonymous === 1"
              type="info"
              size="small"
              class="anon-tag"
            >匿名</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="160">
          <template #default="{ row }">
            <el-rate :model-value="row.score" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="240">
          <template #default="{ row }">
            <!-- 初始评价内容 -->
            <div class="comment-content">{{ row.content || '-' }}</div>
            <!-- 追评列表：初始评价才会附带 replyList -->
            <div v-if="row.replyList && row.replyList.length" class="reply-list">
              <div v-for="reply in row.replyList" :key="reply.id" class="reply-item">
                <div class="reply-text">
                  <el-tag type="warning" size="small" effect="plain">追评</el-tag>
                  <span class="reply-content">{{ reply.content }}</span>
                </div>
                <!-- 追评图片 -->
                <div v-if="reply.images && reply.images.length" class="image-list">
                  <el-image
                    v-for="(img, index) in reply.images"
                    :key="index"
                    :src="img"
                    :preview-src-list="reply.images"
                    :initial-index="index"
                    fit="cover"
                    class="comment-image"
                    preview-teleported
                  />
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="评价图片" width="120">
          <template #default="{ row }">
            <div v-if="row.images && row.images.length" class="image-list">
              <el-image
                v-for="(img, index) in row.images"
                :key="index"
                :src="img"
                :preview-src-list="row.images"
                :initial-index="index"
                fit="cover"
                class="comment-image"
                preview-teleported
              />
            </div>
            <span v-else style="color: #999">无</span>
          </template>
        </el-table-column>
        <el-table-column label="回复状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.reply ? 'success' : 'warning'" size="small">
              {{ row.reply ? '已回复' : '未回复' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="handleReply(row)">
              {{ row.reply ? '修改回复' : '回复' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 回复弹窗：输入或修改回复内容 -->
    <el-dialog v-model="replyDialogVisible" title="回复评价" width="500px" destroy-on-close>
      <div class="reply-comment-preview" v-if="currentComment">
        <div class="preview-row">
          <span class="preview-label">商品：</span>
          <span>{{ currentComment.productName || '-' }}</span>
        </div>
        <div class="preview-row">
          <span class="preview-label">评分：</span>
          <el-rate :model-value="currentComment.score" disabled size="small" />
        </div>
        <div class="preview-row">
          <span class="preview-label">评价：</span>
          <span>{{ currentComment.content || '-' }}</span>
        </div>
      </div>
      <el-form ref="replyFormRef" :model="replyForm" :rules="replyFormRules" label-width="80px">
        <el-form-item label="回复内容" prop="reply">
          <el-input
            v-model="replyForm.reply"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请输入回复内容（最多500字）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="replyLoading" @click="handleReplySubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 评价管理页面
 * 商家查看自己店铺商品收到的评价，并可以回复或修改回复
 */

import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getMerchantCommentList, replyComment } from '@shop/shared'
import type { CommentInfo } from '@shop/shared'

/** 搜索表单 */
const queryForm = reactive({
  hasReply: undefined as boolean | undefined,
  pageNum: 1,
  pageSize: 10,
})

/** 表格数据 */
const loading = ref(false)
const tableData = ref<CommentInfo[]>([])
const total = ref(0)

/** 回复弹窗相关状态 */
const replyDialogVisible = ref(false)
const replyLoading = ref(false)
const replyFormRef = ref<FormInstance>()
/** 当前要回复的评价 */
const currentComment = ref<CommentInfo | null>(null)

/** 回复表单数据 */
const replyForm = reactive({
  commentId: 0,
  reply: '',
})

/** 回复表单校验规则 */
const replyFormRules: FormRules = {
  reply: [
    { required: true, message: '请输入回复内容', trigger: 'blur' },
    { max: 500, message: '回复内容不能超过500字', trigger: 'blur' },
  ],
}

/** 点击搜索按钮，重置到第1页并查询 */
function handleSearch() {
  queryForm.pageNum = 1
  loadData()
}

/** 重置搜索条件并重新查询 */
function handleReset() {
  queryForm.hasReply = undefined
  queryForm.pageNum = 1
  loadData()
}

/** 加载评价列表数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await getMerchantCommentList(queryForm)
    tableData.value = res.data.data.records || []
    total.value = res.data.data.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 打开回复弹窗 */
function handleReply(row: CommentInfo) {
  currentComment.value = row
  replyForm.commentId = row.id
  replyForm.reply = row.reply || ''
  replyDialogVisible.value = true
}

/** 提交回复：校验表单后调用回复接口 */
async function handleReplySubmit() {
  const valid = await replyFormRef.value?.validate().catch(() => false)
  if (!valid) return
  replyLoading.value = true
  try {
    await replyComment({ commentId: replyForm.commentId, reply: replyForm.reply })
    ElMessage.success('回复成功')
    replyDialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '回复失败')
  } finally {
    replyLoading.value = false
  }
}

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

/* 评价图片列表 */
.image-list {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.comment-image {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  cursor: pointer;
}

/* 用户列「匿名」标签：与昵称之间留一点间距 */
.anon-tag {
  margin-left: 6px;
}

/* 评价内容文本：允许换行，避免长文本撑乱表格 */
.comment-content {
  line-height: 1.5;
  word-break: break-all;
}

/* 追评列表容器：与初始评价拉开间距 */
.reply-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 单条追评：浅色背景 + 左侧橙色边线，便于和初始评价区分 */
.reply-item {
  background: #fafafa;
  border-left: 3px solid #e6a23c;
  padding: 6px 8px;
  border-radius: 2px;
}

/* 追评的标签 + 文本一行展示 */
.reply-text {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 追评正文：字号略小、颜色略淡 */
.reply-content {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  word-break: break-all;
}

/* 回复弹窗中的评价预览区 */
.reply-comment-preview {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 12px;
  margin-bottom: 16px;
}

.preview-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  font-size: 14px;
  line-height: 1.5;
}

.preview-row:last-child {
  margin-bottom: 0;
}

.preview-label {
  color: #909399;
  min-width: 50px;
  flex-shrink: 0;
}
</style>
