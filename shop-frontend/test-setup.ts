/**
 * 全局测试初始化文件
 * <p>
 * 所有 Vue 端（web-user / web-merchant / web-admin）的测试启动前都会执行这个文件。
 * 作用：引入 @testing-library/jest-dom，让测试能用 toBeInTheDocument、toBeVisible 等断言。
 * </p>
 */
import '@testing-library/jest-dom/vitest'
