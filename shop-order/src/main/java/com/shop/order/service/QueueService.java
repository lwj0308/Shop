package com.shop.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀排队队列服务（RL-13 引入）
 * <p>
 * 当秒杀接口被 Sentinel 限流时，不是直接拒绝用户，而是把用户放进排队队列，
 * 返回一个排队号，让前端轮询查询排队进度，等限流窗口过去后再重试抢购。
 * </p>
 * <p>
 * 实现原理：使用 Redis List 存储排队用户，RPUSH 入队，通过 LRANGE 查找位置。
 * </p>
 * <p>
 * 小白理解：就像超市结账时排队的队伍，新来的人排在最后（RPUSH），
 * 每个人都有一个排队号（UUID），你想知道自己排第几个，就从头到尾找一遍（LRANGE）。
 * 队伍10分钟后自动消失（设置过期时间），避免堆积垃圾数据。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    /** Redis模板，操作排队队列 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 排队队列key前缀：seckill:queue:{seckillId} 存的是排队用户的列表 */
    private static final String QUEUE_KEY_PREFIX = "seckill:queue:";

    /** 队列自动过期时间（分钟），10分钟后队列自动消失，避免垃圾数据堆积 */
    private static final long QUEUE_EXPIRE_MINUTES = 10;

    /**
     * 入队：把用户加入秒杀排队队列
     * <p>
     * 生成一个唯一的排队号（UUID），把"排队号:用户ID"拼成字符串塞到Redis List末尾。
     * 同时给队列设置10分钟过期时间，避免长时间不消费导致堆积。
     * </p>
     *
     * @param seckillId 秒杀活动ID
     * @param userId    用户ID
     * @return 排队号（UUID字符串，前端用它来查询排队位置）
     */
    public String enqueue(Long seckillId, Long userId) {
        // 生成唯一的排队号（去掉横线更短一些）
        String queueNo = UUID.randomUUID().toString().replace("-", "");
        // 队列里存的内容格式：排队号:用户ID（用冒号分隔，方便解析）
        String value = queueNo + ":" + userId;
        String key = QUEUE_KEY_PREFIX + seckillId;

        // RPUSH：把值塞到列表末尾（新来的人排最后）
        stringRedisTemplate.opsForList().rightPush(key, value);
        // 设置过期时间，10分钟后整个队列自动消失
        stringRedisTemplate.expire(key, QUEUE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        log.info("用户加入秒杀排队: seckillId={}, userId={}, queueNo={}", seckillId, userId, queueNo);
        return queueNo;
    }

    /**
     * 查询排队位置
     * <p>
     * 从头到尾遍历队列，找到排队号对应的位置。
     * 返回1表示排第1位（马上就轮到了），返回0表示不在队列里（可能已经处理完或过期了）。
     * </p>
     * <p>
     * 小白理解：你在队伍里找自己的名字，找到的话看你是第几个，
     * 找不到说明你已经出队了（可能轮到你抢购了）。
     * </p>
     *
     * @param seckillId 秒杀活动ID
     * @param queueNo   排队号
     * @return 排队位置（1-based，1表示排第1位；0表示不在队列中）
     */
    public int getQueuePosition(Long seckillId, String queueNo) {
        String key = QUEUE_KEY_PREFIX + seckillId;
        // LRANGE 0 -1：取出整个列表
        List<String> list = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        // 遍历列表，找到排队号所在位置
        for (int i = 0; i < list.size(); i++) {
            // 每个元素格式是"排队号:用户ID"，用startsWith匹配排队号
            if (list.get(i).startsWith(queueNo + ":")) {
                return i + 1; // 1-based位置，第0个元素排第1位
            }
        }
        // 没找到，说明已经出队或过期
        return 0;
    }

    /**
     * 查询队列总长度（当前排队总人数）
     *
     * @param seckillId 秒杀活动ID
     * @return 队列长度
     */
    public int getQueueSize(Long seckillId) {
        String key = QUEUE_KEY_PREFIX + seckillId;
        Long size = stringRedisTemplate.opsForList().size(key);
        return size != null ? size.intValue() : 0;
    }

    /**
     * 从队列中移除指定用户（抢购成功后调用，清理队列）
     * <p>
     * 当用户重试抢购成功后，把自己从队列里删掉，让后面的人往前挪。
     * </p>
     *
     * @param seckillId 秒杀活动ID
     * @param queueNo   排队号
     * @param userId    用户ID
     */
    public void removeFromQueue(Long seckillId, String queueNo, Long userId) {
        String key = QUEUE_KEY_PREFIX + seckillId;
        String value = queueNo + ":" + userId;
        // LREM：从列表中删除count个等于value的元素，count=1表示只删第一个匹配的
        stringRedisTemplate.opsForList().remove(key, 1, value);
        log.info("用户已从排队队列移除: seckillId={}, userId={}, queueNo={}", seckillId, userId, queueNo);
    }
}
