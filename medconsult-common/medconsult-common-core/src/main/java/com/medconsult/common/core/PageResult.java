package com.medconsult.common.core;

import java.util.List;

/**
 * 分页结果。字段命名与《接口文档.md》各分页接口返回示例一致：
 * <pre>
 * { "page": 1, "pageSize": 10, "total": 1, "items": [...] }
 * </pre>
 *
 * @param page     当前页（从 1 开始）
 * @param pageSize 每页大小
 * @param total    总条数
 * @param items    当前页数据
 */
public record PageResult<T>(
        int page,
        int pageSize,
        long total,
        List<T> items
) {

    /**
     * 便捷工厂：从 MyBatis-Plus 的 IPage 或任意分页源构造。
     */
    public static <T> PageResult<T> of(int page, int pageSize, long total, List<T> items) {
        return new PageResult<>(page, pageSize, total, items == null ? List.of() : items);
    }

    /**
     * 空结果。
     */
    public static <T> PageResult<T> empty(int page, int pageSize) {
        return new PageResult<>(page, pageSize, 0L, List.of());
    }

    /**
     * 总页数（向上取整）。便于前端渲染分页器。
     */
    public int getTotalPages() {
        return pageSize <= 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
    }

    public boolean isEmpty() {
        return total == 0L;
    }
}
