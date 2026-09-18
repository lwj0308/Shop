package com.shop.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.model.user.entity.UserFavorite;
import com.shop.user.mapper.UserFavoriteMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收藏服务 FavoriteServiceImpl 单元测试
 * <p>
 * 小白理解：收藏服务就是用户点"收藏"按钮后做的事情——加收藏、取消收藏、看收藏列表。
 * 我们把操作数据库的 Mapper 假装一下（Mock），这样测试不用真连数据库，跑得快。
 * </p>
 */
@DisplayName("收藏服务 FavoriteServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class FavoriteServiceImplTest {

    /** 假装操作 user_favorite 表的 Mapper */
    @Mock
    private UserFavoriteMapper favoriteMapper;

    /** 被测试的收藏服务，Mock 会自动注入 */
    @InjectMocks
    private FavoriteServiceImpl favoriteService;

    /** 测试用用户ID */
    private static final Long USER_ID = 1L;

    /** 测试用商品ID */
    private static final Long PRODUCT_ID = 100L;

    /**
     * 初始化 MyBatis-Plus Lambda 缓存
     * 小白理解：Service 里用了 .eq(UserFavorite::getUserId, ...) 这种写法，
     * 需要提前告诉 MyBatis-Plus 这个实体对应哪张表，不然会报错。
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, UserFavorite.class);
    }

    // ==================== 添加收藏测试 ====================

    @Nested
    @DisplayName("addFavorite 添加收藏")
    class AddFavoriteTest {

        @Test
        @DisplayName("已收藏过：抛出业务异常提示已收藏")
        void addFavorite_alreadyFavorited_throwsException() {
            // 场景：用户已经收藏过这个商品
            when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            // 验证：抛出异常，不执行 insert
            assertThatThrownBy(() -> favoriteService.addFavorite(USER_ID, PRODUCT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已收藏该商品");

            verify(favoriteMapper, never()).insert(any(UserFavorite.class));
        }

        @Test
        @DisplayName("未收藏过：正常添加收藏记录")
        void addFavorite_notFavorited_insertsRecord() {
            // 场景：用户没收藏过这个商品
            when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(favoriteMapper.insert(any(UserFavorite.class))).thenReturn(1);

            favoriteService.addFavorite(USER_ID, PRODUCT_ID);

            // 验证：插入了收藏记录，且 userId 和 productId 正确
            ArgumentCaptor<UserFavorite> captor = ArgumentCaptor.forClass(UserFavorite.class);
            verify(favoriteMapper).insert(captor.capture());
            UserFavorite saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getProductId()).isEqualTo(PRODUCT_ID);
        }
    }

    // ==================== 取消收藏测试 ====================

    @Nested
    @DisplayName("removeFavorite 取消收藏")
    class RemoveFavoriteTest {

        @Test
        @DisplayName("正常取消收藏：调用delete删除记录")
        void removeFavorite_callsDelete() {
            // 场景：用户取消收藏
            when(favoriteMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

            favoriteService.removeFavorite(USER_ID, PRODUCT_ID);

            // 验证：调用了 delete 方法
            verify(favoriteMapper).delete(any(LambdaQueryWrapper.class));
        }
    }

    // ==================== 收藏列表测试 ====================

    @Nested
    @DisplayName("getFavoriteList 收藏列表分页")
    class GetFavoriteListTest {

        @Test
        @DisplayName("正常分页查询：返回收藏列表和分页信息")
        void getFavoriteList_returnsPagedResult() {
            // 场景：查第1页，每页10条，数据库有2条记录
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            List<UserFavorite> favorites = List.of(
                    buildFavorite(1L, USER_ID, 101L),
                    buildFavorite(2L, USER_ID, 102L)
            );
            Page<UserFavorite> page = new Page<>(1, 10);
            page.setRecords(favorites);
            page.setTotal(2);

            when(favoriteMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            // 验证：返回的 PageResult 字段正确
            PageResult<UserFavorite> result = favoriteService.getFavoriteList(USER_ID, pageRequest);

            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getPageNum()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(10);
            assertThat(result.getPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("空结果：返回空列表和total=0")
        void getFavoriteList_emptyResult_returnsEmptyPage() {
            // 场景：用户没有收藏记录
            PageRequest pageRequest = new PageRequest();
            Page<UserFavorite> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(List.of());
            emptyPage.setTotal(0);

            when(favoriteMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(emptyPage);

            PageResult<UserFavorite> result = favoriteService.getFavoriteList(USER_ID, pageRequest);

            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0);
            assertThat(result.getPages()).isEqualTo(0);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用的收藏对象
     */
    private UserFavorite buildFavorite(Long id, Long userId, Long productId) {
        UserFavorite favorite = new UserFavorite();
        favorite.setId(id);
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        return favorite;
    }
}
