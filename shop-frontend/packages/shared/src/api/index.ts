/**
 * API模块统一导出
 * 从这里引入所有API方法，方便使用
 */
export { get, post, put, del, cancelAllRequests } from './request'
export { userLogin, userRegister, getUserInfo, updateUserInfo, userLogout, sendVerifyCode, getAddressList, addAddress, updateAddress, deleteAddress, setDefaultAddress, addFavorite, removeFavorite, getFavoriteList, getFootprintList } from './modules/user'
export { merchantLogin, merchantApply, getMerchantInfo, updateMerchantSettings, merchantLogout, getSettlementAccount, addSettlementAccount, updateSettlementAccount, getSettlementRecords, applyWithdraw, getWithdrawList } from './modules/merchant'
export {
  getProductList,
  getProductDetail,
  searchProducts,
  getCategoryTree,
  getHotKeywords,
  getSuggest,
  getMerchantProductList,
  createProduct,
  updateProduct,
  toggleProductStatus,
  deleteProduct,
  getMerchantCommentList,
  replyComment,
  // 用户端评价接口：发表评价、追评、查询商品评价列表
  addComment,
  appendComment,
  getCommentList,
  // 管理端评价接口：列表、删除、管理员回复
  getAdminCommentList,
  deleteComment,
  adminReplyComment,
  // 商品推荐接口：热销、新品、相关、猜你喜欢
  getHotProducts,
  getNewProducts,
  getRelatedProducts,
  getGuessProducts,
} from './modules/product'
export {
  getCartList,
  addToCart,
  updateCartItem,
  removeCartItem,
  clearCart,
  toggleCartAllChecked,
  getCartCount,
} from './modules/cart'
export {
  createOrder,
  getOrderList,
  getOrderDetail,
  cancelOrder,
  confirmReceive,
  applyRefund,
  getMerchantOrderList,
  shipOrder,
  agreeRefund,
  rejectRefund,
} from './modules/order'
export { createPayment, mockPay, getPaymentByOrderNo } from './modules/payment'
export { getDashboardStats, getSalesTrend, getDataOverview, getProductRank } from './modules/stats'
export * from './modules/admin'
export * from './modules/notification'
export * from './modules/coupon'
export * from './modules/promotion'
export * from './modules/seckill'
