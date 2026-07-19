package com.medconsult.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * common-web 端到端集成测试：通过 MockMvc 跑通整条 Web 链路。
 *
 * <p>验证点：
 * <ul>
 *   <li>Controller 返回 POJO → 自动包装成 Result</li>
 *   <li>Controller 返回 Result → 不二次包装，回填 traceId/timestamp</li>
 *   <li>抛 BusinessException → 正确 HTTP 状态码 + Result.fail 结构</li>
 *   <li>抛未知异常 → 500 + Result.fail(INTERNAL_ERROR)</li>
 *   <li>traceId 透传：请求头带 X-Trace-Id → 响应头与 Result.traceId 一致</li>
 *   <li>traceId 兜底：不带请求头 → 响应头自动生成</li>
 *   <li>{@code @Mask} 脱敏字段序列化正确</li>
 * </ul>
 *
 * <p>用 {@code @SpringBootTest(MOCK)}：common-web 是 library 无 @SpringBootApplication，
 * 由 {@link TestApplication} 提供配置锚点并完整扫描 controller / handler / advice / filter。
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class GlobalWebFlowTest {

    @Autowired
    MockMvc mvc;

    private static final String TEST_TRACE = "test-trace-abc123";

    @Test
    void pojoReturn_isWrappedIntoResult_andTraceIdBackfilled() throws Exception {
        mvc.perform(get("/test/pojo").header(TraceIdFilter.TRACE_ID_HEADER, TEST_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.name").value("张*"))
                .andExpect(jsonPath("$.data.phoneMasked").value("138****0001"))
                .andExpect(jsonPath("$.traceId").value(TEST_TRACE))
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, TEST_TRACE))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void resultReturn_notDoubleWrapped_traceIdBackfilled() throws Exception {
        mvc.perform(get("/test/result").header(TraceIdFilter.TRACE_ID_HEADER, TEST_TRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("payload"))
                .andExpect(jsonPath("$.traceId").value(TEST_TRACE))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void businessException_404MapsCorrectly() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404001))
                .andExpect(jsonPath("$.message").value("患者档案不存在: P001"));
    }

    @Test
    void businessException_409MapsCorrectly() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409001));
    }

    @Test
    void actuatorHealth_isNotWrappedByBusinessResultAdvice() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"status":"UP"}
                        """))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void unknownException_mapsTo500AndHidesDetail() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.code").value(500001))
                .andExpect(jsonPath("$.message").value("系统内部错误"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void missingParam_returns400WithDetail() throws Exception {
        mvc.perform(get("/test/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400001));
    }

    @Test
    void noTraceIdHeader_filterGeneratesOne() throws Exception {
        mvc.perform(get("/test/pojo"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    /**
     * 回归：String 返回 + produces JSON 不应被 advice 包装。
     * 修复前会因 StringHttpMessageConverter 写 Result 对象而抛 ClassCastException→500。
     * 修复后期望 200 + body 为 JSON 字符串字面量（Spring 默认会加引号）。
     */
    @Test
    void stringReturn_notWrapped_avoidsConverterMismatch() throws Exception {
        // String 返回不进 advice 包装：避免 StringHttpMessageConverter 写 Result 抛 ClassCastException。
        // 期望 200，body 为原始字符串（StringHttpMessageConverter 按 text/plain 写，不加引号）。
        mvc.perform(get("/test/plain-string"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.equalTo("plain-text")));
    }

    @Test
    void mask_appliedOnSensitiveFields() throws Exception {
        mvc.perform(get("/test/pojo"))
                .andExpect(jsonPath("$.data.phoneMasked").value("138****0001"))
                .andExpect(jsonPath("$.data.idNoMasked").value("110101********0011"))
                .andExpect(jsonPath("$.data.name").value("张*"));
    }
}
