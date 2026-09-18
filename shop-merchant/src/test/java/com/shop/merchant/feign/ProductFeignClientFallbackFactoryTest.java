package com.shop.merchant.feign;

import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.product.dto.CommentReplyDTO;
import com.shop.model.product.dto.ProductCreateDTO;
import com.shop.model.product.dto.ProductUpdateDTO;
import com.shop.model.product.vo.CommentVO;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商品服务Feign降级工厂（ProductFeignClientFallbackFactory）的单元测试
 * <p>
 * 这个测试类用来验证：当商品服务挂掉时，FallbackFactory 生成的"降级版"客户端会怎么响应。
 * 简单理解：商品服务（shop-product）可能因为网络问题、超时、服务重启等原因调用失败，
 * 这时候 Feign 框架会自动调用 FallbackFactory.create() 方法生成一个降级实例，
 * 我们测试的就是这个降级实例的每个方法的行为对不对。
 * </p>
 * <p>
 * 商品服务的降级策略分两种：
 * - 查询类接口（listProducts、getProductDetail、getCommentListByShopId）：
 *   降级返回 Result.fail("商品服务暂不可用")，前端能显示友好提示
 * - 写操作接口（createProduct、updateProduct、onShelfProduct、offShelfProduct、replyComment）：
 *   降级直接抛出 BusinessException，因为写操作不能"装作成功"，必须告诉用户操作失败了
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isFalse()
 * </p>
 * <p>
 * 测试覆盖的8个方法：listProducts、getProductDetail、createProduct、updateProduct、
 * onShelfProduct、offShelfProduct、getCommentListByShopId、replyComment
 * </p>
 */
@DisplayName("商品服务降级工厂 ProductFeignClientFallbackFactory 单元测试")
class ProductFeignClientFallbackFactoryTest {

    /** 被测试的降级工厂，直接new出来即可（它没有依赖需要Mock） */
    private final ProductFeignClientFallbackFactory fallbackFactory = new ProductFeignClientFallbackFactory();

    /**
     * 创建降级实例
     * <p>
     * 小白理解：模拟商品服务挂掉的场景，调用 FallbackFactory.create(失败原因) 拿到一个降级版的客户端。
     * 这个降级实例的每个方法都已经被重写过，会按预定的策略返回降级响应。
     * </p>
     *
     * @return 降级版ProductFeignClient实例
     */
    private ProductFeignClient createFallback() {
        // cause参数是失败原因，Feign框架会自动传入真实的异常，这里用RuntimeException模拟
        return fallbackFactory.create(new RuntimeException("商品服务不可用"));
    }

    // ==================== 1. 查询类接口降级：返回友好的失败提示 ====================

    @Nested
    @DisplayName("查询类接口降级：返回Result.fail")
    class QueryFallbackTest {

        @Test
        @DisplayName("listProducts 查询商品列表降级 → 返回失败提示")
        void listProducts_returnsFail() {
            // 场景：商品服务挂了，商家在后台查询商品列表
            ProductFeignClient fallback = createFallback();

            // 调用降级方法
            Result<PageResult<ProductVO>> result = fallback.listProducts(1, 10, null, null, null);

            // 验证：返回失败，提示"商品服务暂不可用"，不抛异常
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("商品服务暂不可用");
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("getProductDetail 查询商品详情降级 → 返回失败提示")
        void getProductDetail_returnsFail() {
            // 场景：商品服务挂了，商家查看某个商品详情
            ProductFeignClient fallback = createFallback();

            // 调用降级方法
            Result<ProductDetailVO> result = fallback.getProductDetail(1L);

            // 验证：返回失败提示
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("商品服务暂不可用");
        }

        @Test
        @DisplayName("getCommentListByShopId 查询评价列表降级 → 返回失败提示")
        void getCommentListByShopId_returnsFail() {
            // 场景：商品服务挂了，商家查看店铺收到的评价列表
            ProductFeignClient fallback = createFallback();

            // 调用降级方法
            Result<PageResult<CommentVO>> result = fallback.getCommentListByShopId(1L, 1, 10, null);

            // 验证：返回失败提示
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("商品服务暂不可用");
        }
    }

    // ==================== 2. 写操作接口降级：抛出业务异常（不能静默失败） ====================

    @Nested
    @DisplayName("写操作接口降级：抛出BusinessException")
    class WriteFallbackTest {

        @Test
        @DisplayName("createProduct 发布商品降级 → 抛出BusinessException")
        void createProduct_throwsBusinessException() {
            // 场景：商品服务挂了，商家发布新商品
            ProductFeignClient fallback = createFallback();

            // 验证：抛出业务异常（写操作不能静默失败，必须告诉用户操作失败）
            assertThatThrownBy(() -> fallback.createProduct(new ProductCreateDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());
        }

        @Test
        @DisplayName("updateProduct 编辑商品降级 → 抛出BusinessException")
        void updateProduct_throwsBusinessException() {
            // 场景：商品服务挂了，商家编辑商品信息
            ProductFeignClient fallback = createFallback();

            // 验证：抛出业务异常
            assertThatThrownBy(() -> fallback.updateProduct(1L, new ProductUpdateDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());
        }

        @Test
        @DisplayName("onShelfProduct 上架商品降级 → 抛出BusinessException")
        void onShelfProduct_throwsBusinessException() {
            // 场景：商品服务挂了，商家上架商品
            ProductFeignClient fallback = createFallback();

            // 验证：抛出业务异常
            assertThatThrownBy(() -> fallback.onShelfProduct(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());
        }

        @Test
        @DisplayName("offShelfProduct 下架商品降级 → 抛出BusinessException")
        void offShelfProduct_throwsBusinessException() {
            // 场景：商品服务挂了，商家下架商品
            ProductFeignClient fallback = createFallback();

            // 验证：抛出业务异常
            assertThatThrownBy(() -> fallback.offShelfProduct(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());
        }

        @Test
        @DisplayName("replyComment 回复评价降级 → 抛出BusinessException")
        void replyComment_throwsBusinessException() {
            // 场景：商品服务挂了，商家回复用户评价
            ProductFeignClient fallback = createFallback();
            CommentReplyDTO dto = new CommentReplyDTO();
            dto.setCommentId(1L);
            dto.setReply("感谢您的反馈");

            // 验证：抛出业务异常
            assertThatThrownBy(() -> fallback.replyComment(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());
        }
    }
}
