package com.shop.common.model;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * PageResult 分页结果封装的单元测试
 * <p>
 * 验证分页结果的构造、转换和总页数计算逻辑。
 * 重点测试 setPagination 的总页数向上取整规则，以及 from(IPage) 的转换。
 * </p>
 */
@DisplayName("PageResult 分页结果测试")
class PageResultTest {

    // ==================== setPagination 测试 ====================

    @Nested
    @DisplayName("setPagination 设置分页信息")
    class SetPaginationTest {

        @Test
        @DisplayName("正常情况：100条数据，每页10条，应为10页")
        void normalCase() {
            PageResult<String> result = new PageResult<>();
            result.setPagination(100L, 1, 10);

            assertThat(result.getTotal()).isEqualTo(100L);
            assertThat(result.getPageNum()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(10);
            assertThat(result.getPages()).isEqualTo(10);
        }

        @Test
        @DisplayName("向上取整：105条数据，每页10条，应为11页")
        void roundUpPages() {
            PageResult<String> result = new PageResult<>();
            result.setPagination(105L, 1, 10);

            // 105/10 = 10.5，向上取整 = 11
            assertThat(result.getPages()).isEqualTo(11);
        }

        @Test
        @DisplayName("零条数据：total=0时，pages应为0")
        void zeroTotal() {
            PageResult<String> result = new PageResult<>();
            result.setPagination(0L, 1, 10);

            assertThat(result.getTotal()).isEqualTo(0L);
            assertThat(result.getPages()).isEqualTo(0);
        }

        @Test
        @DisplayName("刚好整除：100条数据，每页20条，应为5页")
        void exactDivision() {
            PageResult<String> result = new PageResult<>();
            result.setPagination(100L, 1, 20);

            assertThat(result.getPages()).isEqualTo(5);
        }

        @Test
        @DisplayName("一页装得下：3条数据，每页10条，应为1页")
        void lessThanOnePage() {
            PageResult<String> result = new PageResult<>();
            result.setPagination(3L, 1, 10);

            assertThat(result.getPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("刚好多一条：11条数据，每页10条，应为2页")
        void oneMoreThanOnePage() {
            PageResult<String> result = new PageResult<>();
            result.setPagination(11L, 1, 10);

            assertThat(result.getPages()).isEqualTo(2);
        }
    }

    // ==================== from(IPage) 测试 ====================

    @Nested
    @DisplayName("from 从 IPage 转换")
    class FromIPageTest {

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("from(iPage)：应正确转换所有分页字段")
        void fromIPage() {
            // 用 Mockito mock 一个 IPage 对象
            IPage<String> mockPage = Mockito.mock(IPage.class);
            List<String> records = Arrays.asList("A", "B", "C");
            when(mockPage.getRecords()).thenReturn(records);
            when(mockPage.getTotal()).thenReturn(100L);
            when(mockPage.getCurrent()).thenReturn(2L);
            when(mockPage.getSize()).thenReturn(10L);
            when(mockPage.getPages()).thenReturn(10L);

            PageResult<String> result = PageResult.from(mockPage);

            assertThat(result.getRecords()).containsExactly("A", "B", "C");
            assertThat(result.getTotal()).isEqualTo(100L);
            assertThat(result.getPageNum()).isEqualTo(2);
            assertThat(result.getPageSize()).isEqualTo(10);
            assertThat(result.getPages()).isEqualTo(10);
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("from(iPage, records)：转换时应使用传入的records而不是iPage的records")
        void fromIPageWithCustomRecords() {
            // 场景：IPage 里是 Entity，但想返回 VO，传入转换后的 records
            IPage<com.shop.common.model.BaseEntity> mockPage = Mockito.mock(IPage.class);
            // IPage 的 records 是 Entity
            when(mockPage.getRecords()).thenReturn(Collections.emptyList());
            when(mockPage.getTotal()).thenReturn(50L);
            when(mockPage.getCurrent()).thenReturn(1L);
            when(mockPage.getSize()).thenReturn(10L);
            when(mockPage.getPages()).thenReturn(5L);

            // 传入转换后的 VO 列表
            List<String> voRecords = Arrays.asList("VO1", "VO2");
            PageResult<String> result = PageResult.from(mockPage, voRecords);

            // 应该用传入的 records，而不是 IPage 的
            assertThat(result.getRecords()).containsExactly("VO1", "VO2");
            assertThat(result.getTotal()).isEqualTo(50L);
            assertThat(result.getPageNum()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(10);
            assertThat(result.getPages()).isEqualTo(5);
        }
    }

    // ==================== empty 测试 ====================

    @Test
    @DisplayName("empty()：应返回空的分页结果")
    void emptyShouldReturnEmptyResult() {
        PageResult<String> result = PageResult.empty();

        assertThat(result).isNotNull();
        assertThat(result.getRecords()).isNotNull().isEmpty();
        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getPageNum()).isEqualTo(0);
        assertThat(result.getPageSize()).isEqualTo(0);
        assertThat(result.getPages()).isEqualTo(0);
    }

    // ==================== 默认值测试 ====================

    @Test
    @DisplayName("new PageResult()：records默认是空列表而不是null")
    void defaultRecordsShouldBeEmptyList() {
        PageResult<String> result = new PageResult<>();

        // 默认值应该是 Collections.emptyList()，不是 null
        assertThat(result.getRecords()).isNotNull().isEmpty();
    }
}
