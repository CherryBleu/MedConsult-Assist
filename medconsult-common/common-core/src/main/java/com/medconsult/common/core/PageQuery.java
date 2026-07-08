package com.medconsult.common.core;

/**
 * 分页查询基类。约定（对齐《修改建议.md》§6.6）：
 * <ul>
 *   <li>page 从 1 开始</li>
 *   <li>pageSize 默认 10，最大 100（防止大数据量拖垮 DB）</li>
 * </ul>
 *
 * <p>各业务查询 DTO 继承本类，追加自己的筛选字段。
 *
 * <p>参数校验在 common-web 层（@Valid 触发），本基类只负责默认值与边界归一化。
 */
public class PageQuery {

    /** 默认每页条数（《修改建议.md》§6.6 统一） */
    public static final int DEFAULT_PAGE_SIZE = 10;
    /** 最大每页条数（防止滥用） */
    public static final int MAX_PAGE_SIZE = 100;

    private int page = 1;
    private int pageSize = DEFAULT_PAGE_SIZE;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        // 归一化：小于 1 的页码回到第 1 页
        this.page = page < 1 ? 1 : page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        // 归一化：限制在 [1, MAX_PAGE_SIZE]
        if (pageSize < 1) {
            this.pageSize = DEFAULT_PAGE_SIZE;
        } else if (pageSize > MAX_PAGE_SIZE) {
            this.pageSize = MAX_PAGE_SIZE;
        } else {
            this.pageSize = pageSize;
        }
    }

    /**
     * 用于 SQL 的 offset（MyBatis-Plus 分页插件可直接吃 page/pageSize，这里提供 offset 便于手写 SQL）。
     */
    public long getOffset() {
        return (long) (page - 1) * pageSize;
    }
}
