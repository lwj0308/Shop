package com.shop.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内部接口鉴权拦截器测试
 * <p>
 * 小白理解：InnerApiInterceptor 就像门禁系统，只有带正确门禁卡（X-Inner-Key）的请求才能进。
 * 这个测试模拟三种情况：没带卡、带错卡、带对卡，验证拦截器处理得对不对。
 * </p>
 */
@DisplayName("InnerApiInterceptor 内部接口鉴权拦截器测试")
class InnerApiInterceptorTest {

    private InnerApiInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    /** 测试用的内部密钥 */
    private static final String INNER_KEY = "test-inner-key-2024";

    @BeforeEach
    void setUp() throws Exception {
        // 每个测试前都重新创建，避免互相影响
        interceptor = new InnerApiInterceptor();
        // 通过反射注入配置值（模拟@Value注入的效果，不用启动Spring）
        ReflectionTestUtils.setField(interceptor, "innerKey", INNER_KEY);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        // 让response.getWriter()返回可写的StringWriter，方便验证响应内容
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    // ==================== 鉴权失败场景 ====================

    @Nested
    @DisplayName("鉴权失败场景")
    class AuthFailTest {

        @Test
        @DisplayName("没带X-Inner-Key头：返回403，拒绝访问")
        void preHandle_noKeyHeader_returns403() throws Exception {
            // 场景：外部请求直连微服务端口，没有内部密钥头
            when(request.getHeader("X-Inner-Key")).thenReturn(null);
            when(request.getRequestURI()).thenReturn("/inner/order/123");
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");

            // 验证：返回false表示拒绝
            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            verify(response).setStatus(403);
            verify(response).setContentType("application/json;charset=UTF-8");
            // 响应体应包含错误信息
            assertThat(responseWriter.toString()).contains("无权访问内部接口");
            assertThat(responseWriter.toString()).contains("\"code\":403");
        }

        @Test
        @DisplayName("X-Inner-Key不匹配：返回403，拒绝访问")
        void preHandle_wrongKey_returns403() throws Exception {
            // 场景：伪造的密钥
            when(request.getHeader("X-Inner-Key")).thenReturn("wrong-key");
            when(request.getRequestURI()).thenReturn("/inner/product/stock");
            when(request.getRemoteAddr()).thenReturn("10.0.0.5");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            verify(response).setStatus(403);
            assertThat(responseWriter.toString()).contains("无权访问内部接口");
        }

        @Test
        @DisplayName("X-Inner-Key为空字符串：返回403，拒绝访问")
        void preHandle_emptyKey_returns403() throws Exception {
            // 场景：头存在但值是空字符串
            when(request.getHeader("X-Inner-Key")).thenReturn("");
            when(request.getRequestURI()).thenReturn("/inner/user/info");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isFalse();
            verify(response).setStatus(403);
        }
    }

    // ==================== 鉴权成功场景 ====================

    @Nested
    @DisplayName("鉴权成功场景")
    class AuthSuccessTest {

        @Test
        @DisplayName("X-Inner-Key正确：放行，返回true")
        void preHandle_correctKey_returnsTrue() throws Exception {
            // 场景：内部Feign调用，带了正确的密钥
            when(request.getHeader("X-Inner-Key")).thenReturn(INNER_KEY);

            boolean result = interceptor.preHandle(request, response, new Object());

            // 验证：返回true表示放行，不会设置403
            assertThat(result).isTrue();
            verify(response, org.mockito.Mockito.never()).setStatus(403);
        }
    }
}
