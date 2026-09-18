package com.shop.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.product.dto.BrandDTO;
import com.shop.model.product.entity.Brand;
import com.shop.model.product.entity.Product;
import com.shop.model.product.vo.BrandVO;
import com.shop.product.mapper.BrandMapper;
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

import java.time.LocalDateTime;
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
 * 商品品牌服务实现类（BrandServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证品牌的增删改查逻辑。
 * 简单理解：把 BrandMapper、ProductMapper 都"假装"一下（Mock），
 * 不用真的连数据库，跑得又快又稳。
 * </p>
 * <p>
 * 小白快速理解：
 * - @ExtendWith(MockitoExtension.class)：让 Mockito 框架接管测试环境
 * - @Mock：造一个假的 Mapper，方法调用都由我们控制返回值
 * - @InjectMocks：被测试的 Service，Mockito 会把上面的 @Mock 自动注入进来
 * - @BeforeAll：所有测试运行前执行一次，这里用来初始化 MyBatis-Plus 缓存
 * - @Nested：把"同一个方法的多个测试"分组，看起来更清晰
 * - AssertJ：提供 assertThat(x).isEqualTo(y) 这种易读的断言写法
 * </p>
 * <p>
 * 覆盖的方法：addBrand、updateBrand、deleteBrand、getBrandList、getBrandById
 * </p>
 */
@DisplayName("商品品牌服务 BrandServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrandServiceImplTest {

    // ==================== Mock 依赖（都是假的，不真的连数据库） ====================

    /** 品牌 Mapper，操作 brand 表 */
    @Mock
    private BrandMapper brandMapper;

    /** 商品 Mapper，删除品牌时用来检查品牌下有没有商品 */
    @Mock
    private ProductMapper productMapper;

    /** 被测试的品牌服务，Mockito 会自动把上面两个 Mock 注入进来 */
    @InjectMocks
    private BrandServiceImpl brandService;

    /** 常用测试数据：品牌ID */
    private static final Long BRAND_ID = 7001L;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：BrandServiceImpl 用了 LambdaQueryWrapper，
     * 比如 .eq(Product::getBrandId, ...)、.orderByDesc(Brand::getCreateTime)。
     * 这些代码会让 MyBatis-Plus 去查"字段对应数据库哪一列"。
     * 正常启动 Spring 时框架会自动做这件事，但单元测试没有 Spring 环境，
     * 所以需要手动告诉 MyBatis-Plus：Brand、Product 这些实体有哪些字段、对应哪些列。
     * 不初始化会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        // 初始化所有用到 Lambda 查询的实体的缓存
        TableInfoHelper.initTableInfo(assistant, Brand.class);
        TableInfoHelper.initTableInfo(assistant, Product.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个品牌实体
     *
     * @param id   品牌ID
     * @param name 品牌名称
     * @return 构造好的 Brand 实体
     */
    private Brand buildBrand(Long id, String name) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setName(name);
        brand.setLogo("logo.png");
        brand.setDescription("品牌描述");
        brand.setCreateTime(LocalDateTime.now());
        return brand;
    }

    /**
     * 构造一个品牌请求DTO
     *
     * @param name 品牌名称
     * @return 构造好的 BrandDTO
     */
    private BrandDTO buildDTO(String name) {
        BrandDTO dto = new BrandDTO();
        dto.setName(name);
        dto.setLogo("new-logo.png");
        dto.setDescription("新描述");
        return dto;
    }

    // ==================== 1. addBrand 添加品牌测试 ====================

    @Nested
    @DisplayName("addBrand 添加品牌")
    class AddBrandTest {

        @Test
        @DisplayName("正常添加品牌：DTO字段全部写入实体")
        void addBrand_normal_insert() {
            // 场景：前端传入品牌信息，应原样写入数据库
            BrandDTO dto = buildDTO("苹果");

            brandService.addBrand(dto);

            // 用 ArgumentCaptor 抓取传给 mapper.insert 的实体，验证字段是否正确
            ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
            verify(brandMapper).insert(captor.capture());
            Brand saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("苹果");
            assertThat(saved.getLogo()).isEqualTo("new-logo.png");
            assertThat(saved.getDescription()).isEqualTo("新描述");
        }
    }

    // ==================== 2. updateBrand 修改品牌测试 ====================

    @Nested
    @DisplayName("updateBrand 修改品牌")
    class UpdateBrandTest {

        @Test
        @DisplayName("品牌不存在 → 抛出 PRODUCT_NOT_FOUND 异常，不执行更新")
        void updateBrand_notExists_throwsException() {
            // 场景：修改一个不存在的品牌，应该报错"商品不存在"
            BrandDTO dto = buildDTO("新名字");
            when(brandMapper.selectById(BRAND_ID)).thenReturn(null);

            assertThatThrownBy(() -> brandService.updateBrand(BRAND_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PRODUCT_NOT_FOUND.getCode());

            // 验证没有执行更新操作
            verify(brandMapper, never()).updateById(any(Brand.class));
        }

        @Test
        @DisplayName("正常更新品牌 → 字段被写入，调用updateById")
        void updateBrand_normal_updateById() {
            // 场景：品牌存在，更新名称、Logo、描述
            BrandDTO dto = buildDTO("华为");
            Brand existing = buildBrand(BRAND_ID, "旧名字");
            when(brandMapper.selectById(BRAND_ID)).thenReturn(existing);

            brandService.updateBrand(BRAND_ID, dto);

            // 验证传给 updateById 的实体字段是新值
            ArgumentCaptor<Brand> captor = ArgumentCaptor.forClass(Brand.class);
            verify(brandMapper).updateById(captor.capture());
            Brand updated = captor.getValue();
            assertThat(updated.getName()).isEqualTo("华为");
            assertThat(updated.getLogo()).isEqualTo("new-logo.png");
            assertThat(updated.getDescription()).isEqualTo("新描述");
        }
    }

    // ==================== 3. deleteBrand 删除品牌测试 ====================

    @Nested
    @DisplayName("deleteBrand 删除品牌")
    class DeleteBrandTest {

        @Test
        @DisplayName("品牌下有商品 → 抛出 PARAM_ERROR 异常，不执行删除")
        void deleteBrand_hasProducts_throwsException() {
            // 场景：该品牌下还有商品引用它，不能删（防止数据变孤儿）
            when(productMapper.selectCount(any())).thenReturn(5L);

            assertThatThrownBy(() -> brandService.deleteBrand(BRAND_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            // 验证没有执行删除
            verify(brandMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("品牌下无商品 → 正常删除")
        void deleteBrand_noProducts_normalDelete() {
            // 场景：品牌下没有商品引用，可以安全删除
            when(productMapper.selectCount(any())).thenReturn(0L);

            brandService.deleteBrand(BRAND_ID);

            // 验证执行了删除
            verify(brandMapper).deleteById(BRAND_ID);
        }
    }

    // ==================== 4. getBrandList 品牌列表测试 ====================

    @Nested
    @DisplayName("getBrandList 品牌列表")
    class GetBrandListTest {

        @Test
        @DisplayName("正常返回品牌列表：VO字段正确填充")
        void getBrandList_normal_returnVOList() {
            // 场景：数据库里有2个品牌，查询应返回2个VO，字段正确
            Brand b1 = buildBrand(1L, "苹果");
            Brand b2 = buildBrand(2L, "华为");
            when(brandMapper.selectList(any())).thenReturn(Arrays.asList(b1, b2));

            List<BrandVO> list = brandService.getBrandList();

            assertThat(list).hasSize(2);
            assertThat(list).extracting(BrandVO::getName).containsExactly("苹果", "华为");
            // 验证VO字段被正确填充
            assertThat(list.get(0).getId()).isEqualTo(1L);
            assertThat(list.get(0).getLogo()).isEqualTo("logo.png");
            assertThat(list.get(0).getDescription()).isEqualTo("品牌描述");
        }

        @Test
        @DisplayName("无品牌数据 → 返回空列表")
        void getBrandList_empty_returnEmpty() {
            // 场景：数据库里一个品牌都没有，应返回空列表而不是null
            when(brandMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<BrandVO> list = brandService.getBrandList();

            assertThat(list).isNotNull().isEmpty();
        }
    }

    // ==================== 5. getBrandById 品牌详情测试 ====================

    @Nested
    @DisplayName("getBrandById 品牌详情")
    class GetBrandByIdTest {

        @Test
        @DisplayName("品牌不存在 → 抛出 PRODUCT_NOT_FOUND 异常")
        void getBrandById_notExists_throwsException() {
            // 场景：查询一个不存在的品牌，应该报错"商品不存在"
            when(brandMapper.selectById(BRAND_ID)).thenReturn(null);

            assertThatThrownBy(() -> brandService.getBrandById(BRAND_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PRODUCT_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("正常查询：返回品牌VO，字段正确填充")
        void getBrandById_normal_returnVO() {
            // 场景：品牌存在，查询应返回对应的VO
            Brand brand = buildBrand(BRAND_ID, "小米");
            when(brandMapper.selectById(BRAND_ID)).thenReturn(brand);

            BrandVO vo = brandService.getBrandById(BRAND_ID);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(BRAND_ID);
            assertThat(vo.getName()).isEqualTo("小米");
            assertThat(vo.getLogo()).isEqualTo("logo.png");
            assertThat(vo.getDescription()).isEqualTo("品牌描述");
        }
    }
}
