package com.shop.model.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 商品SKU实体
 * <p>
 * 对应数据库 product_sku 表，存储商品的最小库存单位（SKU = Stock Keeping Unit）。
 * SKU是用户真正购买的东西，比如"iPhone 15 红色 128G"就是一个SKU。
 * 每个SKU有独立的价格和库存，扣库存时使用乐观锁（version字段）防止超卖。
 * specValues字段用JSON格式存储规格组合，比如{"颜色":"红色","存储":"128G"}。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "product_sku", autoResultMap = true)
public class ProductSku extends BaseEntity {

    /** 商品ID（SPU），关联product表，表示这个SKU属于哪个商品 */
    private Long productId;

    /** 规格值组合（JSON格式），比如{"颜色":"红色","存储":"128G"} */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> specValues;

    /** 销售价格，用户实际付的钱 */
    private BigDecimal price;

    /** 原价，用来展示划线价，让用户觉得划算 */
    private BigDecimal originalPrice;

    /** 库存数量，还有多少件可以卖 */
    private Integer stock;

    /** SKU图片URL，不同规格的商品可能有不同的图片 */
    private String image;

    /** 乐观锁版本号，扣库存时用来防止超卖（两个人同时买最后一件，只能有一个人成功） */
    @Version
    private Integer version;

    /** 状态：0禁用 1启用 */
    private Integer status;
}
