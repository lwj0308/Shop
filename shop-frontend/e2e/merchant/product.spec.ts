/**
 * F-E-07 商家发布商品 E2E 测试
 * <p>
 * 测试场景：商品列表加载 → 查看商品 → 新建商品 → 填写信息 → 提交 → 上架
 * 涵盖商品列表页、商品编辑页、商品创建等 6 个测试用例。
 * 所有 API 请求都通过 page.route() mock，不依赖真实后端服务。
 * </p>
 * <p>
 * 注意：商家后台除登录页和入驻页外都需要登录才能访问。
 * 测试中通过往 localStorage 写入假的 token 来模拟已登录状态，
 * 并 mock GET /merchant/info 返回已入驻（status=1）的商家信息，
 * 让前端路由守卫认为用户已登录且已入驻。
 * </p>
 */
import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { mockGet, mockPost, mockMerchantCommonGetApis, mockCategoryList, injectAuthToken } from '../helpers/api-mock'

/**
 * 填写并提交商品表单
 * <p>
 * 小白理解：发布商品需要填写很多信息——商品名称、分类、描述、图片、规格、价格、库存。
 * 这个辅助函数把所有必填项都填好，然后点击提交按钮。
 * </p>
 *
 * @param page Playwright 页面对象
 */
async function fillAndSubmitProductForm(page: Page): Promise<void> {
  // ===== 填写基本信息 =====
  await page.getByPlaceholder('请输入商品名称').fill('E2E测试商品 - 蓝牙耳机')
  await page.getByPlaceholder('请输入商品描述').fill('这是E2E测试创建的商品，高品质蓝牙耳机')

  // 选择商品分类（el-select 需要先点击展开下拉，再点击选项）
  // 注意：Element Plus 的 el-select 内部 combobox input 上覆盖了 .el-select__placeholder div，
  // 直接点击 combobox 会被拦截，所以要点击外层的 .el-select 容器。
  // 通过 form-item 的 label 文本「商品分类」定位到对应的 select 容器。
  await page.locator('.el-form-item').filter({ hasText: '商品分类' }).locator('.el-select').click()
  await page.waitForTimeout(300)
  // 点击下拉选项中的「数码电子」（el-select 下拉项是 option 角色）
  await page.getByRole('option', { name: '数码电子' }).click()
  await page.waitForTimeout(300)

  // ===== 上传商品图片 =====
  // el-upload 内部有 input[type="file"]，用 setInputFiles 模拟选择图片
  await page.setInputFiles('input[type="file"]', {
    name: 'product.jpg',
    mimeType: 'image/jpeg',
    buffer: Buffer.from('fake-product-image-for-e2e-test'),
  })
  await page.waitForTimeout(500)

  // ===== 添加规格和SKU =====
  // 点击「+ 添加规格」按钮，新增一个规格行
  // 注意：用 .add-spec-btn 类选择器定位，避免 getByText('添加规格') 匹配到「添加规格值」
  await page.locator('.add-spec-btn').click()
  await page.waitForTimeout(300)

  // 填写规格名（如「颜色」），然后按 Tab 触发 generateSkus 生成SKU
  await page.getByPlaceholder('规格名（如：颜色）').fill('颜色')
  await page.getByPlaceholder('规格名（如：颜色）').press('Tab')
  await page.waitForTimeout(300)

  // 点击「+ 添加规格值」按钮，显示规格值输入框
  // 注意：用 getByRole 精确匹配「添加规格值」，避免和「添加规格」冲突
  await page.getByRole('button', { name: '添加规格值' }).click()
  await page.waitForTimeout(300)

  // 输入规格值「红色」，按回车确认
  await page.getByPlaceholder('输入规格值').fill('红色')
  await page.getByPlaceholder('输入规格值').press('Enter')
  await page.waitForTimeout(500)

  // ===== 批量设置SKU价格和库存 =====
  // SKU表格生成后，用批量设置功能统一设置价格和库存
  await page.getByPlaceholder('价格（元）').fill('99.90')
  await page.getByRole('button', { name: '批量设置价格' }).click()
  await page.waitForTimeout(300)

  await page.getByPlaceholder('库存').fill('100')
  await page.getByRole('button', { name: '批量设置库存' }).click()
  await page.waitForTimeout(300)

  // ===== 点击提交按钮 =====
  // 提交按钮在表单底部的 .submit-row 中
  await page.locator('.submit-row').getByRole('button', { name: '提交' }).click()
}

test.describe('F-E-07 商家发布商品', () => {
  // 每个测试前都做两件事：
  // 1. 注入登录态（必须在 page.goto 之前调用，往 localStorage 写入 Base64 编码的 token）
  // 2. mock 商家端常用 GET 接口（/merchant/info 返回已入驻状态等）
  test.beforeEach(async ({ page }) => {
    await injectAuthToken(page)
    await mockMerchantCommonGetApis(page)
  })

  test('商品列表页应正常加载', async ({ page }) => {
    // mock 商品列表接口返回空列表（避免表格区域因无数据报错）
    await mockGet(page, '**/merchant/product/list**', {
      records: [],
      total: 0,
      current: 1,
      size: 10,
    })

    // 打开商品列表页
    await page.goto('/product/list')

    // 验证页面加载完成：「+ 发布商品」按钮可见
    // 注意：左侧菜单也有「发布商品」菜单项，用 getByRole 精确匹配按钮避免 strict mode violation
    await expect(page.getByRole('button', { name: '+ 发布商品' })).toBeVisible()
  })

  test('商品列表应显示已有商品', async ({ page }) => {
    // mock 商品列表接口返回一条商品数据
    await mockGet(page, '**/merchant/product/list**', {
      records: [
        {
          id: 1,
          name: 'E2E测试商品 - 蓝牙耳机',
          mainImage: '/placeholder.svg',
          categoryName: '数码电子',
          minPrice: 19900,
          maxPrice: 19900,
          stock: 100,
          salesCount: 50,
          status: 1,
        },
      ],
      total: 1,
      current: 1,
      size: 10,
    })

    // 打开商品列表页
    await page.goto('/product/list')

    // 验证商品名称显示在表格中
    await expect(page.getByText('E2E测试商品 - 蓝牙耳机')).toBeVisible()
  })

  test('点击"发布商品"应跳转到商品编辑页', async ({ page }) => {
    // mock 商品列表接口（空列表）
    await mockGet(page, '**/merchant/product/list**', {
      records: [],
      total: 0,
      current: 1,
      size: 10,
    })
    // mock 分类树（商品编辑页需要加载分类）
    await mockGet(page, '**/product/category/tree**', mockCategoryList)

    // 打开商品列表页
    await page.goto('/product/list')

    // 点击「+ 发布商品」按钮（用 getByRole 精确匹配按钮，避免匹配到菜单项）
    await page.getByRole('button', { name: '+ 发布商品' }).click()

    // 验证跳转到商品编辑页（URL 包含 /product/edit）
    await expect(page).toHaveURL(/\/product\/edit/)
  })

  test('商品编辑页应包含商品名称、价格、库存、分类等表单字段', async ({ page }) => {
    // mock 分类树接口
    await mockGet(page, '**/product/category/tree**', mockCategoryList)

    // 打开商品编辑页（新建模式，不带 id 参数）
    await page.goto('/product/edit')

    // 验证基本表单字段存在
    await expect(page.getByPlaceholder('请输入商品名称')).toBeVisible()
    // 注意：el-select 渲染为 combobox 角色，placeholder 不会作为 input 属性，
    // 用 getByRole('combobox', { name: '* 商品分类' }) 通过关联 label 定位
    await expect(page.getByRole('combobox', { name: '* 商品分类' })).toBeVisible()
    await expect(page.getByPlaceholder('请输入商品描述')).toBeVisible()

    // 验证页面标题是「添加商品」（新建模式）
    await expect(page.getByText('添加商品')).toBeVisible()
  })

  test('填写商品信息后提交应成功', async ({ page }) => {
    // mock 分类树接口
    await mockGet(page, '**/product/category/tree**', mockCategoryList)
    // mock 创建商品接口 POST /merchant/product，返回成功（带商品ID）
    await mockPost(page, '**/merchant/product/create**', { id: 2001 })

    // 打开商品编辑页
    await page.goto('/product/edit')

    // 填写商品信息并提交
    await fillAndSubmitProductForm(page)

    // 验证提交成功后显示成功提示「商品创建成功」
    await expect(page.getByText('商品创建成功')).toBeVisible({ timeout: 5000 })
  })

  test('提交后应跳转回商品列表或显示成功提示', async ({ page }) => {
    // mock 分类树接口
    await mockGet(page, '**/product/category/tree**', mockCategoryList)
    // mock 创建商品接口返回成功
    await mockPost(page, '**/merchant/product/create**', { id: 2002 })
    // mock 商品列表接口（提交成功后会跳回列表页，需要 mock 列表数据）
    await mockGet(page, '**/merchant/product/list**', {
      records: [],
      total: 0,
      current: 1,
      size: 10,
    })

    // 打开商品编辑页
    await page.goto('/product/edit')

    // 填写商品信息并提交
    await fillAndSubmitProductForm(page)

    // 提交成功后会先显示「商品创建成功」提示，然后跳转到商品列表页
    // 等待成功提示 或 跳转到列表页，任一先发生即算通过
    const successMessage = page.getByText('商品创建成功')
    const listUrl = page.waitForURL(/\/product\/list/, { timeout: 5000 })

    await Promise.race([
      successMessage.waitFor({ state: 'visible' }),
      listUrl,
    ])
  })
})
