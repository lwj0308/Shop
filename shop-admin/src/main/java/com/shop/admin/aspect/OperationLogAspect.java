package com.shop.admin.aspect;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.shop.admin.annotation.OperationLog;
import com.shop.admin.service.AdminOperationLogService;
import com.shop.model.admin.entity.AdminOperationLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;

/**
 * 操作日志切面
 * <p>
 * 拦截加了@OperationLog注解的Controller方法，自动记录操作日志到数据库。
 * 包括操作人、操作模块、请求参数、响应结果、耗时等信息。
 * 不管方法执行成功还是失败，都会记录日志（失败时记录错误信息）。
 * </p>
 * <p>
 * 工作流程：
 * 1. 记录方法开始执行的时间
 * 2. 执行目标方法
 * 3. 方法执行成功：记录响应结果，状态标记为成功
 * 4. 方法执行失败：记录错误信息，状态标记为失败，并重新抛出异常
 * 5. 在finally中计算耗时，异步保存日志到数据库
 * </p>
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class OperationLogAspect {

    /** 操作日志服务，负责把日志保存到数据库 */
    private final AdminOperationLogService adminOperationLogService;

    /** 响应结果和请求参数的最大长度，超过会被截断（防止字段太长存不下） */
    private static final int MAX_LENGTH = 2000;

    /** 请求参数中需要脱敏的字段名（这些字段的值不能明文记录到日志里） */
    private static final List<String> SENSITIVE_FIELDS = Arrays.asList("password", "oldPassword", "newPassword", "confirmPassword", "token", "accessToken", "refreshToken");

    /**
     * 环绕通知：记录操作日志
     * <p>
     * 拦截所有标注了@OperationLog注解的方法，在方法执行前后记录操作日志。
     * 不管方法成功还是失败，日志都会被保存到数据库。
     * </p>
     *
     * @param joinPoint    切点信息，包含被拦截方法的详细信息
     * @param operationLog 注解实例，包含日志配置（模块、类型、描述）
     * @return 方法执行结果
     * @throws Throwable 方法执行过程中可能抛出的异常
     */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        // 记录方法开始执行的时间，用来计算耗时
        long startTime = System.currentTimeMillis();

        // 构建操作日志实体
        AdminOperationLog logEntity = new AdminOperationLog();
        logEntity.setModule(operationLog.module());
        logEntity.setOperationType(operationLog.type().name());
        logEntity.setDescription(operationLog.description());

        // 从HttpServletRequest中获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            logEntity.setRequestUrl(request.getRequestURL().toString());
            logEntity.setRequestMethod(request.getMethod());
            logEntity.setIp(com.shop.admin.util.IpUtils.getClientIp(request));

            // 序列化请求参数为JSON（脱敏处理）
            String params = serializeArgs(joinPoint.getArgs());
            logEntity.setRequestParams(truncate(params));
        }

        // 设置当前操作人信息（如果已登录）
        setCurrentUserInfo(logEntity);

        Object result = null;
        try {
            // 执行目标方法
            result = joinPoint.proceed();
            // 方法执行成功，状态标记为1
            logEntity.setStatus(1);

            // 记录响应结果（截断防止太长）
            if (result != null) {
                String responseJson = JSONUtil.toJsonStr(result);
                logEntity.setResponseResult(truncate(responseJson));
            }
        } catch (Throwable e) {
            // 方法执行失败，状态标记为0
            logEntity.setStatus(0);
            // 记录错误信息（截断防止太长）
            String errorMsg = e.getMessage();
            logEntity.setErrorMsg(truncate(errorMsg != null ? errorMsg : e.getClass().getName()));
            // 重新抛出异常，不影响原有业务逻辑
            throw e;
        } finally {
            // 计算方法执行耗时（毫秒）
            logEntity.setDuration((int) (System.currentTimeMillis() - startTime));
            // 通过@Async异步保存日志到数据库（不管成功失败都要保存）
            // recordOperationLog方法上加了@Async注解，Spring会自动在线程池中异步执行
            adminOperationLogService.recordOperationLog(logEntity);
        }

        return result;
    }

    /**
     * 设置当前登录管理员的信息
     * <p>
     * 从Sa-Token获取当前登录管理员的ID和用户名，记录到日志中。
     * 如果未登录（比如登录接口本身），则不设置这些字段。
     * </p>
     *
     * @param logEntity 操作日志实体
     */
    private void setCurrentUserInfo(AdminOperationLog logEntity) {
        try {
            if (StpUtil.isLogin()) {
                logEntity.setUserId(StpUtil.getLoginIdAsLong());
                // 用户名暂时用userId代替，因为Sa-Token默认只存loginId
                logEntity.setUsername(String.valueOf(StpUtil.getLoginIdAsLong()));
            }
        } catch (Exception e) {
            // 获取登录信息失败不影响主流程，忽略即可
            log.debug("获取当前登录管理员信息失败：{}", e.getMessage());
        }
    }

    /**
     * 序列化方法参数为JSON字符串，并对敏感字段脱敏
     * <p>
     * 把方法的参数数组转成JSON字符串，方便存到数据库。
     * 对于password等敏感字段，把值替换成"******"，防止密码泄露。
     * </p>
     *
     * @param args 方法参数数组
     * @return 脱敏后的JSON字符串
     */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        try {
            String json = JSONUtil.toJsonStr(args);
            // 对敏感字段进行脱敏处理（把password等字段的值替换成******）
            for (String field : SENSITIVE_FIELDS) {
                // 匹配字符串值："password":"xxx" 或 "password": "xxx"
                json = json.replaceAll("(\"(" + field + ")\"\\s*:\\s*)\"[^\"]*\"", "$1\"******\"");
                // 匹配非字符串值："password":123 或 "password":null 或 "password":true
                // 上面字符串值的正则已经处理了字符串情况，这里处理数字、布尔、null等
                // 注意：lookahead里的******要转义成\\*{6}，否则*会被当成正则量词导致PatternSyntaxException
                json = json.replaceAll("(\"(" + field + ")\"\\s*:\\s*)(?!\"\\*{6}\")\\S+", "$1\"******\"");
            }
            return json;
        } catch (Exception e) {
            // 序列化失败不影响主流程，返回简单描述
            return Arrays.toString(args);
        }
    }

    /**
     * 截断字符串，防止超长内容存不进数据库
     * <p>
     * 数据库字段有长度限制，如果内容太长会导致插入失败。
     * 超过MAX_LENGTH的部分会被截断，并追加"..."提示。
     * </p>
     *
     * @param str 原始字符串
     * @return 截断后的字符串
     */
    private String truncate(String str) {
        if (str == null) {
            return null;
        }
        if (str.length() > MAX_LENGTH) {
            return str.substring(0, MAX_LENGTH) + "...";
        }
        return str;
    }

}
