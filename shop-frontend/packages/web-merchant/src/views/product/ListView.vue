<template>
  <!-- 商品列表页：搜索栏 + 表格 + 分页 -->
  <div class="product-list">
    <!-- 搜索栏卡片 -->
    <div class="search-card">
      <div class="search-row">
        <div class="search-field">
          <label class="search-label">商品名称</label>
          <el-input v-model="nameFilter" placeholder="请输入商品名称" clearable />
        </div>
        <div class="search-field" style="width: 160px;">
          <label class="search-label">商品分类</label>
          <el-select v-model="categoryFilter" placeholder="全部分类" clearable>
            <el-option label="服装鞋帽" value="1" />
            <el-option label="数码电子" value="2" />
            <el-option label="家居生活" value="3" />
            <el-option label="美妆个护" value="4" />
          </el-select>
        </div>
        <div class="search-field" style="width: 140px;">
          <label class="search-label">商品状态</label>
          <el-select v-model="statusFilter" placeholder="全部状态" clearable @change="loadProducts">
            <el-option label="已上架" :value="1" />
            <el-option label="已下架" :value="0" />
          </el-select>
        </div>
        <el-button type="primary" @click="loadProducts">🔍 搜索</el-button>
        <el-button @click="resetFilter">重置</el-button>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <div class="action-info">共 <b class="highlight">{{ total }}</b> 件商品</div>
      <el-button type="primary" @click="goToAdd">+ 发布商品</el-button>
    </div>

    <!-- 批量操作栏 -->
    <div v-if="selectedIds.length > 0" class="batch-bar">
      <span>已选择 {{ selectedIds.length }} 件商品</span>
      <el-button type="success" size="small" @click="handleBatchOnShelf">批量上架</el-button>
      <el-button type="warning" size="small" @click="handleBatchOffShelf">批量下架</el-button>
      <el-button type="danger" size="small" @click="handleBatchDelete">批量删除</el-button>
    </div>

    <!-- 商品表格：斑马纹 -->
    <el-table
      v-loading="loading"
      :data="productList"
      stripe
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column label="商品图片" width="80">
        <template #default="{ row }">
          <el-image :src="row.mainImage" class="product-image" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column label="商品名称" min-width="200">
        <template #default="{ row }">
          <div class="product-name">{{ row.name }}</div>
          <div class="product-category">{{ row.categoryName }}</div>
        </template>
      </el-table-column>
      <el-table-column label="价格" width="160">
        <template #default="{ row }">
          <span class="price">{{ formatPriceRange(row.minPrice, row.maxPrice) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="库存" prop="salesCount" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag
            :type="row.status === 1 ? 'success' : 'info'"
            size="small"
          >
            {{ PRODUCT_STATUS_MAP[row.status]?.label || '未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="goToEdit(row.id)">编辑</el-button>
          <el-button
            v-if="row.status === 1"
            type="warning" text size="small"
            @click="handleToggleStatus(row)"
          >
            下架
          </el-button>
          <el-button
            v-else
            type="success" text size="small"
            @click="handleToggleStatus(row)"
          >
            上架
          </el-button>
          <el-button type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页器 -->
    <div class="pagination-row">
      <span class="pagination-total">共 {{ total }} 条</span>
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next"
        @size-change="loadProducts"
        @current-change="loadProducts"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 商品列表页
 * 搜索栏 + 斑马纹表格 + 分页器
 * 优化点：批量操作（批量上下架、批量删除）、删除确认弹窗、上下架状态切换确认
 */

import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMerchantProductList,
  toggleProductStatus,
  deleteProduct,
} from '@shop/shared'
import { formatPriceWithSymbol } from '@shop/shared'
import { PRODUCT_STATUS_MAP } from '@shop/shared'
import type { ProductInfo } from '@shop/shared'

const router = useRouter()

/** 商品列表数据 */
const productList = ref<ProductInfo[]>([])

/** 加载状态 */
const loading = ref(false)

/** 分页参数 */
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

/** 名称筛选 */
const nameFilter = ref('')

/** 分类筛选 */
const categoryFilter = ref('')

/** 状态筛选 */
const statusFilter = ref<number | undefined>(undefined)

/** 已选中的商品ID列表 */
const selectedIds = ref<number[]>([])

/**
 * 格式化价格区间
 * 如果最低价和最高价相同，只显示一个价格
 */
const formatPriceRange = (minPrice: number, maxPrice: number): string => {
  if (minPrice === maxPrice) {
    return formatPriceWithSymbol(minPrice)
  }
  return `${formatPriceWithSymbol(minPrice)} - ${formatPriceWithSymbol(maxPrice)}`
}

/** 重置筛选条件 */
const resetFilter = () => {
  nameFilter.value = ''
  categoryFilter.value = ''
  statusFilter.value = undefined
  loadProducts()
}

/**
 * 加载商品列表
 */
const loadProducts = async () => {
  console.log('[商品列表] 开始加载')
  loading.value = true
  try {
    const res = await getMerchantProductList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: statusFilter.value,
    })
    console.log('[商品列表] 加载成功, records=', res.data.records)
    productList.value = res.data.records
    total.value = res.data.total
  } catch (err) {
    console.error('[商品列表] 加载失败:', err)
    ElMessage.error('加载商品列表失败')
  } finally {
    loading.value = false
  }
}

/**
 * 处理表格多选变化
 * 记录选中的商品ID
 */
const handleSelectionChange = (rows: ProductInfo[]) => {
  selectedIds.value = rows.map(r => r.id)
}

/**
 * 上下架状态切换
 * 弹出确认弹窗
 */
const handleToggleStatus = async (product: ProductInfo) => {
  const newStatus = product.status === 1 ? 0 : 1
  const actionText = newStatus === 1 ? '上架' : '下架'

  try {
    await ElMessageBox.confirm(
      `确定要${actionText}商品"${product.name}"吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }

  try {
    await toggleProductStatus(product.id, newStatus as 0 | 1)
    ElMessage.success(`${actionText}成功`)
    loadProducts()
  } catch (error) {
    const msg = error instanceof Error ? error.message : `${actionText}失败`
    ElMessage.error(msg)
  }
}

/**
 * 删除商品
 * 弹出确认弹窗，防止误操作
 */
const handleDelete = async (product: ProductInfo) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除商品"${product.name}"吗？删除后不可恢复！`,
      '警告',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'error' },
    )
  } catch {
    return
  }

  try {
    await deleteProduct(product.id)
    ElMessage.success('删除成功')
    loadProducts()
  } catch (error) {
    const msg = error instanceof Error ? error.message : '删除失败'
    ElMessage.error(msg)
  }
}

/** 批量上架 */
const handleBatchOnShelf = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要将选中的 ${selectedIds.value.length} 件商品上架吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }

  try {
    await Promise.all(selectedIds.value.map(id => toggleProductStatus(id, 1)))
    ElMessage.success('批量上架成功')
    loadProducts()
  } catch {
    ElMessage.error('部分商品上架失败，请重试')
  }
}

/** 批量下架 */
const handleBatchOffShelf = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要将选中的 ${selectedIds.value.length} 件商品下架吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await Promise.all(selectedIds.value.map(id => toggleProductStatus(id, 0)))
    ElMessage.success('批量下架成功')
    loadProducts()
  } catch {
    ElMessage.error('部分商品下架失败，请重试')
  }
}

/** 批量删除 */
const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedIds.value.length} 件商品吗？删除后不可恢复！`,
      '警告',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'error' },
    )
  } catch {
    return
  }

  try {
    await Promise.all(selectedIds.value.map(id => deleteProduct(id)))
    ElMessage.success('批量删除成功')
    loadProducts()
  } catch {
    ElMessage.error('部分商品删除失败，请重试')
  }
}

/** 跳转到添加商品页 */
const goToAdd = () => {
  router.push('/product/edit')
}

/** 跳转到编辑商品页 */
const goToEdit = (id: number) => {
  router.push(`/product/edit/${id}`)
}

onMounted(() => {
  loadProducts()
})
</script>

<style scoped>
.product-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 搜索栏卡片 */
.search-card {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 20px;
  box-shadow: var(--shadow-card);
}

.search-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.search-field {
  flex: 1;
  min-width: 180px;
}

.search-label {
  display: block;
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
  font-weight: 500;
}

/* 操作栏 */
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.action-info {
  font-size: 14px;
  color: var(--color-text-muted);
}

.highlight {
  color: var(--color-text);
}

/* 批量操作栏 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #ecf5ff;
  border-radius: var(--radius-button);
  font-size: 14px;
  color: var(--color-primary);
}

/* 商品图片 */
.product-image {
  width: 60px;
  height: 60px;
  border-radius: var(--radius-button);
  flex-shrink: 0;
}

/* 商品名称 */
.product-name {
  font-weight: 500;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-category {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 4px;
}

/* 价格：红色 */
.price {
  font-weight: 600;
  color: var(--color-danger);
}

/* 分页行 */
.pagination-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.pagination-total {
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
