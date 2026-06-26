package com.shop.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.shop.product.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品评价服务实现类
 * <p>
 * 实现添加评价、评价列表、商家回复评价、追评等功能。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    /** 评价Mapper，操作product_comment表 */
    private final ProductCommentMapper productCommentMapper;

    /** 商品Mapper，用来检查商品是否存在 */
    private final ProductMapper productMapper;

    /** 订单服务Feign客户端，校验订单归属+标记已评价 */
    private final OrderFeignClient orderFeignClient;

    /** 用户服务Feign客户端，批量查询用户昵称和头像 */
    private final UserFeignClient userFeignClient;

    /** 匿名用户的默认昵称 */
    private static final String ANONYMOUS_NICKNAME = "匿名用户";

    /** 匿名用户的默认头像URL */
    private static final String ANONYMOUS_AVATAR = "";

    /**
     * 添加评价（初始评价）
     * <p>
     * 增强版流程：
     * 1. 校验商品存在
     * 2. 通过Feign调用订单服务校验订单归属当前用户
     * 3. 校验防重复：该 order_item_id 不能已有初始评价
     * 4. 设置 commentType=0（初始评价）、parentId=null、isAnonymous
     * 5. 评价成功后调用Feign标记订单已评价
     * </p>
     */
    @Override
    public void addComment(Long userId, CommentDTO dto) {
        // 1. 检查商品是否存在
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 2. 通过Feign校验订单归属当前用户（防止评价别人的订单）
        //    如果订单服务故障降级返回false，校验失败拒绝评价，保证安全
        Result<Boolean> ownerResult = orderFeignClient.checkOrderOwnership(dto.getOrderId(), userId);
        if (ownerResult == null || !ownerResult.isSuccess() || !Boolean.TRUE.equals(ownerResult.getData())) {
            log.warn("订单归属校验失败: orderId={}, userId={}", dto.getOrderId(), userId);
            throw new BusinessException(ErrorCode.ORDER_NOT_YOURS);
        }

        // 3. 校验防重复：查询product_comment表是否已存在该order_item_id的初始评价
        Long existCount = productCommentMapper.selectCount(
                new LambdaQueryWrapper<ProductComment>()
                        .eq(ProductComment::getOrderItemId, dto.getOrderItemId())
                        .eq(ProductComment::getCommentType, CommentTypeEnum.INITIAL.getCode())
        );
        if (existCount > 0) {
            throw new BusinessException(ErrorCode.COMMENT_ALREADY_EXISTS);
        }

        // 4. 构造评价实体并保存
        ProductComment comment = new ProductComment();
        comment.setProductId(dto.getProductId());
        comment.setOrderItemId(dto.getOrderItemId());
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setImages(dto.getImages());
        comment.setScore(dto.getScore());
        comment.setCommentType(CommentTypeEnum.INITIAL.getCode()); // 初始评价
        comment.setParentId(null); // 初始评价没有父评价
        // isAnonymous：DTO里是Boolean，转成0/1存储
        comment.setIsAnonymous(Boolean.TRUE.equals(dto.getIsAnonymous()) ? 1 : 0);
        productCommentMapper.insert(comment);

        // 5. 评价成功后通过Feign标记订单为已评价（防止重复评价）
        //    这里即使标记失败也不影响评价本身，由对账任务补偿
        try {
            orderFeignClient.markOrderReviewed(dto.getOrderId());
        } catch (Exception e) {
            log.error("标记订单已评价失败（不影响评价本身）: orderId={}", dto.getOrderId(), e);
        }

        log.info("添加评价成功: productId={}, orderId={}, userId={}", dto.getProductId(), dto.getOrderId(), userId);
    }

    /**
     * 评价列表（分页，向后兼容版本）
     * <p>
     * 默认查询全部评价（不筛选评分），保持向后兼容。
     * </p>
     */
    @Override
    public PageResult<CommentVO> getCommentList(Long productId, PageRequest pageRequest) {
        return getCommentList(productId, "all", pageRequest);
    }

    /**
     * 评价列表（分页，支持评分筛选 + 用户信息填充 + 追评列表）
     * <p>
     * 增强版流程：
     * 1. 按评分类型筛选：all=不筛选、good=score>=4、medium=score=3、bad=score<=2
     * 2. 只查询初始评价（comment_type=0）
     * 3. 查询后批量填充用户昵称和头像（通过Feign调用用户服务）
     * 4. 匿名评价（isAnonymous=1）的昵称显示为"匿名用户"，头像显示默认头像
     * 5. 附带追评列表：查询每个初始评价的追评（parent_id=初始评价id），设置到replyList
     * </p>
     */
    @Override
    public PageResult<CommentVO> getCommentList(Long productId, String scoreType, PageRequest pageRequest) {
        Page<ProductComment> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());

        // 1. 构造查询条件：按商品ID筛选 + 只查初始评价
        LambdaQueryWrapper<ProductComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductComment::getProductId, productId);
        wrapper.eq(ProductComment::getCommentType, CommentTypeEnum.INITIAL.getCode());

        // 2. 按评分类型筛选
        if (scoreType != null) {
            switch (scoreType) {
                case "good": // 好评：4-5分
                    wrapper.ge(ProductComment::getScore, 4);
                    break;
                case "medium": // 中评：3分
                    wrapper.eq(ProductComment::getScore, 3);
                    break;
                case "bad": // 差评：1-2分
                    wrapper.le(ProductComment::getScore, 2);
                    break;
                case "all":
                default:
                    // 全部不筛选
                    break;
            }
        }
        wrapper.orderByDesc(ProductComment::getCreateTime);

        // 3. 执行分页查询
        Page<ProductComment> result = productCommentMapper.selectPage(page, wrapper);
        List<ProductComment> records = result.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            PageResult<CommentVO> emptyResult = new PageResult<>();
            emptyResult.setRecords(new ArrayList<>());
            emptyResult.setPagination(0L, pageRequest.getPageNum(), pageRequest.getPageSize());
            return emptyResult;
        }

        // 4. 批量查询用户信息（一次性传入所有userId，避免N+1查询）
        Set<Long> userIds = records.stream()
                .map(ProductComment::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserBriefVO> userMap = batchGetUserInfo(userIds);

        // 5. 查询每个初始评价的追评列表（按parentId批量查询）
        List<Long> commentIds = records.stream()
                .map(ProductComment::getId)
                .collect(Collectors.toList());
        Map<Long, List<ProductComment>> replyMap = batchGetReplies(commentIds);

        // 6. 转换为VO并填充用户信息和追评列表
        List<CommentVO> voList = records.stream().map(comment -> {
            CommentVO vo = convertToVO(comment);
            // 填充用户信息（处理匿名）
            fillUserInfo(vo, comment, userMap);
            // 填充追评列表
            List<ProductComment> replies = replyMap.getOrDefault(comment.getId(), Collections.emptyList());
            List<CommentVO> replyVOList = replies.stream()
                    .map(reply -> {
                        CommentVO replyVO = convertToVO(reply);
                        // 追评也需要填充用户信息（追评人和初始评价人是同一个用户）
                        fillUserInfo(replyVO, reply, userMap);
                        return replyVO;
                    })
                    .collect(Collectors.toList());
            vo.setReplyList(replyVOList);
            return vo;
        }).collect(Collectors.toList());

        PageResult<CommentVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setPagination(result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
        return pageResult;
    }

    /**
     * 按店铺查询评价列表（分页，商家端内部接口）
     * <p>
     * 通过JOIN product表查询该店铺所有商品的评价，支持按"是否已回复"筛选。
     * </p>
     */
    @Override
    public PageResult<CommentVO> getCommentListByShopId(Long shopId, Boolean hasReply, PageRequest pageRequest) {
        Page<ProductComment> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());

        // 调用自定义Mapper方法：JOIN product表按shopId过滤，支持hasReply筛选
        IPage<ProductComment> result = productCommentMapper.selectCommentsByShopId(page, shopId, hasReply);

        // 转换为VO（convertToVOWithProduct会额外设置商品名称）
        List<CommentVO> voList = result.getRecords().stream()
                .map(this::convertToVOWithProduct)
                .collect(Collectors.toList());

        PageResult<CommentVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setPagination(result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
        return pageResult;
    }

    /**
     * 商家回复评价
     * <p>
     * 1. 先查出评价（含商品关联的shopId）
     * 2. 校验该评价对应的商品是否属于当前商家的店铺
     * 3. 更新评价的reply字段
     * </p>
     */
    @Override
    public void replyComment(Long shopId, CommentReplyDTO dto) {
        // 查出评价信息（含商品的shopId，用于校验归属）
        ProductComment comment = productCommentMapper.selectCommentWithShopById(dto.getCommentId());
        if (comment == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "评价不存在");
        }

        // 校验：这条评价对应的商品必须属于当前商家的店铺
        if (!shopId.equals(comment.getShopId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权回复此评价");
        }

        // 更新回复内容
        ProductComment update = new ProductComment();
        update.setId(dto.getCommentId());
        update.setReply(dto.getReply());
        productCommentMapper.updateById(update);

        log.info("商家回复评价成功: commentId={}, shopId={}", dto.getCommentId(), shopId);
    }

    /**
     * 追评（在初始评价之后追加评价）
     * <p>
     * 校验流程：
     * 1. 校验parentId对应的初始评价存在
     * 2. 校验初始评价属于当前用户（只有评价人本人可以追评）
     * 3. 校验未已追评（一个初始评价只能有一条追评）
     * </p>
     * <p>
     * 追评内容、图片来自DTO；评分继承初始评价的评分（追评不需要重新打分）。
     * </p>
     *
     * @param userId 用户ID
     * @param dto    追评参数
     */
    @Override
    public void appendComment(Long userId, CommentAppendDTO dto) {
        // 1. 校验初始评价存在
        ProductComment parentComment = productCommentMapper.selectById(dto.getParentId());
        if (parentComment == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "初始评价不存在");
        }

        // 2. 校验初始评价类型必须是0（防止追评再追评）
        if (!Integer.valueOf(CommentTypeEnum.INITIAL.getCode()).equals(parentComment.getCommentType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "只能对初始评价进行追评");
        }

        // 3. 校验初始评价属于当前用户（只有评价人本人可以追评）
        if (!userId.equals(parentComment.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权对此评价追评");
        }

        // 4. 校验未已追评（防止重复追评）
        Long existAppendCount = productCommentMapper.selectCount(
                new LambdaQueryWrapper<ProductComment>()
                        .eq(ProductComment::getParentId, dto.getParentId())
                        .eq(ProductComment::getCommentType, CommentTypeEnum.APPEND.getCode())
        );
        if (existAppendCount > 0) {
            throw new BusinessException(ErrorCode.COMMENT_ALREADY_EXISTS.getCode(), "该评价已追评，不可重复追评");
        }

        // 5. 创建追评记录（commentType=1，parentId=传入的parentId）
        ProductComment appendComment = new ProductComment();
        appendComment.setProductId(parentComment.getProductId());
        appendComment.setOrderItemId(parentComment.getOrderItemId());
        appendComment.setUserId(userId);
        appendComment.setContent(dto.getContent());
        appendComment.setImages(dto.getImages());
        appendComment.setScore(parentComment.getScore()); // 追评不需要评分，继承初始评价的评分
        appendComment.setCommentType(CommentTypeEnum.APPEND.getCode()); // 追评
        appendComment.setParentId(dto.getParentId()); // 关联到初始评价
        appendComment.setIsAnonymous(parentComment.getIsAnonymous()); // 继承初始评价的匿名设置
        productCommentMapper.insert(appendComment);

        log.info("追评成功: parentId={}, userId={}", dto.getParentId(), userId);
    }

    /**
     * 管理端-查询全平台评价列表
     * <p>
     * 实现思路和 getCommentList 完全一致，唯一的区别是不限定 productId（管理员看全平台）。
     * 流程：
     * 1. 按评分类型筛选：all=不筛选、good=score>=4、medium=score=3、bad=score<=2
     * 2. 只查询初始评价（comment_type=0），追评会通过 batchGetReplies 附带
     * 3. 批量填充用户昵称头像、附带追评列表
     * </p>
     */
    @Override
    public PageResult<CommentVO> getAdminCommentList(String scoreType, PageRequest pageRequest) {
        Page<ProductComment> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());

        // 1. 构造查询条件：只查初始评价（不限商品ID）
        LambdaQueryWrapper<ProductComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductComment::getCommentType, CommentTypeEnum.INITIAL.getCode());

        // 2. 按评分类型筛选（逻辑和 getCommentList 一致）
        if (scoreType != null) {
            switch (scoreType) {
                case "good": // 好评：4-5分
                    wrapper.ge(ProductComment::getScore, 4);
                    break;
                case "medium": // 中评：3分
                    wrapper.eq(ProductComment::getScore, 3);
                    break;
                case "bad": // 差评：1-2分
                    wrapper.le(ProductComment::getScore, 2);
                    break;
                case "all":
                default:
                    // 全部不筛选
                    break;
            }
        }
        wrapper.orderByDesc(ProductComment::getCreateTime);

        // 3. 执行分页查询
        Page<ProductComment> result = productCommentMapper.selectPage(page, wrapper);
        List<ProductComment> records = result.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            PageResult<CommentVO> emptyResult = new PageResult<>();
            emptyResult.setRecords(new ArrayList<>());
            emptyResult.setPagination(0L, pageRequest.getPageNum(), pageRequest.getPageSize());
            return emptyResult;
        }

        // 4. 批量查询用户信息（一次性传入所有userId，避免N+1查询）
        Set<Long> userIds = records.stream()
                .map(ProductComment::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserBriefVO> userMap = batchGetUserInfo(userIds);

        // 5. 查询每个初始评价的追评列表（按parentId批量查询）
        List<Long> commentIds = records.stream()
                .map(ProductComment::getId)
                .collect(Collectors.toList());
        Map<Long, List<ProductComment>> replyMap = batchGetReplies(commentIds);

        // 6. 转换为VO并填充用户信息和追评列表
        List<CommentVO> voList = records.stream().map(comment -> {
            CommentVO vo = convertToVO(comment);
            // 填充用户信息（处理匿名）
            fillUserInfo(vo, comment, userMap);
            // 填充追评列表
            List<ProductComment> replies = replyMap.getOrDefault(comment.getId(), Collections.emptyList());
            List<CommentVO> replyVOList = replies.stream()
                    .map(reply -> {
                        CommentVO replyVO = convertToVO(reply);
                        // 追评也需要填充用户信息（追评人和初始评价人是同一个用户）
                        fillUserInfo(replyVO, reply, userMap);
                        return replyVO;
                    })
                    .collect(Collectors.toList());
            vo.setReplyList(replyVOList);
            return vo;
        }).collect(Collectors.toList());

        PageResult<CommentVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setPagination(result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
        return pageResult;
    }

    /**
     * 管理端-删除评价
     * <p>
     * 逻辑删除：BaseEntity 的 deleted 字段有 @TableLogic 注解，
     * 调用 deleteById 时 MyBatis-Plus 会自动转成 UPDATE 设置 deleted=1，不会真的从表里删掉。
     * </p>
     */
    @Override
    public void deleteComment(Long commentId) {
        // 先校验评价存在，不存在则提示管理员
        ProductComment comment = productCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "评价不存在");
        }
        // 逻辑删除（@TableLogic 会把 DELETE 转成 UPDATE deleted=1）
        productCommentMapper.deleteById(commentId);
        log.info("管理员删除评价成功: commentId={}", commentId);
    }

    /**
     * 管理端-管理员回复评价
     * <p>
     * 管理员可以回复任意评价，不需要校验店铺归属（和商家回复的区别）。
     * 直接更新评价的 reply 字段。
     * </p>
     */
    @Override
    public void adminReplyComment(Long commentId, String reply) {
        // 1. 校验评价存在
        ProductComment comment = productCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "评价不存在");
        }
        // 2. 更新回复内容
        ProductComment update = new ProductComment();
        update.setId(commentId);
        update.setReply(reply);
        productCommentMapper.updateById(update);
        log.info("管理员回复评价成功: commentId={}", commentId);
    }

    /**
     * 批量查询用户信息（通过Feign调用用户服务）
     * <p>
     * 把userId集合传入shop-user的批量查询接口，返回userId到UserBriefVO的映射。
     * 用户服务故障时返回空映射，调用方会跳过用户信息填充。
     * </p>
     *
     * @param userIds 用户ID集合
     * @return userId到用户简要信息的映射
     */
    private Map<Long, UserBriefVO> batchGetUserInfo(Set<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        try {
            Result<List<UserBriefVO>> result = userFeignClient.batchGetUserInfo(new ArrayList<>(userIds));
            if (result != null && result.isSuccess() && result.getData() != null) {
                return result.getData().stream()
                        .collect(Collectors.toMap(UserBriefVO::getUserId, v -> v, (a, b) -> a));
            }
        } catch (Exception e) {
            log.error("批量查询用户信息失败", e);
        }
        return Collections.emptyMap();
    }

    /**
     * 批量查询追评列表（按parentId批量查询）
     * <p>
     * 一次查询出所有初始评价的追评，按parentId分组返回，避免对每条初始评价单独查询。
     * </p>
     *
     * @param commentIds 初始评价ID列表
     * @return parentId到追评列表的映射
     */
    private Map<Long, List<ProductComment>> batchGetReplies(List<Long> commentIds) {
        if (CollectionUtils.isEmpty(commentIds)) {
            return Collections.emptyMap();
        }
        List<ProductComment> replies = productCommentMapper.selectList(
                new LambdaQueryWrapper<ProductComment>()
                        .in(ProductComment::getParentId, commentIds)
                        .eq(ProductComment::getCommentType, CommentTypeEnum.APPEND.getCode())
                        .orderByAsc(ProductComment::getCreateTime)
        );
        return replies.stream().collect(Collectors.groupingBy(ProductComment::getParentId));
    }

    /**
     * 填充用户信息到评价VO（处理匿名）
     * <p>
     * - 匿名评价（isAnonymous=1）：昵称显示"匿名用户"，头像显示默认头像
     * - 非匿名评价：从userMap中取昵称和头像，取不到则留空
     * </p>
     *
     * @param vo       评价VO
     * @param comment  评价实体
     * @param userMap  用户信息映射
     */
    private void fillUserInfo(CommentVO vo, ProductComment comment, Map<Long, UserBriefVO> userMap) {
        if (Integer.valueOf(1).equals(comment.getIsAnonymous())) {
            // 匿名评价：显示"匿名用户"，头像留空
            vo.setUserNickname(ANONYMOUS_NICKNAME);
            vo.setUserAvatar(ANONYMOUS_AVATAR);
        } else {
            // 非匿名：从用户信息映射中取昵称和头像
            UserBriefVO user = userMap.get(comment.getUserId());
            if (user != null) {
                vo.setUserNickname(user.getNickname());
                vo.setUserAvatar(user.getAvatar());
            }
        }
    }

    /**
     * ProductComment实体转CommentVO（基础转换，不含商品名称）
     * <p>
     * 注意：用户昵称和头像需要从用户服务获取，这里只设置userId，
     * 调用方在getCommentList中会通过fillUserInfo批量填充。
     * </p>
     *
     * @param comment 评价实体
     * @return 评价VO
     */
    private CommentVO convertToVO(ProductComment comment) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setProductId(comment.getProductId());
        vo.setUserId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setImages(comment.getImages());
        vo.setScore(comment.getScore());
        vo.setReply(comment.getReply());
        vo.setIsAnonymous(comment.getIsAnonymous());
        vo.setCommentType(comment.getCommentType());
        vo.setParentId(comment.getParentId());
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }

    /**
     * ProductComment实体转CommentVO（含商品名称，商家端用）
     * <p>和convertToVO的区别：这个会额外设置productName字段</p>
     *
     * @param comment 评价实体（含JOIN查询的productName）
     * @return 评价VO（含商品名称）
     */
    private CommentVO convertToVOWithProduct(ProductComment comment) {
        CommentVO vo = convertToVO(comment);
        vo.setProductName(comment.getProductName());
        return vo;
    }
}
