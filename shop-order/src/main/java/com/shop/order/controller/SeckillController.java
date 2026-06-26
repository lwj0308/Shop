package com.shop.order.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.result.Result;
import com.shop.order.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * 秒杀抢购
     * <p>
     * 用户点击"立即抢购"按钮时调用这个接口。
     * 接口会先在Redis里原子扣减秒杀库存，成功后异步创建订单。
     * </p>
     *
     * @param seckillId 秒杀活动ID
     * @return 抢购结果提示
     */
    @PostMapping("/{seckillId}")
    @Operation(summary = "秒杀抢购", description = "用户参与秒杀抢购，先扣减Redis库存，成功后异步创建订单")
    public Result<String> seckill(@PathVariable Long seckillId) {
        // 获取当前登录用户ID（未登录会抛异常）
        Long userId = StpUtil.getLoginIdAsLong();
        return seckillService.executeSeckill(userId, seckillId);
    }
}
