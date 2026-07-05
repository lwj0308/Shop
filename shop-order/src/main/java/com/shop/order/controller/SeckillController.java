package com.shop.order.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.result.Result;
import com.shop.order.service.QueueService;
import com.shop.order.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 秒杀控制器
 * <p>
 * 提供秒杀抢购接口，用户点击"立即抢购"按钮时调用。
 * 需要用户登录后才能访问。
 * </p>
 * <p>
 * 小白讲解：秒杀接口只负责接收用户请求，真正的抢购逻辑在SeckillService里。
 * 控制器不写业务逻辑，只做参数接收和响应封装（分层架构规范）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/order/seckill")
@RequiredArgsConstructor
@Tag(name = "秒杀接口", description = "秒杀抢购相关接口")
public class SeckillController {

    /** 秒杀服务 */
    private final SeckillService seckillService;

    /** 排队队列服务（RL-13 引入），查询排队位置 */
    private final QueueService queueService;

    /**
     * 秒杀抢购
     * <p>
     * 用户点击"立即抢购"按钮时调用这个接口。
     * 接口会先在Redis里原子扣减秒杀库存，成功后异步创建订单。
     * 如果被限流，返回 202 + 排队号，前端轮询排队状态后重试。
     * </p>
     *
     * @param seckillId 秒杀活动ID
     * @return 抢购结果提示（code=200 成功；code=202 排队中，data 是排队号）
     */
    @PostMapping("/{seckillId}")
    @Operation(summary = "秒杀抢购", description = "用户参与秒杀抢购，先扣减Redis库存，成功后异步创建订单；限流时返回排队号")
    public Result<String> seckill(@PathVariable Long seckillId) {
        // 获取当前登录用户ID（未登录会抛异常）
        Long userId = StpUtil.getLoginIdAsLong();
        return seckillService.executeSeckill(userId, seckillId);
    }

    /**
     * 查询排队位置（RL-13 引入）
     * <p>
     * 前端收到 202 响应后，用排队号轮询这个接口查询当前位置。
     * 返回值包含 position（当前位置，1表示排第1位）和 total（队列总人数）。
     * 当 position=0 时表示不在队列中，前端可以重试抢购。
     * </p>
     *
     * @param seckillId 秒杀活动ID
     * @param queueNo   排队号（秒杀接口返回的 202 响应中的 data）
     * @return 排队状态信息
     */
    @GetMapping("/queue/{seckillId}/{queueNo}")
    @Operation(summary = "查询排队位置", description = "查询用户在秒杀排队队列中的当前位置和队列总人数")
    public Result<Map<String, Object>> getQueueStatus(
            @Parameter(description = "秒杀活动ID") @PathVariable Long seckillId,
            @Parameter(description = "排队号") @PathVariable String queueNo) {
        int position = queueService.getQueuePosition(seckillId, queueNo);
        int total = queueService.getQueueSize(seckillId);
        Map<String, Object> status = new HashMap<>();
        status.put("position", position);
        status.put("total", total);
        return Result.success(status);
    }
}

