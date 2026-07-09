package com.medconsult.ai.common;

import java.util.List;

public record PageResult<T>(
        long page,
        long pageSize,
        long total,
        List<T> items
) {
}
