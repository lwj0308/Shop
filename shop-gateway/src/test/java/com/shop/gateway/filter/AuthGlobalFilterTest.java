package com.shop.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.gateway.config.WhitelistConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthGlobalFilter 鉴权过滤器单元测试
 * <p>
 * 这个过滤器就像商场的保安，每个人进来都要检查"通行证"（Token）。
 * 白名单区域不需要通行证，其他区域必须携带有效 Token。
 * 这里用 mock 模拟白名单配置、Sa-Token 校验和过滤器链，验证保安的行为是否正确。
 * </p>
 * <p>
 * 因为 gateway 是 WebFlux 响应式模块，filter 返回 Mono&lt;Void&gt;，
 * 所以用 reactor-test 的 StepVerifier 来验证响应式结果，
 * 用 spring-test 的 MockServerWebExchange 构造测试请求。
 * </p>
 */
@DisplayName("AuthGlobalFilter 鉴权过滤器测试")
@ExtendWith(MockitoExtension.class)
class AuthGlobalFilterTest {

    /** 白名单配置的 mock，模拟从 Nacos 读取的白名单 */
    @Mock
    private WhitelistConfig whitelistConfig;

    /** 过滤器链的 mock，模拟放行动作 */
    @Mock
    private GatewayFilterChain chain;

    /** 被测对象：鉴权过滤器 */
    private AuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        // 手动构造 filter，注入 mock 的白名单配置
        filter = new AuthGlobalFilter(whitelistConfig);
        // 用 lenient 避免严格模式下未使用 stub 报错
        // 默认放行动作返回 Mono.empty()，表示"放行后什么都不做"
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ==================== 过滤器顺序测试 ====================

    @Test
    @DisplayName("getOrder：应返回 -100，确保在其他过滤器之前执行鉴权")
    void getOrder_shouldReturnMinus100() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }

    // ==================== 白名单请求测试 ====================

    @Nested
    @DisplayName("白名单请求处理")
    class WhitelistTest {

        @Test
        @DisplayName("完全公开白名单路径（如 /api/user/login）应直接放行，不校验Token")
        void whitelistPath_shouldPassThrough() {
            // 准备：配置白名单包含登录接口
            when(whitelistConfig.getWhitelist()).thenReturn(List.of("/api/user/login"));
            // 构造一个登录请求（不带Token）
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/user/login").build());

            // 执行：调用过滤器，验证 Mono 正常完成
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：chain.filter 被调用了一次，说明请求被放行
            verify(chain).filter(any());
        }

        @Test
        @DisplayName("白名单请求也应清除外部伪造的 X-User-Id 等身份Header，防止冒充")
        void whitelistPath_shouldStripFakeUserIdHeader() {
            when(whitelistConfig.getWhitelist()).thenReturn(List.of("/api/user/login"));
            // 外部请求恶意带上伪造的用户身份Header
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/user/login")
                            .header("X-User-Id", "fake-user")
                            .header("X-User-Role", "fake-admin")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 捕获传给下游 chain 的 exchange，验证身份Header已被清除
            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            HttpHeaders headers = captor.getValue().getRequest().getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isNull();
            assertThat(headers.getFirst("X-User-Role")).isNull();
        }

        @Test
        @DisplayName("白名单支持Ant通配符，如 /api/user/auth/** 匹配 /api/user/auth/login")
        void whitelistPath_shouldSupportAntPattern() {
            when(whitelistConfig.getWhitelist()).thenReturn(List.of("/api/user/auth/**"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/user/auth/login").build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain).filter(any());
        }
    }

    // ==================== 未登录请求测试 ====================

    @Nested
    @DisplayName("未登录请求处理")
    class NoTokenTest {

        @Test
        @DisplayName("非白名单且未携带Token，应返回401未登录")
        void noToken_shouldReturn401() {
            when(whitelistConfig.getWhitelist()).thenReturn(List.of());
            when(whitelistConfig.getAuthWhitelist()).thenReturn(List.of());

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/order/list").build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            // 验证：未放行
            verify(chain, never()).filter(any());
            // 验证：返回401状态码
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("在登录用户白名单内但未携带Token，应返回401请先登录")
        void authWhitelistWithoutToken_shouldReturn401() {
            when(whitelistConfig.getWhitelist()).thenReturn(List.of());
            when(whitelistConfig.getAuthWhitelist()).thenReturn(List.of("/api/product/list"));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/product/list").build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain, never()).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==================== Token格式校验测试 ====================

    @Nested
    @DisplayName("Token格式校验测试")
    class TokenFormatTest {

        @Test
        @DisplayName("Token格式无效（含非法字符空格和感叹号），应返回401")
        void invalidTokenFormat_shouldReturn401() {
            when(whitelistConfig.getWhitelist()).thenReturn(List.of());
            // token 含空格和感叹号，不符合"只允许字母数字横线"的规则
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/order/list")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid token!")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain, never()).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Token格式无效（长度太短，少于16位），应返回401")
        void shortToken_shouldReturn401() {
            when(whitelistConfig.getWhitelist()).thenReturn(List.of());
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/order/list")
                            .header(HttpHeaders.AUTHORIZATION, "short")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify();

            verify(chain, never()).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==================== 已登录请求测试 ====================

    @Nested
    @DisplayName("已登录请求处理")
    class LoggedInTest {

        @Test
        @DisplayName("携带有效Token的请求应放行，并在Header中写入真实userId和角色")
        void validToken_shouldPassAndSetUserIdHeader() {
            when(whitelistConfig.getWhitelist()).thenReturn(List.of());
            // 有效Token：长度>=16 且只含字母数字横线
            String validToken = "valid-token-1234567890-abcdef";
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/order/list")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .build());

            // mock Sa-Token 的静态方法（getLoginIdByToken 和 getRoleList 都是静态方法）
            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                // 模拟 Token 校验通过，返回用户ID
                stpMock.when(() -> StpUtil.getLoginIdByToken(anyString())).thenReturn("user123");
                // 模拟查询用户角色
                stpMock.when(() -> StpUtil.getRoleList(any())).thenReturn(List.of("admin"));

                StepVerifier.create(filter.filter(exchange, chain))
                        .expectComplete()
                        .verify();
            }

            // 验证：放行了
            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            // 验证：下游收到的请求中，Header 写入了真实的用户信息
            HttpHeaders headers = captor.getValue().getRequest().getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isEqualTo("user123");
            assertThat(headers.getFirst("X-User-Role")).isEqualTo("admin");
            // Authorization 应被替换为纯 token（去掉 Bearer 前缀）
            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(validToken);
        }

        @Test
        @DisplayName("Sa-Token校验Token无效（返回null），应返回401登录已过期")
        void invalidTokenBySaToken_shouldReturn401() {
            when(whitelistConfig.getWhitelist()).thenReturn(List.of());
            String validToken = "valid-token-1234567890-abcdef";
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/order/list")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .build());

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                // 模拟 Token 无效，Sa-Token 返回 null
                stpMock.when(() -> StpUtil.getLoginIdByToken(anyString())).thenReturn(null);

                StepVerifier.create(filter.filter(exchange, chain))
                        .expectComplete()
                        .verify();
            }

            verify(chain, never()).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Sa-Token校验抛异常，应返回401登录已过期")
        void saTokenThrowsException_shouldReturn401() {
            when(whitelistConfig.getWhitelist()).thenReturn(List.of());
            String validToken = "valid-token-1234567890-abcdef";
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/order/list")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .build());

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                // 模拟 Sa-Token 校验时抛异常
                stpMock.when(() -> StpUtil.getLoginIdByToken(anyString()))
                        .thenThrow(new RuntimeException("token已过期"));

                StepVerifier.create(filter.filter(exchange, chain))
                        .expectComplete()
                        .verify();
            }

            verify(chain, never()).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("外部伪造的 X-User-Id Header 在鉴权通过后应被真实值覆盖，防止冒充")
        void fakeHeaderShouldBeOverwrittenAfterAuth() {
            when(whitelistConfig.getWhitelist()).thenReturn(List.of());
            String validToken = "valid-token-1234567890-abcdef";
            // 外部恶意带上伪造的 X-User-Id
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/order/list")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                            .header("X-User-Id", "fake-user")
                            .build());

            try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
                stpMock.when(() -> StpUtil.getLoginIdByToken(anyString())).thenReturn("real-user-123");
                stpMock.when(() -> StpUtil.getRoleList(any())).thenReturn(List.of("user"));

                StepVerifier.create(filter.filter(exchange, chain))
                        .expectComplete()
                        .verify();
            }

            // 验证：下游收到的是真实的 userId，而不是伪造的
            ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            HttpHeaders headers = captor.getValue().getRequest().getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isEqualTo("real-user-123");
        }
    }
}
