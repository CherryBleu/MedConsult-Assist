package com.medconsult.common.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * common-core 契约测试。验收点：
 * <ul>
 *   <li>Result/ErrorCode 与《接口文档.md》§1 对齐（code/message/httpStatus）</li>
 *   <li>Result.ok / fail 工厂行为正确</li>
 *   <li>BusinessException 携带 ErrorCode 与 detail 不可变</li>
 *   <li>PageQuery 归一化（页码、上限）</li>
 *   <li>PageResult 工厂与 totalPages 计算</li>
 * </ul>
 */
class ResultTest {

    // ===== Result =====

    @Test
    void ok_carriesSuccessCodeAndMessage() {
        Result<String> r = Result.ok("payload");
        assertAll("Result.ok 与《接口文档》§1 success 形状一致",
                () -> assertEquals(0, r.code(), "成功 code 必须 = 0"),
                () -> assertEquals("success", r.message()),
                () -> assertEquals("payload", r.data()),
                () -> assertTrue(r.isSuccess()),
                () -> assertNull(r.traceId(), "traceId 默认空，由 web 层填充"),
                () -> assertNull(r.timestamp(), "timestamp 默认空，由 web 层填充")
        );
    }

    @Test
    void okWithoutData_hasNullData() {
        Result<Void> r = Result.ok();
        assertEquals(0, r.code());
        assertNull(r.data());
    }

    @Test
    void fail_usesErrorCodeCodeAndMessage() {
        Result<Void> r = Result.fail(ErrorCode.PARAM_ERROR);
        assertAll(
                () -> assertEquals(400001, r.code(), "PARAM_ERROR.code = 400001"),
                () -> assertEquals("请求参数错误", r.message()),
                () -> assertFalse(r.isSuccess())
        );
    }

    @Test
    void failWithCustomMessage_keepsCodeButOverridesMessage() {
        Result<Void> r = Result.fail(ErrorCode.NOT_FOUND, "患者档案不存在: P001");
        assertEquals(404001, r.code());
        assertEquals("患者档案不存在: P001", r.message());
    }

    // ===== ErrorCode =====

    @Test
    void errorCode_allValuesAlignWithApiDoc() {
        // 《接口文档.md》§1 通用错误码表 —— 全量断言，任何枚举变动都会被捕获
        assertAll("ErrorCode 与《接口文档》§1 完全对齐",
                () -> assertEquals(0,      ErrorCode.SUCCESS.getCode(),            "SUCCESS"),
                () -> assertEquals(400001, ErrorCode.PARAM_ERROR.getCode(),        "PARAM_ERROR"),
                () -> assertEquals(401001, ErrorCode.UNAUTHORIZED.getCode(),       "UNAUTHORIZED"),
                () -> assertEquals(403001, ErrorCode.FORBIDDEN.getCode(),          "FORBIDDEN"),
                () -> assertEquals(404001, ErrorCode.NOT_FOUND.getCode(),          "NOT_FOUND"),
                () -> assertEquals(409001, ErrorCode.CONFLICT.getCode(),           "CONFLICT"),
                () -> assertEquals(500001, ErrorCode.INTERNAL_ERROR.getCode(),     "INTERNAL_ERROR"),
                () -> assertEquals(502001, ErrorCode.AI_EXTERNAL_FAILED.getCode(), "AI_EXTERNAL_FAILED")
        );
    }

    @Test
    void errorCode_httpStatusMatchesCodePrefix() {
        // 约定：错误码前 3 位 = HTTP 状态码
        for (ErrorCode ec : ErrorCode.values()) {
            int prefix = ec.getCode() == 0 ? 200 : ec.getCode() / 1000;
            assertEquals(ec.getHttpStatus(), prefix,
                    "HTTP 状态码应与 code 前缀一致: " + ec);
        }
    }

    @Test
    void errorCode_ofCode_resolvesAndReturnsNullForUnknown() {
        assertEquals(ErrorCode.NOT_FOUND, ErrorCode.ofCode(404001));
        assertNull(ErrorCode.ofCode(999999));
    }

    // ===== BusinessException =====

    @Test
    void businessException_carriesErrorCodeAndMessage() {
        BusinessException ex = new BusinessException(ErrorCode.CONFLICT, "号源已被抢占");
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        assertEquals("号源已被抢占", ex.getMessage());
        assertNull(ex.getDetail());
    }

    @Test
    void businessException_withDetail_isImmutableSnapshot() {
        Map<String, Object> detail = Map.of("field", "idNo", "reason", "duplicated");
        BusinessException ex = new BusinessException(ErrorCode.PARAM_ERROR, detail);
        assertEquals(detail, ex.getDetail());

        // with() 返回新实例，原实例不变
        BusinessException chained = ex.with("extra", "x");
        assertNotSame(ex, chained);
        assertEquals(2, ex.getDetail().size(), "原异常 detail 不被修改");
        assertEquals(3, chained.getDetail().size(), "新异常 detail 含新增项");
    }

    @Test
    void businessException_withNullDetail_safe() {
        BusinessException ex = new BusinessException(ErrorCode.INTERNAL_ERROR, (Map<String, Object>) null);
        assertNull(ex.getDetail());
        // with 在 null 基础上也能用
        BusinessException chained = ex.with("k", "v");
        assertEquals(Map.of("k", "v"), chained.getDetail());
    }

    // ===== PageQuery =====

    @Test
    void pageQuery_defaultsAndNormalization() {
        PageQuery q = new PageQuery();
        assertEquals(1, q.getPage(), "默认页码 = 1");
        assertEquals(10, q.getPageSize(), "默认每页 = 10");
        assertEquals(0L, q.getOffset());

        q.setPage(3);
        q.setPageSize(20);
        assertEquals(3, q.getPage());
        assertEquals(20, q.getPageSize());
        assertEquals(40L, q.getOffset(), "offset = (page-1)*pageSize");

        // 非法值归一化
        q.setPage(0);
        assertEquals(1, q.getPage(), "页码 < 1 归一为 1");
        q.setPage(-5);
        assertEquals(1, q.getPage());

        q.setPageSize(0);
        assertEquals(10, q.getPageSize(), "pageSize < 1 归为默认 10");
        q.setPageSize(999);
        assertEquals(100, q.getPageSize(), "pageSize 超上限归为 100");
    }

    // ===== PageResult =====

    @Test
    void pageResult_factoryAndTotalPages() {
        PageResult<String> r = PageResult.of(2, 10, 25L, List.of("a", "b"));
        assertEquals(2, r.page());
        assertEquals(10, r.pageSize());
        assertEquals(25L, r.total());
        assertEquals(2, r.items().size());
        assertEquals(3, r.getTotalPages(), "25 条 / 每页 10 = 3 页");
        assertFalse(r.isEmpty());

        PageResult<String> empty = PageResult.empty(1, 10);
        assertEquals(0L, empty.total());
        assertTrue(empty.items().isEmpty());
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.getTotalPages());
    }

    @Test
    void pageResult_totalPagesRoundsUpOnExactMultiple() {
        // 30 条 / 每页 10 = 恰好 3 页
        PageResult<Object> r = PageResult.of(1, 10, 30L, List.of());
        assertEquals(3, r.getTotalPages());

        // 31 条 → 4 页
        PageResult<Object> r2 = PageResult.of(1, 10, 31L, List.of());
        assertEquals(4, r2.getTotalPages());
    }
}
