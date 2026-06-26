package com.shop.product.service;

import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.model.product.dto.CommentAppendDTO;
import com.shop.model.product.dto.CommentDTO;
import com.shop.model.product.dto.CommentReplyDTO;
import com.shop.model.product.vo.CommentVO;

/**
 * 商品评价服务接口
 * <p>
 * 定义评价相关的业务方法，包括添加评价、评价列表、商家回复评价、追评。
 * 实现类在 CommentServiceImpl 中，具体逻辑去看那里。
 * </p>
 */
public interface CommentService {

    /**
     * 添加评价（初始评价）
     * <p>
     * 用户购买商品后发表的评价，会校验订单归属和防重复。
     * </p>
     *
     * @param userId 用户ID
     * @param dto    评价参数
     */
    void addComment(Long userId, CommentDTO dto);

    /**
     * 评价列表（分页，按商品ID查询，C端公开接口）
     * <p>
     * 默认查询全部评价（不筛选评分），保持向后兼容。
     * </p>
     *
     * @param productId   商品ID
     * @param pageRequest 分页参数
     * @return 分页评价列表
     */
    PageResult<CommentVO> getCommentList(Long productId, PageRequest pageRequest);

    /**
     * 评价列表（分页，按商品ID查询 + 评分筛选，C端公开接口）
     * <p>
     * 支持按评分类型筛选（全部/好评/中评/差评），并批量填充用户昵称和头像、附带追评列表。
     * </p>
     *
     * @param productId   商品ID
     * @param scoreType   评分类型：all=全部 good=好评(4-5分) medium=中评(3分) bad=差评(1-2分)
     * @param pageRequest 分页参数
     * @return 分页评价列表（含用户信息和追评列表）
     */
    PageResult<CommentVO> getCommentList(Long productId, String scoreType, PageRequest pageRequest);

    /**
     * 按店铺查询评价列表（分页，商家端内部接口）
     * <p>商家在评价管理页面查看自己店铺商品收到的所有评价</p>
     *
     * @param shopId     店铺ID
     * @param hasReply   是否已回复：true只看已回复，false只看未回复，null看全部
     * @param pageRequest 分页参数
     * @return 分页评价列表（含商品名称）
     */
    PageResult<CommentVO> getCommentListByShopId(Long shopId, Boolean hasReply, PageRequest pageRequest);

    /**
     * 商家回复评价
     * <p>商家对用户的评价进行回复，需校验该评价对应的商品属于该商家店铺</p>
     *
     * @param shopId 店铺ID（用于校验评价归属）
     * @param dto    回复参数（评价ID + 回复内容）
     */
    void replyComment(Long shopId, CommentReplyDTO dto);

    /**
     * 追评（在初始评价之后追加评价）
     * <p>
     * 用户在初始评价之后，可以追加一条追评。追评关联到初始评价（通过 parentId）。
     * 校验：
     * - 初始评价必须存在
     * - 初始评价必须属于当前用户
     * - 不能重复追评（一个初始评价只能有一条追评）
     * </p>
     *
     * @param userId 用户ID
     * @param dto    追评参数
     */
    void appendComment(Long userId, CommentAppendDTO dto);

    /**
     * 管理端-查询全平台评价列表
     * <p>
     * 管理员查看全平台所有商品的评价，不限商品ID。
     * 支持按评分类型筛选，并批量填充用户昵称头像和追评列表（实现思路和 getCommentList 类似，只是去掉了 productId 过滤）。
     * </p>
     *
     * @param scoreType   评分类型：all=全部 good=好评(4-5分) medium=中评(3分) bad=差评(1-2分)，可为null（等于全部）
     * @param pageRequest 分页参数
     * @return 分页评价列表（含用户信息和追评列表）
     */
    PageResult<CommentVO> getAdminCommentList(String scoreType, PageRequest pageRequest);

    /**
     * 管理端-删除评价
     * <p>
     * 管理员删除任意一条评价（逻辑删除，把 deleted 字段标记为1）。
     * </p>
     *
     * @param commentId 评价ID
     */
    void deleteComment(Long commentId);

    /**
     * 管理端-管理员回复评价
     * <p>
     * 管理员对用户评价进行回复，直接设置评价的 reply 字段。
     * 和商家回复的区别：不需要校验店铺归属，管理员可以回复任意评价。
     * </p>
     *
     * @param commentId 评价ID
     * @param reply     回复内容
     */
    void adminReplyComment(Long commentId, String reply);
}
