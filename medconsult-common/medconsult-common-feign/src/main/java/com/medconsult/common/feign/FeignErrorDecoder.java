package com.medconsult.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.Result;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Feign 错误解码器（架构文档 §3.2）。
 *
 * <p>把下游服务返回的 4xx/5xx 错误响应（其 body 是 {@link Result} JSON）解码回
 * {@link BusinessException}，让调用方代码可以像本地抛异常一样处理（统一 try/catch）。
 *
 * <p>映射规则：
 * <ul>
 *   <li>能解析出 Result.code → 用对应 ErrorCode 抛 BusinessException（保留下游 message）</li>
 *   <li>解析失败/非业务错误 → 按 HTTP 状态码映射（401→UNAUTHORIZED, 403→FORBIDDEN, 5xx→INTERNAL/AI_EXTERNAL）</li>
 *   <li>408/ReadTimeout → 网关超时，CONFLICT 或专门错误</li>
 * </ul>
 */
public class FeignErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(FeignErrorDecoder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();

        // 尝试解析下游的 Result body
        Result<?> downstreamResult = tryParseResult(response);

        if (downstreamResult != null) {
            ErrorCode ec = ErrorCode.ofCode(downstreamResult.code());
            if (ec == null) {
                ec = mapByStatus(status);
            }
            String msg = downstreamResult.message() != null ? downstreamResult.message() : ec.getMessage();
            log.warn("Feign 调用下游业务拒绝: {} status={} code={} msg={}", methodKey, status, ec, msg);
            return new BusinessException(ec, msg);
        }

        // 非 Result body，按 HTTP 状态码映射
        ErrorCode ec = mapByStatus(status);
        log.warn("Feign 调用下游非业务错误: {} status={} -> {}", methodKey, status, ec);
        return new BusinessException(ec, "下游服务异常: HTTP " + status);
    }

    private Result<?> tryParseResult(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream is = response.body().asInputStream()) {
            return MAPPER.readValue(is, Result.class);
        } catch (IOException e) {
            // body 不是 Result JSON（如网关 HTML 错误页），忽略
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private ErrorCode mapByStatus(int status) {
        if (status == 401) return ErrorCode.UNAUTHORIZED;
        if (status == 403) return ErrorCode.FORBIDDEN;
        if (status == 404) return ErrorCode.NOT_FOUND;
        if (status == 409) return ErrorCode.CONFLICT;
        if (status == 502 || status == 504) return ErrorCode.AI_EXTERNAL_FAILED; // 网关/外部
        if (status >= 500) return ErrorCode.INTERNAL_ERROR;
        if (status == 400) return ErrorCode.PARAM_ERROR;
        return ErrorCode.INTERNAL_ERROR;
    }
}
