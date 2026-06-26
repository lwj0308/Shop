package com.shop.model.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商家资质实体
 * <p>
 * 对应数据库 merchant_qualification 表，存储商家的营业执照等资质信息。
 * 商家入驻时必须提交资质，管理员审核通过后商家才能正常经营。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant_qualification")
public class MerchantQualification extends BaseEntity {

    /** 商家ID，关联 merchant 表的 id */
    private Long merchantId;

    /** 营业执照号，比如"91110108MA01XXXXX" */
    private String licenseNo;

    /** 营业执照图片地址，审核员要看图片确认资质真实性 */
    private String licenseImg;

    /** 法人姓名，营业执照上的法定代表人 */
    private String legalPerson;

    /** 审核状态：0待审核 1已通过 2已拒绝 */
    private Integer status;

    /** 审核备注，管理员审核时填写的意见，比如"营业执照模糊，请重新上传" */
    private String auditNote;
}
