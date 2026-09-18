package com.shop.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.model.user.entity.UserFootprint;
import com.shop.user.mapper.UserFootprintMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 浏览足迹服务 FootprintServiceImpl 单元测试
 * <p>
 * 小白理解：用户每次看商品详情，就会留下一条"足迹"。
 * 同一个商品看第二次不会产生新记录，只会更新时间（把商品"顶到最前面"）。
 * 这个测试验证这些逻辑对不对。
 * </p>
 */
@DisplayName("浏览足迹服务 FootprintServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class FootprintServiceImplTest {

    /** 假装操作 user_footprint 表的 Mapper */
    @Mock
    private UserFootprintMapper footprintMapper;

    /** 被测试的足迹服务 */
    @InjectMocks
    private FootprintServiceImpl footprintService;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;
    private static final Long CATEGORY_ID = 10L;

    /**
     * 初始化 MyBatis-Plus Lambda 缓存（和 FavoriteServiceImplTest 一样的道理）
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, UserFootprint.class);
    }

    // ==================== 添加足迹测试 ====================

    @Nested
    @DisplayName("addFootprint 添加浏览足迹")
    class AddFootprintTest {

        @Test
        @DisplayName("首次浏览：新增足迹记录")
        void addFootprint_firstTime_insertsRecord() {
            // 场景：用户第一次浏览这个商品，数据库里没有记录
            when(footprintMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            footprintService.addFootprint(USER_ID, PRODUCT_ID, CATEGORY_ID);

            // 验证：执行了 insert，没有执行 updateById
            ArgumentCaptor<UserFootprint> captor = ArgumentCaptor.forClass(UserFootprint.class);
            verify(footprintMapper).insert(captor.capture());
            UserFootprint saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(saved.getCategoryId()).isEqualTo(CATEGORY_ID);
            verify(footprintMapper, never()).updateById(any(UserFootprint.class));
        }

        @Test
        @DisplayName("重复浏览：更新已有足迹的时间和分类")
        void addFootprint_alreadyExists_updatesRecord() {
            // 场景：用户之前浏览过这个商品，已有足迹记录
            UserFootprint existing = buildFootprint(1L, USER_ID, PRODUCT_ID, 5L);
            when(footprintMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            footprintService.addFootprint(USER_ID, PRODUCT_ID, CATEGORY_ID);

            // 验证：执行了 updateById，没有执行 insert
            verify(footprintMapper).updateById(existing);
            // 验证分类被更新为新值（从5更新为10）
            assertThat(existing.getCategoryId()).isEqualTo(CATEGORY_ID);
            assertThat(existing.getCreateTime()).isNotNull();
            verify(footprintMapper, never()).insert(any(UserFootprint.class));
        }
    }

    // ==================== 足迹列表测试 ====================

    @Nested
    @DisplayName("getFootprintList 足迹列表分页")
    class GetFootprintListTest {

        @Test
        @DisplayName("正常分页查询：返回足迹列表和分页信息")
        void getFootprintList_returnsPagedResult() {
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            List<UserFootprint> footprints = List.of(
                    buildFootprint(1L, USER_ID, 101L, 10L),
                    buildFootprint(2L, USER_ID, 102L, 20L)
            );
            Page<UserFootprint> page = new Page<>(1, 10);
            page.setRecords(footprints);
            page.setTotal(2);

            when(footprintMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            PageResult<UserFootprint> result = footprintService.getFootprintList(USER_ID, pageRequest);

            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getPageNum()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(10);
        }
    }

    // ==================== 足迹分类ID测试 ====================

    @Nested
    @DisplayName("getFootprintCategoryIds 获取浏览过的分类ID列表")
    class GetFootprintCategoryIdsTest {

        @Test
        @DisplayName("正常返回去重后的分类ID列表")
        void getFootprintCategoryIds_returnsDistinctCategoryIds() {
            // 场景：用户浏览过3个商品，分属2个分类（但SQL的groupBy已去重，selectList返回的每条categoryId唯一）
            List<UserFootprint> list = List.of(
                    buildFootprint(1L, USER_ID, 101L, 10L),
                    buildFootprint(2L, USER_ID, 102L, 20L)
            );
            when(footprintMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(list);

            List<Long> categoryIds = footprintService.getFootprintCategoryIds(USER_ID);

            // 验证：返回2个分类ID
            assertThat(categoryIds).hasSize(2);
            assertThat(categoryIds).containsExactly(10L, 20L);
        }

        @Test
        @DisplayName("无足迹记录：返回空列表")
        void getFootprintCategoryIds_noFootprint_returnsEmptyList() {
            when(footprintMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            List<Long> categoryIds = footprintService.getFootprintCategoryIds(USER_ID);

            assertThat(categoryIds).isEmpty();
        }
    }

    // ==================== 辅助方法 ====================

    private UserFootprint buildFootprint(Long id, Long userId, Long productId, Long categoryId) {
        UserFootprint footprint = new UserFootprint();
        footprint.setId(id);
        footprint.setUserId(userId);
        footprint.setProductId(productId);
        footprint.setCategoryId(categoryId);
        return footprint;
    }
}
