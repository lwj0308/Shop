package com.shop.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.product.dto.CommentAppendDTO;
import com.shop.model.product.dto.CommentDTO;
import com.shop.model.product.dto.CommentReplyDTO;
import com.shop.model.product.entity.Product;
import com.shop.model.product.entity.ProductComment;
import com.shop.model.product.enums.CommentTypeEnum;
import com.shop.model.product.vo.CommentVO;
import com.shop.model.user.vo.UserBriefVO;
import com.shop.product.feign.OrderFeignClient;
import com.shop.product.feign.UserFeignClient;
import com.shop.product.mapper.ProductCommentMapper;
import com.shop.product.mapper.ProductMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 商品评价服务实现类（CommentServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证评价提交、追评、商家回复、管理员回复、删除等核心方法。
 * 简单理解：我们把所有依赖（Mapper、Feign）都"假装"一下（Mock），
 * 这样测试不需要真的连数据库和远程服务，跑得又快又稳定。
 * </p>
 * <p>
 * 覆盖的方法：addComment（添加评价）、appendComment（追评）、replyComment（商家回复）、
 * deleteComment（删除评价）、adminReplyComment（管理员回复）
 * </p>
 */
@DisplayName("评价服务 CommentServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentServiceImplTest {

    // ==================== Mock 依赖（都是假的） ====================

    /** 评价 Mapper，操作 product_comment 表 */
    @Mock
    private ProductCommentMapper productCommentMapper;
    /** 商品 Mapper，校验商品是否存在 */
    @Mock
    private ProductMapper productMapper;
    /** 订单服务 Feign，校验订单归属 + 标记已评价 */
    @Mock
    private OrderFeignClient orderFeignClient;
    /** 用户服务 Feign，批量查询用户昵称和头像 */
    @Mock
    private UserFeignClient userFeignClient;

    /** 被测试的评价服务，Mockito 会自动把上面所有 Mock 注入进来 */
    @InjectMocks
    private CommentServiceImpl commentService;

    /** 常用测试数据：用户ID */
    private static final Long USER_ID = 1001L;
    /** 常用测试数据：另一个用户ID（测试非本人操作用） */
    private static final Long OTHER_USER_ID = 1002L;
    /** 常用测试数据：商品ID */
    private static final Long PRODUCT_ID = 2001L;
    /** 常用测试数据：订单ID */
    private static final Long ORDER_ID = 3001L;
    /** 常用测试数据：订单明细ID */
    private static final Long ORDER_ITEM_ID = 4001L;
    /** 常用测试数据：评价ID */
    private static final Long COMMENT_ID = 5001L;
    /** 常用测试数据：店铺ID */
    private static final Long SHOP_ID = 6001L;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * CommentServiceImpl 用了 LambdaQueryWrapper 查询评价，
     * 需要手动初始化 ProductComment 实体的缓存，否则会报
     * "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProductComment.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造评价请求DTO
     *
     * @param isAnonymous 是否匿名
     * @param score       评分1-5
     * @return 构造好的CommentDTO
     */
    private CommentDTO buildCommentDTO(Boolean isAnonymous, Integer score) {
        CommentDTO dto = new CommentDTO();
        dto.setProductId(PRODUCT_ID);
        dto.setOrderId(ORDER_ID);
        dto.setOrderItemId(ORDER_ITEM_ID);
        dto.setContent("商品质量很好，物流也快！");
        dto.setImages(Arrays.asList("img1.jpg", "img2.jpg"));
        dto.setScore(score);
        dto.setIsAnonymous(isAnonymous);
        return dto;
    }

    /**
     * 构造追评请求DTO
     *
     * @param parentId 父评价ID（初始评价ID）
     * @return 构造好的CommentAppendDTO
     */
    private CommentAppendDTO buildAppendDTO(Long parentId) {
        CommentAppendDTO dto = new CommentAppendDTO();
        dto.setParentId(parentId);
        dto.setContent("用了一段时间，依然很好！");
        dto.setImages(Arrays.asList("append1.jpg"));
        return dto;
    }

    /**
     * 构造商家回复评价DTO
     *
     * @param commentId 评价ID
     * @param reply     回复内容
     * @return 构造好的CommentReplyDTO
     */
    private CommentReplyDTO buildReplyDTO(Long commentId, String reply) {
        CommentReplyDTO dto = new CommentReplyDTO();
        dto.setCommentId(commentId);
        dto.setReply(reply);
        return dto;
    }

    /**
     * 构造一条评价实体
     *
     * @param id          评价ID
     * @param userId      评价用户ID
     * @param commentType 评价类型：0初始 1追评
     * @param isAnonymous 是否匿名：0否 1是
     * @return 构造好的ProductComment
     */
    private ProductComment buildComment(Long id, Long userId, Integer commentType, Integer isAnonymous) {
        ProductComment comment = new ProductComment();
        comment.setId(id);
        comment.setProductId(PRODUCT_ID);
        comment.setOrderItemId(ORDER_ITEM_ID);
        comment.setUserId(userId);
        comment.setContent("测试评价内容");
        comment.setScore(5);
        comment.setCommentType(commentType);
        comment.setIsAnonymous(isAnonymous);
        comment.setParentId(null);
        return comment;
    }

    // ==================== 1. addComment 添加评价测试 ====================

    @Nested
    @DisplayName("addComment 添加评价（初始评价）")
    class AddCommentTest {

        @Test
        @DisplayName("商品不存在 → 抛出 PRODUCT_NOT_FOUND 异常")
        void addComment_productNotExists_throwsException() {
            // 场景：评价的商品不存在
            CommentDTO dto = buildCommentDTO(false, 5);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);

            assertThatThrownBy(() -> commentService.addComment(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PRODUCT_NOT_FOUND.getCode());

            // 验证没有调用订单服务校验订单归属
            verify(orderFeignClient, never()).checkOrderOwnership(anyLong(), anyLong());
            // 验证没有插入评价
            verify(productCommentMapper, never()).insert(any(ProductComment.class));
        }

        @Test
        @DisplayName("订单归属校验失败：Feign返回false → 抛出 ORDER_NOT_YOURS 异常")
        void addComment_orderNotYours_throwsException() {
            // 场景：用户尝试评价别人的订单
            CommentDTO dto = buildCommentDTO(false, 5);
            Product product = new Product();
            product.setId(PRODUCT_ID);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
            // 模拟订单服务返回"订单不属于该用户"
            when(orderFeignClient.checkOrderOwnership(ORDER_ID, USER_ID))
                    .thenReturn(Result.success(false));

            assertThatThrownBy(() -> commentService.addComment(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ORDER_NOT_YOURS.getCode());

            verify(productCommentMapper, never()).insert(any(ProductComment.class));
        }

        @Test
        @DisplayName("订单服务降级：Feign返回null → 抛出 ORDER_NOT_YOURS 异常（安全降级）")
        void addComment_feignReturnNull_throwsException() {
            // 场景：订单服务故障降级返回null，为保证安全拒绝评价
            CommentDTO dto = buildCommentDTO(false, 5);
            Product product = new Product();
            product.setId(PRODUCT_ID);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
            when(orderFeignClient.checkOrderOwnership(ORDER_ID, USER_ID)).thenReturn(null);

            assertThatThrownBy(() -> commentService.addComment(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.ORDER_NOT_YOURS.getCode());
        }

        @Test
        @DisplayName("防重复评价：该订单明细已有初始评价 → 抛出 COMMENT_ALREADY_EXISTS 异常")
        void addComment_alreadyExists_throwsException() {
            // 场景：用户对同一订单商品重复评价
            CommentDTO dto = buildCommentDTO(false, 5);
            Product product = new Product();
            product.setId(PRODUCT_ID);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
            // 订单归属校验通过
            when(orderFeignClient.checkOrderOwnership(ORDER_ID, USER_ID))
                    .thenReturn(Result.success(true));
            // 模拟已存在初始评价（selectCount 返回1）
            when(productCommentMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> commentService.addComment(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.COMMENT_ALREADY_EXISTS.getCode());

            verify(productCommentMapper, never()).insert(any(ProductComment.class));
        }

        @Test
        @DisplayName("正常评价（非匿名）：插入评价，commentType=0，isAnonymous=0，调用Feign标记订单已评价")
        void addComment_normal_notAnonymous() {
            // 场景：用户正常发表非匿名评价
            CommentDTO dto = buildCommentDTO(false, 5);
            Product product = new Product();
            product.setId(PRODUCT_ID);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
            when(orderFeignClient.checkOrderOwnership(ORDER_ID, USER_ID))
                    .thenReturn(Result.success(true));
            when(productCommentMapper.selectCount(any())).thenReturn(0L); // 没有重复评价

            commentService.addComment(USER_ID, dto);

            // 捕获插入的评价实体，验证字段
            ArgumentCaptor<ProductComment> captor = ArgumentCaptor.forClass(ProductComment.class);
            verify(productCommentMapper).insert(captor.capture());
            ProductComment saved = captor.getValue();
            assertThat(saved.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(saved.getOrderItemId()).isEqualTo(ORDER_ITEM_ID);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getScore()).isEqualTo(5);
            // 初始评价：commentType=0
            assertThat(saved.getCommentType()).isEqualTo(CommentTypeEnum.INITIAL.getCode());
            // 初始评价没有父评价
            assertThat(saved.getParentId()).isNull();
            // 非匿名
            assertThat(saved.getIsAnonymous()).isEqualTo(0);

            // 验证调用了Feign标记订单已评价
            verify(orderFeignClient).markOrderReviewed(ORDER_ID);
        }

        @Test
        @DisplayName("正常评价（匿名）：isAnonymous=1，调用Feign标记订单已评价")
        void addComment_normal_anonymous() {
            // 场景：用户发表匿名评价，昵称会显示为"匿名用户"
            CommentDTO dto = buildCommentDTO(true, 4);
            Product product = new Product();
            product.setId(PRODUCT_ID);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
            when(orderFeignClient.checkOrderOwnership(ORDER_ID, USER_ID))
                    .thenReturn(Result.success(true));
            when(productCommentMapper.selectCount(any())).thenReturn(0L);

            commentService.addComment(USER_ID, dto);

            ArgumentCaptor<ProductComment> captor = ArgumentCaptor.forClass(ProductComment.class);
            verify(productCommentMapper).insert(captor.capture());
            ProductComment saved = captor.getValue();
            // 匿名评价：isAnonymous=1
            assertThat(saved.getIsAnonymous()).isEqualTo(1);
            verify(orderFeignClient).markOrderReviewed(ORDER_ID);
        }

        @Test
        @DisplayName("标记订单已评价失败：不影响评价本身（弱依赖）")
        void addComment_markReviewedFail_notAffectMain() {
            // 场景：评价插入成功，但Feign标记订单已评价失败（订单服务挂了）
            // 评价本身不应受影响，由对账任务补偿
            CommentDTO dto = buildCommentDTO(false, 5);
            Product product = new Product();
            product.setId(PRODUCT_ID);
            when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
            when(orderFeignClient.checkOrderOwnership(ORDER_ID, USER_ID))
                    .thenReturn(Result.success(true));
            when(productCommentMapper.selectCount(any())).thenReturn(0L);
            // 模拟标记已评价抛异常
            doThrow(new RuntimeException("订单服务不可用"))
                    .when(orderFeignClient).markOrderReviewed(ORDER_ID);

            // 不抛异常即可（弱依赖）
            commentService.addComment(USER_ID, dto);

            // 验证评价仍然插入成功
            verify(productCommentMapper).insert(any(ProductComment.class));
        }
    }

    // ==================== 2. appendComment 追评测试 ====================

    @Nested
    @DisplayName("appendComment 追评")
    class AppendCommentTest {

        @Test
        @DisplayName("初始评价不存在 → 抛出 PARAM_ERROR 异常")
        void appendComment_parentNotExists_throwsException() {
            // 场景：追评时传入的parentId对应的初始评价不存在
            CommentAppendDTO dto = buildAppendDTO(COMMENT_ID);
            when(productCommentMapper.selectById(COMMENT_ID)).thenReturn(null);

            assertThatThrownBy(() -> commentService.appendComment(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            verify(productCommentMapper, never()).insert(any(ProductComment.class));
        }

        @Test
        @DisplayName("父评价类型错误：父评价是追评不是初始评价 → 抛出 PARAM_ERROR 异常")
        void appendComment_parentNotInitial_throwsException() {
            // 场景：用户尝试对一条追评再追评（追评的追评）
            CommentAppendDTO dto = buildAppendDTO(COMMENT_ID);
            // 父评价是追评类型（commentType=1）
            ProductComment parent = buildComment(COMMENT_ID, USER_ID,
                    CommentTypeEnum.APPEND.getCode(), 0);
            when(productCommentMapper.selectById(COMMENT_ID)).thenReturn(parent);

            assertThatThrownBy(() -> commentService.appendComment(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());
        }

        @Test
        @DisplayName("非本人评价追评：父评价属于其他用户 → 抛出 FORBIDDEN 异常")
        void appendComment_notOwner_throwsForbidden() {
            // 场景：用户A尝试对用户B的初始评价追评
            CommentAppendDTO dto = buildAppendDTO(COMMENT_ID);
            // 父评价属于用户1002，当前用户是1001
            ProductComment parent = buildComment(COMMENT_ID, OTHER_USER_ID,
                    CommentTypeEnum.INITIAL.getCode(), 0);
            when(productCommentMapper.selectById(COMMENT_ID)).thenReturn(parent);

            assertThatThrownBy(() -> commentService.appendComment(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());
        }

        @Test
        @DisplayName("已追评：该初始评价已有一条追评 → 抛出 COMMENT_ALREADY_EXISTS 异常")
        void appendComment_alreadyAppended_throwsException() {
            // 场景：用户对同一初始评价重复追评
            CommentAppendDTO dto = buildAppendDTO(COMMENT_ID);
            ProductComment parent = buildComment(COMMENT_ID, USER_ID,
                    CommentTypeEnum.INITIAL.getCode(), 0);
            when(productCommentMapper.selectById(COMMENT_ID)).thenReturn(parent);
            // 模拟已存在追评
            when(productCommentMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> commentService.appendComment(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.COMMENT_ALREADY_EXISTS.getCode());
        }

        @Test
        @DisplayName("正常追评：插入追评，commentType=1，parentId=初始评价ID，继承评分和匿名设置")
        void appendComment_normal_insert() {
            // 场景：用户正常追评，验证追评字段正确
            CommentAppendDTO dto = buildAppendDTO(COMMENT_ID);
            // 初始评价是匿名的，5分
            ProductComment parent = buildComment(COMMENT_ID, USER_ID,
                    CommentTypeEnum.INITIAL.getCode(), 1);
            parent.setScore(5);
            when(productCommentMapper.selectById(COMMENT_ID)).thenReturn(parent);
            when(productCommentMapper.selectCount(any())).thenReturn(0L); // 没有已追评

            commentService.appendComment(USER_ID, dto);

            ArgumentCaptor<ProductComment> captor = ArgumentCaptor.forClass(ProductComment.class);
            verify(productCommentMapper).insert(captor.capture());
            ProductComment saved = captor.getValue();
            // 追评类型：commentType=1
            assertThat(saved.getCommentType()).isEqualTo(CommentTypeEnum.APPEND.getCode());
            // parentId 指向初始评价
            assertThat(saved.getParentId()).isEqualTo(COMMENT_ID);
            // 继承初始评价的评分
            assertThat(saved.getScore()).isEqualTo(5);
            // 继承初始评价的匿名设置
            assertThat(saved.getIsAnonymous()).isEqualTo(1);
            // 追评关联的商品ID和订单明细ID与初始评价一致
            assertThat(saved.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(saved.getOrderItemId()).isEqualTo(ORDER_ITEM_ID);
        }
    }

    // ==================== 3. replyComment 商家回复评价测试 ====================

    @Nested
    @DisplayName("replyComment 商家回复评价")
    class ReplyCommentTest {

        @Test
        @DisplayName("评价不存在 → 抛出 PARAM_ERROR 异常")
        void replyComment_notExists_throwsException() {
            // 场景：商家回复一条不存在的评价
            CommentReplyDTO dto = buildReplyDTO(COMMENT_ID, "感谢评价！");
            when(productCommentMapper.selectCommentWithShopById(COMMENT_ID)).thenReturn(null);

            assertThatThrownBy(() -> commentService.replyComment(SHOP_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            verify(productCommentMapper, never()).updateById(any(ProductComment.class));
        }

        @Test
        @DisplayName("归属校验失败：评价对应的商品不属于当前商家店铺 → 抛出 FORBIDDEN 异常")
        void replyComment_notOwner_throwsForbidden() {
            // 场景：商家A想回复商家B商品的评价
            CommentReplyDTO dto = buildReplyDTO(COMMENT_ID, "感谢评价！");
            // 评价对应的商品属于店铺6002，当前商家是店铺6001
            ProductComment comment = buildComment(COMMENT_ID, USER_ID,
                    CommentTypeEnum.INITIAL.getCode(), 0);
            comment.setShopId(6002L); // 属于其他店铺
            when(productCommentMapper.selectCommentWithShopById(COMMENT_ID)).thenReturn(comment);

            assertThatThrownBy(() -> commentService.replyComment(SHOP_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

            verify(productCommentMapper, never()).updateById(any(ProductComment.class));
        }

        @Test
        @DisplayName("正常回复：调用updateById更新reply字段")
        void replyComment_normal_updateReply() {
            // 场景：商家回复自己店铺商品的评价
            CommentReplyDTO dto = buildReplyDTO(COMMENT_ID, "感谢您的支持！");
            ProductComment comment = buildComment(COMMENT_ID, USER_ID,
                    CommentTypeEnum.INITIAL.getCode(), 0);
            comment.setShopId(SHOP_ID); // 属于当前商家店铺
            when(productCommentMapper.selectCommentWithShopById(COMMENT_ID)).thenReturn(comment);

            commentService.replyComment(SHOP_ID, dto);

            // 验证调用了updateById更新回复内容
            ArgumentCaptor<ProductComment> captor = ArgumentCaptor.forClass(ProductComment.class);
            verify(productCommentMapper).updateById(captor.capture());
            ProductComment updated = captor.getValue();
            assertThat(updated.getId()).isEqualTo(COMMENT_ID);
            assertThat(updated.getReply()).isEqualTo("感谢您的支持！");
        }
    }

    // ==================== 4. deleteComment 删除评价测试 ====================

    @Nested
    @DisplayName("deleteComment 管理员删除评价")
    class DeleteCommentTest {

        @Test
        @DisplayName("评价不存在 → 抛出 PARAM_ERROR 异常")
        void deleteComment_notExists_throwsException() {
            when(productCommentMapper.selectById(COMMENT_ID)).thenReturn(null);

            assertThatThrownBy(() -> commentService.deleteComment(COMMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            verify(productCommentMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("正常删除：调用deleteById（@TableLogic 会转为逻辑删除）")
        void deleteComment_normal_softDelete() {
            // 场景：管理员删除评价，MyBatis-Plus 会自动转为 UPDATE deleted=1
            ProductComment comment = buildComment(COMMENT_ID, USER_ID,
                    CommentTypeEnum.INITIAL.getCode(), 0);
            when(productCommentMapper.selectById(COMMENT_ID)).thenReturn(comment);

            commentService.deleteComment(COMMENT_ID);

            // 验证调用了 deleteById（实际是逻辑删除）
            verify(productCommentMapper).deleteById(COMMENT_ID);
        }
    }

    // ==================== 5. adminReplyComment 管理员回复评价测试 ====================

    @Nested
    @DisplayName("adminReplyComment 管理员回复评价")
    class AdminReplyCommentTest {

        @Test
        @DisplayName("评价不存在 → 抛出 PARAM_ERROR 异常")
        void adminReplyComment_notExists_throwsException() {
            when(productCommentMapper.selectById(COMMENT_ID)).thenReturn(null);

            assertThatThrownBy(() -> commentService.adminReplyComment(COMMENT_ID, "官方回复"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            verify(productCommentMapper, never()).updateById(any(ProductComment.class));
        }

        @Test
        @DisplayName("正常回复：调用updateById更新reply字段（管理员可回复任意评价）")
        void adminReplyComment_normal_updateReply() {
            // 场景：管理员回复任意评价，不需要校验店铺归属
            ProductComment comment = buildComment(COMMENT_ID, USER_ID,
                    CommentTypeEnum.INITIAL.getCode(), 0);
            when(productCommentMapper.selectById(COMMENT_ID)).thenReturn(comment);

            commentService.adminReplyComment(COMMENT_ID, "官方回复内容");

            ArgumentCaptor<ProductComment> captor = ArgumentCaptor.forClass(ProductComment.class);
            verify(productCommentMapper).updateById(captor.capture());
            ProductComment updated = captor.getValue();
            assertThat(updated.getId()).isEqualTo(COMMENT_ID);
            assertThat(updated.getReply()).isEqualTo("官方回复内容");
        }
    }
}
