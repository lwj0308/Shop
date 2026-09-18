package com.shop.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.product.dto.CategoryDTO;
import com.shop.model.product.entity.Category;
import com.shop.model.product.entity.Product;
import com.shop.model.product.vo.CategoryVO;
import com.shop.product.mapper.CategoryMapper;
import com.shop.product.mapper.ProductMapper;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品分类服务实现类（CategoryServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证分类的增删改查和分类树构建逻辑。
 * 简单理解：把 CategoryMapper、ProductMapper 都"假装"一下（Mock），
 * 不用真的连数据库，跑得又快又稳。
 * </p>
 * <p>
 * 小白快速理解：
 * - @ExtendWith(MockitoExtension.class)：让 Mockito 框架接管测试环境
 * - @Mock：造一个假的 Mapper，方法调用都由我们控制返回值
 * - @InjectMocks：被测试的 Service，Mockito 会把上面的 @Mock 自动注入进去
 * - @BeforeAll：所有测试运行前执行一次，这里用来初始化 MyBatis-Plus 缓存
 * - @Nested：把"同一个方法的多个测试"分组，看起来更清晰
 * - AssertJ：提供 assertThat(x).isEqualTo(y) 这种易读的断言写法
 * </p>
 * <p>
 * 覆盖的方法：addCategory、updateCategory、deleteCategory、getCategoryTree
 * </p>
 */
@DisplayName("商品分类服务 CategoryServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceImplTest {

    // ==================== Mock 依赖（都是假的，不真的连数据库） ====================

    /** 分类 Mapper，操作 category 表 */
    @Mock
    private CategoryMapper categoryMapper;

    /** 商品 Mapper，删除分类时用来检查分类下有没有商品 */
    @Mock
    private ProductMapper productMapper;

    /** 被测试的分类服务，Mockito 会自动把上面两个 Mock 注入进来 */
    @InjectMocks
    private CategoryServiceImpl categoryService;

    /** 常用测试数据：分类ID */
    private static final Long CATEGORY_ID = 1001L;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：CategoryServiceImpl 用了 LambdaQueryWrapper，
     * 比如 .eq(Category::getParentId, ...)。这些代码会让 MyBatis-Plus 去查
     * "parentId 字段对应数据库哪一列"。正常启动 Spring 时框架会自动做这件事，
     * 但单元测试没有 Spring 环境，所以需要手动告诉 MyBatis-Plus：
     * Category、Product 这些实体有哪些字段、对应哪些列。
     * 不初始化会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        // 初始化所有用到 Lambda 查询的实体的缓存
        TableInfoHelper.initTableInfo(assistant, Category.class);
        TableInfoHelper.initTableInfo(assistant, Product.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个分类实体
     *
     * @param id       分类ID
     * @param parentId 父分类ID（0表示顶级分类）
     * @param name     分类名称
     * @param sort     排序值
     * @return 构造好的 Category 实体
     */
    private Category buildCategory(Long id, Long parentId, String name, Integer sort) {
        Category category = new Category();
        category.setId(id);
        category.setParentId(parentId);
        category.setName(name);
        category.setSort(sort);
        category.setStatus(1); // 默认启用
        return category;
    }

    /**
     * 构造一个分类请求DTO
     *
     * @param parentId 父分类ID
     * @param name     分类名称
     * @param sort     排序值（可传null，测试默认值兜底逻辑）
     * @return 构造好的 CategoryDTO
     */
    private CategoryDTO buildDTO(Long parentId, String name, Integer sort) {
        CategoryDTO dto = new CategoryDTO();
        dto.setParentId(parentId);
        dto.setName(name);
        dto.setSort(sort);
        return dto;
    }

    // ==================== 1. addCategory 添加分类测试 ====================

    @Nested
    @DisplayName("addCategory 添加分类")
    class AddCategoryTest {

        @Test
        @DisplayName("正常添加分类：sort有值 → 使用传入的sort，status默认1")
        void addCategory_withSort_useInputSort() {
            // 场景：前端传了sort=5，应该原样写入
            CategoryDTO dto = buildDTO(0L, "手机数码", 5);

            categoryService.addCategory(dto);

            // 用 ArgumentCaptor 抓取传给 mapper.insert 的实体，验证字段是否正确
            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryMapper).insert(captor.capture());
            Category saved = captor.getValue();
            assertThat(saved.getParentId()).isEqualTo(0L);
            assertThat(saved.getName()).isEqualTo("手机数码");
            assertThat(saved.getSort()).isEqualTo(5);
            // status 应该被默认设置为1（启用）
            assertThat(saved.getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("sort为null → 兜底默认为0")
        void addCategory_sortNull_defaultZero() {
            // 场景：前端没传sort（null），代码应该兜底成0，避免排序字段为空
            CategoryDTO dto = buildDTO(0L, "电脑办公", null);

            categoryService.addCategory(dto);

            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryMapper).insert(captor.capture());
            Category saved = captor.getValue();
            assertThat(saved.getSort()).isEqualTo(0);
            assertThat(saved.getStatus()).isEqualTo(1);
        }
    }

    // ==================== 2. updateCategory 修改分类测试 ====================

    @Nested
    @DisplayName("updateCategory 修改分类")
    class UpdateCategoryTest {

        @Test
        @DisplayName("分类不存在 → 抛出 PRODUCT_NOT_FOUND 异常，不执行更新")
        void updateCategory_notExists_throwsException() {
            // 场景：修改一个不存在的分类，应该报错"商品不存在"
            CategoryDTO dto = buildDTO(0L, "新名字", 1);
            when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(null);

            assertThatThrownBy(() -> categoryService.updateCategory(CATEGORY_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PRODUCT_NOT_FOUND.getCode());

            // 验证没有执行更新操作
            verify(categoryMapper, never()).updateById(any(Category.class));
        }

        @Test
        @DisplayName("正常更新分类 → 字段被写入，调用updateById")
        void updateCategory_normal_updateById() {
            // 场景：分类存在，更新名称和排序
            CategoryDTO dto = buildDTO(0L, "新名字", 2);
            Category existing = buildCategory(CATEGORY_ID, 0L, "旧名字", 1);
            when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(existing);

            categoryService.updateCategory(CATEGORY_ID, dto);

            // 验证传给 updateById 的实体字段是新值
            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryMapper).updateById(captor.capture());
            Category updated = captor.getValue();
            assertThat(updated.getName()).isEqualTo("新名字");
            assertThat(updated.getSort()).isEqualTo(2);
            assertThat(updated.getParentId()).isEqualTo(0L);
        }
    }

    // ==================== 3. deleteCategory 删除分类测试 ====================

    @Nested
    @DisplayName("deleteCategory 删除分类")
    class DeleteCategoryTest {

        @Test
        @DisplayName("有子分类 → 抛出 PARAM_ERROR 异常，不查商品也不删除")
        void deleteCategory_hasChildren_throwsException() {
            // 场景：该分类下还有子分类，不能删（防止数据变孤儿）
            when(categoryMapper.selectCount(any())).thenReturn(2L);

            assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            // 验证：因为子分类校验已经失败，不应该再去查商品数，更不应该删除
            verify(productMapper, never()).selectCount(any());
            verify(categoryMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("无子分类但有商品 → 抛出 PARAM_ERROR 异常，不执行删除")
        void deleteCategory_hasProducts_throwsException() {
            // 场景：分类下没有子分类，但有商品引用它，不能删
            when(categoryMapper.selectCount(any())).thenReturn(0L);
            when(productMapper.selectCount(any())).thenReturn(3L);

            assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            // 验证没有执行删除
            verify(categoryMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("无子分类无商品 → 正常删除")
        void deleteCategory_normal_delete() {
            // 场景：分类下既没子分类也没商品，可以安全删除
            when(categoryMapper.selectCount(any())).thenReturn(0L);
            when(productMapper.selectCount(any())).thenReturn(0L);

            categoryService.deleteCategory(CATEGORY_ID);

            // 验证执行了删除
            verify(categoryMapper).deleteById(CATEGORY_ID);
        }
    }

    // ==================== 4. getCategoryTree 分类树构建测试 ====================

    @Nested
    @DisplayName("getCategoryTree 分类树构建")
    class GetCategoryTreeTest {

        @Test
        @DisplayName("空表 → 返回空列表")
        void getCategoryTree_empty_returnEmpty() {
            // 场景：数据库里一个分类都没有，应返回空列表而不是null
            when(categoryMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<CategoryVO> tree = categoryService.getCategoryTree();

            assertThat(tree).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("正常构建树：顶级节点包含子节点列表")
        void getCategoryTree_normal_buildTree() {
            // 场景：一个顶级分类"手机数码"下面有两个子分类"智能手机"和"功能手机"
            Category top = buildCategory(1L, 0L, "手机数码", 1);
            Category child1 = buildCategory(2L, 1L, "智能手机", 1);
            Category child2 = buildCategory(3L, 1L, "功能手机", 2);
            when(categoryMapper.selectList(any())).thenReturn(Arrays.asList(top, child1, child2));

            List<CategoryVO> tree = categoryService.getCategoryTree();

            // 顶级节点只有1个
            assertThat(tree).hasSize(1);
            CategoryVO root = tree.get(0);
            assertThat(root.getId()).isEqualTo(1L);
            assertThat(root.getName()).isEqualTo("手机数码");
            // 顶级节点下应该挂2个子节点
            assertThat(root.getChildren()).hasSize(2);
            assertThat(root.getChildren()).extracting(CategoryVO::getName)
                    .containsExactly("智能手机", "功能手机");
        }

        @Test
        @DisplayName("多个顶级节点：只返回parentId=0的节点，子节点正确归类")
        void getCategoryTree_multipleRoots_onlyReturnTopLevel() {
            // 场景：有两个顶级分类"手机数码"和"电脑办公"，其中"手机数码"下有1个子分类
            Category top1 = buildCategory(1L, 0L, "手机数码", 1);
            Category top2 = buildCategory(2L, 0L, "电脑办公", 2);
            Category child = buildCategory(3L, 1L, "智能手机", 1);
            when(categoryMapper.selectList(any())).thenReturn(Arrays.asList(top1, top2, child));

            List<CategoryVO> tree = categoryService.getCategoryTree();

            // 只返回2个顶级节点（parentId=0）
            assertThat(tree).hasSize(2);
            assertThat(tree).extracting(CategoryVO::getName)
                    .containsExactly("手机数码", "电脑办公");
            // 第一个顶级节点下有1个子分类
            assertThat(tree.get(0).getChildren()).hasSize(1);
            assertThat(tree.get(0).getChildren().get(0).getName()).isEqualTo("智能手机");
            // 第二个顶级节点下没有子分类，children为null（map里取不到就是null）
            assertThat(tree.get(1).getChildren()).isNull();
        }
    }
}
