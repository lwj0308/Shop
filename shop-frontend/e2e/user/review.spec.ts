/**
 * F-E-05 商品评价与追评 E2E 测试
 * <p>
 * 测试场景：发表评价页 → 查看商品信息与评分组件 → 提交评价 → 匿名评价 → 追评页 → 提交追评。
 * 由于后端服务可能未启动，所有测试都通过 page.route() mock 接口数据。
 * 运行前需要先启动用户端 dev server（npm run dev:user），默认地址 http://localhost:3000。
 * </p>
 */
import { test, expect } from '@playwright/test'
import {
  mockGet,
  mockPost,
  mockUserCommonGetApis,
  mockOrderList,
  mockCommentList,
  injectAuthToken,
  mockUserInfo,
} from '../helpers/api-mock'

test.describe('F-E-05 商品评价与追评', () => {
  /**
   * 每个测试前先注入登录态并 mock 用户信息接口
   * <p>
   * 小白理解：发表评价 /review/create 和 追评 /review/append 页面都配置了
   * 路由 meta.requiresAuth=true，路由守卫会检查 localStorage 中的 token，
   * 没有就会取消导航并弹出登录弹窗，导致 body 不可见、测试失败。
   * 所以这里在每次测试前先用公共 injectAuthToken 注入 Base64 编码的假 token，
   * 同时 mock /user/info 接口，让前端以为用户已登录。
   * </p>
   */
  test.beforeEach(async ({ page }) => {
    await mockUserCommonGetApis(page)
    await injectAuthToken(page)
    await mockGet(page, '**/user/info**', mockUserInfo)
  })

  /**
   * 测试1：发表评价页应正常加载
   * 小白理解：打开评价页 URL，验证页面 body 可见，说明页面加载成功没有白屏。
   * 注意：URL 必须带 orderId、orderItemId、productId 三个参数，缺一个页面会显示"未找到订单商品信息"。
   */
  test('发表评价页应正常加载', async ({ page }) => {
    // mock 订单详情接口（评价页会调用 getOrderDetail 获取订单商品信息）
    await mockGet(page, '**/order/10001**', mockOrderList.records[0])
    // 跳转到发表评价页，URL 必须带 orderId + orderItemId + productId 三个参数
    await page.goto('/review/create?orderId=10001&orderItemId=1&productId=1')
    await page.waitForTimeout(500)
    await expect(page.locator('body')).toBeVisible()
  })

  /**
   * 测试2：评价页应显示商品信息和评分组件
   * 小白理解：mock 订单详情接口返回订单数据（含商品信息 items 字段），
   * 打开评价页后，应能看到商品名称和 el-rate 星级组件。
   * 关键点：
   * 1. URL 必须带 orderId=10001&orderItemId=1&productId=1 三个参数
   * 2. mock 订单详情的 items 数组中必须有 id=1 的订单项（匹配 orderItemId=1）
   */
  test('评价页应显示商品信息和评分组件', async ({ page }) => {
    await mockUserCommonGetApis(page)
    // mock 订单详情接口，返回订单列表中的第一条订单（其 items[0].id=1 匹配 orderItemId=1）
    await mockGet(page, '**/order/10001**', mockOrderList.records[0])
    await page.goto('/review/create?orderId=10001&orderItemId=1&productId=1')
    await page.waitForTimeout(500)
    // 验证商品名称可见（评价页会从订单详情的 items 中找到 id=1 的订单项展示）
    await expect(page.getByText('测试商品A - 无线蓝牙耳机').first()).toBeVisible({ timeout: 5000 })
    // 验证评分组件 el-rate 可见（Element Plus 的星级组件 class 名为 el-rate）
    await expect(page.locator('.el-rate').first()).toBeVisible({ timeout: 5000 })
  })

  /**
   * 测试3：选择星级并填写评价内容后提交应成功
   * 小白理解：mock 提交评价接口返回成功，手动点击星级、填写评价内容、点击提交按钮，
   * 验证提交请求被发出并且页面有反馈。
   * 关键点：
   * 1. 评价内容输入框 placeholder 是"说说商品怎么样吧，至少10个字哦"（不是"请输入评价内容"）
   * 2. 评价内容至少 10 个字（前端表单校验规则）
   * 3. 提交按钮文本是"提交评价"
   * 4. 成功提示是"评价提交成功"（包含"提交成功"关键词）
   */
  test('选择星级并填写评价内容后提交应成功', async ({ page }) => {
    await mockUserCommonGetApis(page)
    await mockGet(page, '**/order/10001**', mockOrderList.records[0])
    // mock 发表评价接口，返回成功（addComment 返回 null，但 mock 返回 {id:1} 也能让 code=200）
    await mockPost(page, '**/product/comment**', { id: 1 })
    await page.goto('/review/create?orderId=10001&orderItemId=1&productId=1')
    await page.waitForTimeout(500)
    // 点击星级组件的第一个星星（el-rate 的星星是 .el-rate__icon）
    await page.locator('.el-rate .el-rate__icon').first().click()
    // 在评价内容输入框中填写内容（placeholder 是"说说商品怎么样吧，至少10个字哦"）
    await page.getByPlaceholder('说说商品怎么样吧，至少10个字哦').fill('商品质量很好，物流也快！')
    // 点击提交按钮
    await page.getByRole('button', { name: '提交评价' }).click()
    await page.waitForTimeout(500)
    // 验证提交后出现成功提示（前端 ElMessage.success('评价提交成功')，包含"提交成功"）
    await expect(page.locator('.el-message--success').first()).toBeVisible({ timeout: 5000 })
  })

  /**
   * 测试4：勾选"匿名评价"开关后提交应成功
   * 小白理解：先点击"匿名评价"开关（el-switch，不是复选框），
   * 开启后再提交评价，验证提交成功。
   * 关键点：
   * 1. 匿名评价是 el-switch 组件（不是 checkbox），点击 el-switch 本身即可切换
   * 2. 评价内容至少 10 个字
   */
  test('勾选"匿名评价"开关后提交应成功', async ({ page }) => {
    await mockUserCommonGetApis(page)
    await mockGet(page, '**/order/10001**', mockOrderList.records[0])
    await mockPost(page, '**/product/comment**', { id: 1 })
    await page.goto('/review/create?orderId=10001&orderItemId=1&productId=1')
    await page.waitForTimeout(500)
    // 点击星级
    await page.locator('.el-rate .el-rate__icon').first().click()
    // 填写评价内容
    await page.getByPlaceholder('说说商品怎么样吧，至少10个字哦').fill('匿名评价测试，质量不错。')
    // 点击"匿名评价"开关（Element Plus el-switch 组件，class 为 el-switch）
    // 注意：不是 checkbox，必须点击 el-switch 元素本身
    await page.locator('.anonymous-wrapper .el-switch').first().click()
    // 提交评价
    await page.getByRole('button', { name: '提交评价' }).click()
    await page.waitForTimeout(500)
    // 验证成功提示
    await expect(page.locator('.el-message--success').first()).toBeVisible({ timeout: 5000 })
  })

  /**
   * 测试5：追评页应正常加载
   * 小白理解：打开追评页 URL（带 parentId 参数），验证页面 body 可见。
   * 注意：URL 参数名是 parentId（不是 commentId），否则页面会显示"未找到原始评价"。
   */
  test('追评页应正常加载', async ({ page }) => {
    // mock 评价列表接口（追评页会调用 getCommentList(parentId, ...) 获取原始评价）
    await mockGet(page, '**/product/comment/list**', mockCommentList)
    await page.goto('/review/append?parentId=1')
    await page.waitForTimeout(500)
    await expect(page.locator('body')).toBeVisible()
  })

  /**
   * 测试6：填写追评内容后提交应成功
   * 小白理解：mock 追评接口返回成功，填写追评内容后点击提交，
   * 验证页面有成功反馈。
   * 关键点：
   * 1. URL 参数是 parentId=1（不是 commentId=1）
   * 2. 追评页会调用 getCommentList(parentId, ...) 获取原始评价，需要 mock
   * 3. 追评内容至少 5 个字
   * 4. 追评输入框 placeholder 是"使用一段时间后，再说说商品怎么样吧"
   * 5. 提交按钮文本是"提交追评"
   * 6. 成功提示是"追评提交成功"
   */
  test('填写追评内容后提交应成功', async ({ page }) => {
    await mockUserCommonGetApis(page)
    // mock 评价列表接口（追评页会按 parentId 查询原始评价）
    await mockGet(page, '**/product/comment/list**', mockCommentList)
    // mock 追评接口，返回成功
    await mockPost(page, '**/product/comment/append**', { id: 2 })
    await page.goto('/review/append?parentId=1')
    await page.waitForTimeout(500)
    // 找到追评内容输入框并填写（placeholder 是"使用一段时间后，再说说商品怎么样吧"）
    await page.getByPlaceholder('使用一段时间后，再说说商品怎么样吧').fill('使用一段时间后追评：商品依然很好用！')
    // 点击提交按钮（追评页提交按钮文本是"提交追评"）
    await page.getByRole('button', { name: '提交追评' }).click()
    await page.waitForTimeout(500)
    // 验证追评成功提示
    await expect(page.locator('.el-message--success').first()).toBeVisible({ timeout: 5000 })
  })
})
