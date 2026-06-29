package com.shop.admin.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * IpUtils IP工具类的单元测试
 * <p>
 * 这个测试类专门验证 IpUtils 能不能正确地从 HTTP 请求里拿到客户端真实 IP。
 * 简单理解：用户请求可能经过 Nginx 等代理，真实 IP 藏在请求头里，
 * IpUtils 要按 X-Forwarded-For → X-Real-IP → RemoteAddr 的顺序找出来。
 * </p>
 */
@DisplayName("IpUtils IP工具类测试")
class IpUtilsTest {

    // ==================== getClientIp(HttpServletRequest) 测试 ====================

    @Nested
    @DisplayName("getClientIp(request)：从请求对象获取IP")
    class GetClientIpWithRequestTest {

        @Test
        @DisplayName("X-Forwarded-For头（单个IP）：直接返回该IP")
        void xForwardedForSingleIp() {
            // 准备一个mock的请求对象，模拟Nginx转发的场景
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100");

            // 调用被测方法
            String ip = IpUtils.getClientIp(request);

            // 应该返回 X-Forwarded-For 里的IP
            assertThat(ip).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("X-Forwarded-For头（多个IP）：取第一个IP（多级代理时第一个是真实客户端）")
        void xForwardedForMultipleIp() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            // 多级代理时格式是 "客户端IP, 代理1IP, 代理2IP"
            when(request.getHeader("X-Forwarded-For")).thenReturn("1.1.1.1, 2.2.2.2, 3.3.3.3");

            String ip = IpUtils.getClientIp(request);

            // 第一个IP就是客户端真实IP
            assertThat(ip).isEqualTo("1.1.1.1");
        }

        @Test
        @DisplayName("X-Real-IP头：X-Forwarded-For为null时，从X-Real-IP取")
        void xRealIpWhenXForwardedForNull() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn("10.0.0.1");

            String ip = IpUtils.getClientIp(request);

            assertThat(ip).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("RemoteAddr：前两个头都没有时，用RemoteAddr（直连场景）")
        void remoteAddrWhenNoProxyHeader() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            String ip = IpUtils.getClientIp(request);

            assertThat(ip).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("unknown值：X-Forwarded-For为'unknown'字符串时，应跳到X-Real-IP")
        void unknownValueShouldFallback() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            // 有些代理会把无法识别的IP写成"unknown"
            when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
            when(request.getHeader("X-Real-IP")).thenReturn("172.16.0.1");

            String ip = IpUtils.getClientIp(request);

            assertThat(ip).isEqualTo("172.16.0.1");
        }

        @Test
        @DisplayName("空字符串：X-Forwarded-For为空字符串时，应跳到X-Real-IP")
        void emptyStringShouldFallback() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("X-Forwarded-For")).thenReturn("");
            when(request.getHeader("X-Real-IP")).thenReturn("172.16.0.2");

            String ip = IpUtils.getClientIp(request);

            assertThat(ip).isEqualTo("172.16.0.2");
        }

        @Test
        @DisplayName("大小写不敏感：'UNKNOWN'也能被识别并跳过")
        void unknownIgnoreCaseShouldFallback() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("X-Forwarded-For")).thenReturn("UNKNOWN");
            when(request.getHeader("X-Real-IP")).thenReturn("172.16.0.3");

            String ip = IpUtils.getClientIp(request);

            assertThat(ip).isEqualTo("172.16.0.3");
        }

        @Test
        @DisplayName("X-Forwarded-For含多个IP且带空格：第一个IP应trim去掉空格")
        void xForwardedForMultipleIpWithSpaces() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            // 模拟 " 1.1.1.1 , 2.2.2.2" 这种带空格的情况
            when(request.getHeader("X-Forwarded-For")).thenReturn(" 1.1.1.1 , 2.2.2.2");

            String ip = IpUtils.getClientIp(request);

            // split后trim，应该去掉前面的空格
            assertThat(ip).isEqualTo("1.1.1.1");
        }
    }

    // ==================== getClientIp() 无参版本测试 ====================

    @Nested
    @DisplayName("getClientIp()：无参版本，依赖RequestContextHolder")
    class GetClientIpNoParamTest {

        @Test
        @DisplayName("非Web环境（无RequestAttributes）：返回'unknown'")
        void noRequestAttributesReturnsUnknown() {
            // 测试环境没有Spring Web上下文，RequestContextHolder.getRequestAttributes()返回null
            // 这种场景出现在定时任务、消息消费者等非HTTP线程里
            String ip = IpUtils.getClientIp();

            // 应该返回"unknown"，而不是抛异常
            assertThat(ip).isEqualTo("unknown");
        }
    }
}
