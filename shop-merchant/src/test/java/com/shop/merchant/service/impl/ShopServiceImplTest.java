package com.shop.merchant.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.merchant.mapper.ShopMapper;
import com.shop.model.merchant.dto.ShopDTO;
import com.shop.model.merchant.entity.Shop;
import com.shop.model.merchant.vo.ShopVO;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 店铺服务实现类（ShopServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证店铺的各种操作能不能正常工作，比如创建店铺、修改店铺信息、查询店铺等。
 * 简单理解：我们把真正访问数据库的 ShopMapper "假装"一下（Mock），
 * 这样测试就不需要真的连数据库，跑得又快又稳定。
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架，提供 @Test、@DisplayName 等注解
 * - Mockito：用来"假装"依赖的对象（Mock），让它们返回我们指定的值
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isEqualTo(1)
 * </p>
 * <p>
 * 测试覆盖的4个方法：createShop、updateShop、getShopInfo、getShopByMerchantId
 * </p>
 */
@DisplayName("店铺服务 ShopServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class ShopServiceImplTest {

    /** 假装店铺信息 Mapper，不真的连数据库 */
    @Mock
    private ShopMapper shopMapper;

    /** 被测试的店铺服务，Mockito 会自动把上面这个 Mock 注入进来 */
    @InjectMocks
    private ShopServiceImpl shopService;

    // 常用的测试数据，用常量定义方便复用
    private static final Long SHOP_ID = 1L;         // 店铺ID
    private static final Long MERCHANT_ID = 1001L;   // 商家ID

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：ShopServiceImpl 里用到了 new LambdaQueryWrapper<Shop>().eq(Shop::getMerchantId, ...)，
     * 这行代码会让 MyBatis-Plus 去查"merchantId 字段对应数据库哪一列"。
     * 正常启动 Spring 时框架会自动做这件事，但单元测试没有 Spring 环境，
     * 所以需要我们手动告诉 MyBatis-Plus：Shop 这个实体有哪些字段。
     * 不初始化的话会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Shop.class
        );
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个店铺实体
     * 小白理解：数据库 shop 表里的一条记录
     *
     * @param id         店铺ID
     * @param merchantId 商家ID
     * @param name       店铺名称
     * @param status     状态：0关闭 1正常
     * @return 构造好的Shop
     */
    private Shop buildShop(Long id, Long merchantId, String name, Integer status) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setMerchantId(merchantId);
        shop.setName(name);
        shop.setStatus(status);
        return shop;
    }

    /**
     * 构造一个店铺信息修改DTO
     * 小白理解：商家修改店铺信息时前端传过来的参数
     *
     * @param name 店铺名称
     * @return 构造好的ShopDTO
     */
    private ShopDTO buildShopDTO(String name) {
        ShopDTO dto = new ShopDTO();
        dto.setName(name);
        dto.setLogo("https://example.com/logo.png");
        dto.setBanner("https://example.com/banner.png");
        dto.setDescription("店铺描述");
        return dto;
    }

    // ==================== 1. createShop 创建店铺 ====================

    @Nested
    @DisplayName("createShop 创建店铺")
    class CreateShopTest {

        @Test
        @DisplayName("店铺名称已存在 → 抛出SHOP_NAME_EXISTS异常")
        void createShop_nameExists_throwsException() {
            // 场景：创建店铺时，店铺名称已经被别人用了
            ShopDTO dto = buildShopDTO("已存在店铺");

            // 模拟：名称查到1条（重复）
            when(shopMapper.selectCount(any())).thenReturn(1L);

            // 验证：抛出"店铺名称已存在"异常，错误码20015
            assertThatThrownBy(() -> shopService.createShop(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.SHOP_NAME_EXISTS.getCode());

            // 验证：校验失败，没有调用insert
            verify(shopMapper, never()).insert(any(Shop.class));
        }

        @Test
        @DisplayName("正常创建店铺 → 调用insert，状态为正常(1)")
        void createShop_normal_success() {
            // 场景：店铺名称不重复，正常创建店铺
            ShopDTO dto = buildShopDTO("新店铺");

            // 模拟：名称查到0条（不重复）
            when(shopMapper.selectCount(any())).thenReturn(0L);

            // 执行创建
            ShopVO result = shopService.createShop(MERCHANT_ID, dto);

            // 验证：调用了insert
            verify(shopMapper).insert(any(Shop.class));

            // 验证：返回的VO信息正确
            assertThat(result.getName()).isEqualTo("新店铺");
            assertThat(result.getMerchantId()).isEqualTo(MERCHANT_ID);
            // 验证：状态为正常(1)
            assertThat(result.getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("shopDTO为null → 使用默认值创建店铺，不检查名称")
        void createShop_nullShopDTO_usesDefaults() {
            // 场景：商家审核通过时系统自动创建默认店铺，传的shopDTO是null
            // 此时不需要检查名称唯一性（因为没有传名称）

            // 执行创建（shopDTO传null）
            ShopVO result = shopService.createShop(MERCHANT_ID, null);

            // 验证：调用了insert
            verify(shopMapper).insert(any(Shop.class));
            // 验证：没有调用selectCount（因为shopDTO为null时不检查名称）
            verify(shopMapper, never()).selectCount(any());

            // 验证：返回的VO商家ID正确，状态为正常(1)
            assertThat(result.getMerchantId()).isEqualTo(MERCHANT_ID);
            assertThat(result.getStatus()).isEqualTo(1);
        }
    }

    // ==================== 2. updateShop 更新店铺信息 ====================

    @Nested
    @DisplayName("updateShop 更新店铺信息")
    class UpdateShopTest {

        @Test
        @DisplayName("店铺不存在 → 抛出SHOP_NOT_FOUND异常")
        void updateShop_notFound_throwsException() {
            // 场景：修改一个不存在的店铺
            ShopDTO dto = buildShopDTO("新名称");

            when(shopMapper.selectById(SHOP_ID)).thenReturn(null);

            assertThatThrownBy(() -> shopService.updateShop(SHOP_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.SHOP_NOT_FOUND.getCode());

            verify(shopMapper, never()).updateById(any(Shop.class));
        }

        @Test
        @DisplayName("店铺已关闭 → 抛出SHOP_CLOSED异常")
        void updateShop_closed_throwsException() {
            // 场景：已关闭的店铺不能修改信息
            ShopDTO dto = buildShopDTO("新名称");
            Shop shop = buildShop(SHOP_ID, MERCHANT_ID, "旧名称", 0); // status=0表示关闭

            when(shopMapper.selectById(SHOP_ID)).thenReturn(shop);

            assertThatThrownBy(() -> shopService.updateShop(SHOP_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.SHOP_CLOSED.getCode());

            verify(shopMapper, never()).updateById(any(Shop.class));
        }

        @Test
        @DisplayName("店铺名称重复 → 抛出SHOP_NAME_EXISTS异常")
        void updateShop_nameExists_throwsException() {
            // 场景：修改名称时，新名称已被其他店铺使用
            ShopDTO dto = buildShopDTO("已存在名称");
            Shop shop = buildShop(SHOP_ID, MERCHANT_ID, "旧名称", 1); // 正常状态

            when(shopMapper.selectById(SHOP_ID)).thenReturn(shop);
            // 模拟：名称查到1条（重复）
            when(shopMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> shopService.updateShop(SHOP_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.SHOP_NAME_EXISTS.getCode());

            verify(shopMapper, never()).updateById(any(Shop.class));
        }

        @Test
        @DisplayName("正常更新店铺信息 → 调用updateById")
        void updateShop_normal_success() {
            // 场景：店铺存在且正常，名称没变，正常更新其他信息
            ShopDTO dto = new ShopDTO();
            dto.setLogo("https://example.com/new_logo.png");
            dto.setDescription("新描述");
            // 不传name，名称保持不变

            Shop shop = buildShop(SHOP_ID, MERCHANT_ID, "店铺名称", 1); // 正常状态

            when(shopMapper.selectById(SHOP_ID)).thenReturn(shop);

            // 执行更新
            shopService.updateShop(SHOP_ID, dto);

            // 捕获传给updateById的shop，验证信息已更新
            ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
            verify(shopMapper).updateById(captor.capture());
            Shop capturedShop = captor.getValue();

            // 验证：Logo和描述已更新
            assertThat(capturedShop.getLogo()).isEqualTo("https://example.com/new_logo.png");
            assertThat(capturedShop.getDescription()).isEqualTo("新描述");
            // 验证：名称保持不变（因为DTO里没传name）
            assertThat(capturedShop.getName()).isEqualTo("店铺名称");
        }
    }

    // ==================== 3. getShopInfo 获取店铺信息 ====================

    @Nested
    @DisplayName("getShopInfo 获取店铺信息")
    class GetShopInfoTest {

        @Test
        @DisplayName("店铺不存在 → 抛出SHOP_NOT_FOUND异常")
        void getShopInfo_notFound_throwsException() {
            when(shopMapper.selectById(SHOP_ID)).thenReturn(null);

            assertThatThrownBy(() -> shopService.getShopInfo(SHOP_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.SHOP_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("正常查询 → 返回店铺信息")
        void getShopInfo_normal_returnsVO() {
            // 场景：查询存在的店铺信息
            Shop shop = buildShop(SHOP_ID, MERCHANT_ID, "测试店铺", 1);
            shop.setLogo("https://example.com/logo.png");
            shop.setDescription("店铺描述");

            when(shopMapper.selectById(SHOP_ID)).thenReturn(shop);

            // 执行查询
            ShopVO vo = shopService.getShopInfo(SHOP_ID);

            // 验证：返回了正确的店铺信息
            assertThat(vo.getId()).isEqualTo(SHOP_ID);
            assertThat(vo.getMerchantId()).isEqualTo(MERCHANT_ID);
            assertThat(vo.getName()).isEqualTo("测试店铺");
            assertThat(vo.getLogo()).isEqualTo("https://example.com/logo.png");
            assertThat(vo.getStatus()).isEqualTo(1);
        }
    }

    // ==================== 4. getShopByMerchantId 根据商家ID获取店铺 ====================

    @Nested
    @DisplayName("getShopByMerchantId 根据商家ID获取店铺")
    class GetShopByMerchantIdTest {

        @Test
        @DisplayName("商家还没有店铺 → 返回null")
        void getShopByMerchantId_notFound_returnsNull() {
            // 场景：商家还没审核通过，没有店铺
            when(shopMapper.selectOne(any())).thenReturn(null);

            ShopVO vo = shopService.getShopByMerchantId(MERCHANT_ID);

            // 验证：返回null
            assertThat(vo).isNull();
        }

        @Test
        @DisplayName("正常查询 → 返回店铺信息")
        void getShopByMerchantId_normal_returnsVO() {
            // 场景：商家已审核通过，有店铺
            Shop shop = buildShop(SHOP_ID, MERCHANT_ID, "测试店铺", 1);

            when(shopMapper.selectOne(any())).thenReturn(shop);

            ShopVO vo = shopService.getShopByMerchantId(MERCHANT_ID);

            // 验证：返回了正确的店铺信息
            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(SHOP_ID);
            assertThat(vo.getMerchantId()).isEqualTo(MERCHANT_ID);
            assertThat(vo.getName()).isEqualTo("测试店铺");
        }
    }
}
