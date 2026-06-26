package com.shop.model.seckill.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 秒杀活动查询参数DTO
 * <p>
 * 商家端和管理端查询秒杀活动列表时的筛选条件。
 * 用户端查询时也可以用（查进行中的活动）。
 * </p>
 */
@Data
public class SeckillQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态筛选（可选）：0待生效 1进行中 2已结束 3已下架 */
    private Integer status;

    /** 页码（从1开始） */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;
}
