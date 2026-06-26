package com.shop.common.model;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应结果
 * <p>
 * 包含分页数据和分页信息（总条数、总页数等）。
 * 前端拿到这个对象后，可以渲染分页组件和数据列表。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 *     // 方式1：手动构建分页结果
 *     PageResult&lt;UserVO&gt; pageResult = new PageResult&lt;&gt;();
 *     pageResult.setRecords(userList);    // 当前页的数据列表
 *     pageResult.setTotal(100L);          // 总共有100条数据
 *     pageResult.setPageNum(1);           // 当前是第1页
 *     pageResult.setPageSize(10);         // 每页10条
 *     pageResult.setPages(10);            // 总共10页
 *
 *     // 方式2：使用setPagination便捷方法（自动计算总页数）
 *     PageResult&lt;UserVO&gt; pageResult = new PageResult&lt;&gt;();
 *     pageResult.setRecords(userList);
 *     pageResult.setPagination(100L, 1, 10);
 *
 *     // 方式3：从MyBatis-Plus的IPage转换（推荐，最方便）
 *     IPage&lt;User&gt; iPage = userMapper.selectPage(page, query);
 *     PageResult&lt;UserVO&gt; pageResult = PageResult.from(iPage);
 * </pre>
 * </p>
 *
 * @param <T> 数据列表中每条记录的类型
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 数据列表：当前页的数据记录 */
    private List<T> records = Collections.emptyList();

    /** 总条数：满足条件的记录一共有多少条 */
    private long total;

    /** 当前页码：现在查的是第几页 */
    private int pageNum;

    /** 每页条数：一页显示多少条 */
    private int pageSize;

    /** 总页数：一共可以分多少页（总条数÷每页条数，向上取整） */
    private int pages;

    /**
     * 根据总条数和每页条数自动计算总页数
     * 这是个便捷方法，不用手动算总页数了
     *
     * @param total    总条数
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     */
    public void setPagination(long total, int pageNum, int pageSize) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        // 计算总页数：如果总条数是0，总页数也是0；否则向上取整
        this.pages = (total == 0) ? 0 : (int) Math.ceil((double) total / pageSize);
    }

    /**
     * 从MyBatis-Plus的IPage对象转换成分页结果
     * 这是最方便的方式，MyBatis-Plus查完分页后直接转
     *
     * @param iPage MyBatis-Plus的分页对象
     * @param <T>   数据类型
     * @return 转换后的分页结果
     */
    public static <T> PageResult<T> from(IPage<T> iPage) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(iPage.getRecords());
        result.setTotal(iPage.getTotal());
        result.setPageNum((int) iPage.getCurrent());
        result.setPageSize((int) iPage.getSize());
        result.setPages((int) iPage.getPages());
        return result;
    }

    /**
     * 从MyBatis-Plus的IPage对象转换，同时转换数据类型
     * 比如IPage里是Entity，但你想返回VO，就可以用这个方法
     *
     * @param iPage    MyBatis-Plus的分页对象
     * @param records  转换后的数据列表（比如Entity转VO后的列表）
     * @param <T>      目标数据类型（比如VO）
     * @param <S>      源数据类型（比如Entity）
     * @return 转换后的分页结果
     */
    public static <T, S> PageResult<T> from(IPage<S> iPage, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(iPage.getTotal());
        result.setPageNum((int) iPage.getCurrent());
        result.setPageSize((int) iPage.getSize());
        result.setPages((int) iPage.getPages());
        return result;
    }

    /**
     * 创建一个空的分页结果
     * 用于暂时未实现查询逻辑时返回空分页，避免返回null
     *
     * @param <T> 数据类型
     * @return 空的分页结果（records为空列表，total为0）
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>();
    }
}
