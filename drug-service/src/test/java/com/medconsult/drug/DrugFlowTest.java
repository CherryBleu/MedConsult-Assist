package com.medconsult.drug;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 药品库存服务全流程集成测试：H2 内存库 + Redis 16379（无 Nacos，排除 discovery/config）。
 *
 * <p>覆盖《接口文档》§2.7 关键路径 + FEFO 选批算法（架构文档 §7.1 库存锁）：
 * <ul>
 *   <li>创建药品（drugNo 生成 / current_stock=0）</li>
 *   <li>入库（建批次，current_stock 增加，INBOUND flow）</li>
 *   <li>FEFO 出库（建两个批次不同有效期，验证先扣近效期的——expire_date ASC）</li>
 *   <li>库存不足出库拒绝（CONFLICT）</li>
 *   <li>过期批次不可出库（建一个过期批次 + 一个正常批次，出库跳过过期的）</li>
 *   <li>流水查询（INBOUND/OUTBOUND 都有记录）</li>
 *   <li>预警查询（LOW_STOCK：current_stock &lt; threshold）</li>
 *   <li>内部接口 getRiskInfo + ffeoBatches</li>
 * </ul>
 *
 * <p>注意：H2 不支持 MySQL JSON 函数，contraindications/interactions 用 TEXT 存 JSON 串，Service 层仍可解析。
 * H2 MySQL 模式支持 FOR UPDATE，但 MOCK 环境无真实并发，锁主要走 Redis。
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "medconsult.security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef-min32bytes",
        // 测试用 H2 内存库（MySQL dialect 模式），不连真实 MySQL 避免污染数据
        "spring.datasource.url=jdbc:h2:mem:medconsult_drug_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        // Redis 仍连真实容器（infra/docker-compose.yml，测试需先启动），库存锁依赖
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=16379",
        // 禁用 Nacos discovery/config，避免测试连接 Nacos
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.bootstrap.enabled=false"
})
class DrugFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    void createDrug_assignsDrugNoAndZeroStock() throws Exception {
        String body = """
                {"genericName":"硝苯地平","tradeName":"拜新同","specification":"30mg*7片",
                 "dosageForm":"控释片","manufacturer":"示例制药","unit":"盒","minStockThreshold":50,
                 "contraindications":["严重低血压","心源性休克"]}""";
        mvc.perform(post("/api/v1/drugs").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.drugId").exists())
                .andExpect(jsonPath("$.data.genericName").value("硝苯地平"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // 列表查询：stockQuantity=0
        mvc.perform(get("/api/v1/drugs").param("keyword", "硝苯地平"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].genericName").value("硝苯地平"))
                .andExpect(jsonPath("$.data.items[0].stockQuantity").value(0))
                .andExpect(jsonPath("$.data.items[0].unit").value("盒"));
    }

    @Test
    void inbound_createsBatchAndIncreasesStock() throws Exception {
        String drugNo = createDrug("阿莫西林", 50);

        String inboundBody = """
                {"batchNo":"BATCH001","quantity":100,"unitPrice":12.50,
                 "productionDate":"2026-01-01","expireDate":"2028-06-01","supplier":"供应链公司"}""";
        mvc.perform(post("/api/v1/drugs/" + drugNo + "/stock/inbound")
                        .contentType("application/json").content(inboundBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.stockFlowId").exists())
                .andExpect(jsonPath("$.data.drugId").value(drugNo))
                .andExpect(jsonPath("$.data.currentStock").value(100));

        // 再次入库同批次：累加 quantity
        mvc.perform(post("/api/v1/drugs/" + drugNo + "/stock/inbound")
                        .contentType("application/json").content(inboundBody))
                .andExpect(jsonPath("$.data.currentStock").value(200));
    }

    @Test
    void outbound_ffeoPicksNearestExpiryFirst() throws Exception {
        String drugNo = createDrug("布洛芬", 10);
        // 批次 A：远效期（2028），入库 100
        inbound(drugNo, "BATCH_FAR", 100, "2028-12-31");
        // 批次 B：近效期（今天 +60 天），入库 50 —— FEFO 应优先扣这个
        String nearExpire = LocalDate.now().plusDays(60).toString();
        inbound(drugNo, "BATCH_NEAR", 50, nearExpire);

        // 出库 30：FEFO 应全部从近效期 BATCH_NEAR 扣（near 还剩 20，far 仍 100）
        String outBody = """
                {"quantity":30,"purpose":"PRESCRIPTION","relatedRecordId":"MR001"}""";
        mvc.perform(post("/api/v1/drugs/" + drugNo + "/stock/outbound")
                        .contentType("application/json").content(outBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.stockFlowId").exists())
                .andExpect(jsonPath("$.data.currentStock").value(120)); // 150 - 30

        // 查流水：应有 2 条 INBOUND + 1 条 OUTBOUND，OUTBOUND 的 batchNo 应是 BATCH_NEAR
        mvc.perform(get("/api/v1/drugs/" + drugNo + "/stock/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].type").value("OUTBOUND")) // 按 createdAt DESC
                .andExpect(jsonPath("$.data.items[0].quantity").value(30))
                .andExpect(jsonPath("$.data.items[0].batchNo").value("BATCH_NEAR"));
    }

    @Test
    void outbound_insufficientStockRejected() throws Exception {
        String drugNo = createDrug("止咳糖浆", 10);
        inbound(drugNo, "BATCH001", 5, "2028-01-01");

        // 出库 10 > 库存 5 → CONFLICT
        String outBody = """
                {"quantity":10,"purpose":"DISPENSE"}""";
        mvc.perform(post("/api/v1/drugs/" + drugNo + "/stock/outbound")
                        .contentType("application/json").content(outBody))
                .andExpect(jsonPath("$.code").value(409001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("库存不足")));
    }

    @Test
    void outbound_skipsExpiredBatch() throws Exception {
        String drugNo = createDrug("维生素C", 10);
        // 批次 A：已过期（昨天），入库 100 —— 不可出库
        inbound(drugNo, "BATCH_EXPIRED", 100, LocalDate.now().minusDays(1).toString());
        // 批次 B：正常（2028），入库 20 —— 应从此扣
        inbound(drugNo, "BATCH_OK", 20, "2028-01-01");

        // 出库 15：应跳过过期的 BATCH_EXPIRED，全部从 BATCH_OK 扣
        String outBody = """
                {"quantity":15,"purpose":"DISPENSE"}""";
        mvc.perform(post("/api/v1/drugs/" + drugNo + "/stock/outbound")
                        .contentType("application/json").content(outBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStock").value(105)); // 120 - 15

        // OUTBOUND 流水的 batchNo 应是 BATCH_OK（过期批次被跳过）
        mvc.perform(get("/api/v1/drugs/" + drugNo + "/stock/flows"))
                .andExpect(jsonPath("$.data.items[0].type").value("OUTBOUND"))
                .andExpect(jsonPath("$.data.items[0].batchNo").value("BATCH_OK"));
    }

    @Test
    void alerts_lowStockReturned() throws Exception {
        // threshold=100，入库 5 → current(5) < threshold(100) 触发 LOW_STOCK
        String drugNo = createDrug("感冒灵", 100);
        inbound(drugNo, "BATCH001", 5, "2028-01-01");

        // 注意：H2 内存库 DB_CLOSE_DELAY=-1，跨测试数据累积，其他测试创建的低库存药品也会出现。
        // 故不固定 total，用 drugName=感冒灵 过滤定位本测试创建的药品。
        mvc.perform(get("/api/v1/drugs/stock/alerts").param("type", "LOW_STOCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[?(@.drugName=='感冒灵')].drugId").value(org.hamcrest.Matchers.hasItem(drugNo)))
                .andExpect(jsonPath("$.data.items[?(@.drugName=='感冒灵')].alertType").value(org.hamcrest.Matchers.hasItem("LOW_STOCK")))
                .andExpect(jsonPath("$.data.items[?(@.drugName=='感冒灵')].currentStock").value(org.hamcrest.Matchers.hasItem(5)))
                .andExpect(jsonPath("$.data.items[?(@.drugName=='感冒灵')].threshold").value(org.hamcrest.Matchers.hasItem(100)));
    }

    @Test
    void internalRiskInfoAndFfeoBatches() throws Exception {
        String drugNo = createDrug("氯氮平", 10);
        // 批次号用 UUID 后缀，避免与其他测试的 uk_batch_no 冲突（测试隔离）
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String nearBatch = "BATCH_NEAR_" + suffix;
        String farBatch = "BATCH_FAR_" + suffix;
        inbound(drugNo, nearBatch, 30, LocalDate.now().plusDays(10).toString());
        inbound(drugNo, farBatch, 70, "2028-01-01");

        // 解析 drugNo → drugId：查药品列表拿主键不便，改用内部接口先取 current-stock 也需 drugId。
        // 这里直接查药品列表拿到 drugNo 后，用 JdbcTemplate 反查主键 id（drug_no 是业务编号）。
        Long drugId = queryDrugIdByNo(drugNo);

        // getRiskInfo
        mvc.perform(get("/internal/drugs/" + drugId + "/risk-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.drugId").value(drugId))
                .andExpect(jsonPath("$.data.genericName").value("氯氮平"))
                .andExpect(jsonPath("$.data.contraindications").isArray());

        // ffeoBatches：返回 2 个批次（data 是数组，非分页对象），按 expire_date ASC（近效期在前）
        mvc.perform(get("/internal/drugs/batch/ffeo").param("drugId", String.valueOf(drugId)).param("quantity", "40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.size()").value(2))
                .andExpect(jsonPath("$.data[0].batchNo").value(nearBatch))
                .andExpect(jsonPath("$.data[0].quantity").value(30))
                .andExpect(jsonPath("$.data[1].batchNo").value(farBatch))
                .andExpect(jsonPath("$.data[1].quantity").value(70));

        // getCurrentStock
        mvc.perform(get("/internal/drugs/" + drugId + "/current-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(100));
    }

    // ===== 测试助手 =====

    /** 创建药品，返回 drugNo */
    private String createDrug(String genericName, int threshold) throws Exception {
        String body = """
                {"genericName":"%s","unit":"盒","minStockThreshold":%d,
                 "contraindications":["禁忌A"],"interactions":["相互作用B"]}""".formatted(genericName, threshold);
        MvcResult r = mvc.perform(post("/api/v1/drugs").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/drugId").asText();
    }

    /** 入库，返回 currentStock */
    private int inbound(String drugNo, String batchNo, int quantity, String expireDate) throws Exception {
        String body = """
                {"batchNo":"%s","quantity":%d,"unitPrice":10.00,"expireDate":"%s","supplier":"测试供应商"}""".formatted(
                batchNo, quantity, expireDate);
        MvcResult r = mvc.perform(post("/api/v1/drugs/" + drugNo + "/stock/inbound")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).at("/data/currentStock").asInt();
    }

    @Autowired
    javax.sql.DataSource dataSource;

    /** 通过 drug_no 反查 BIGINT 主键（内部接口需要主键） */
    private Long queryDrugIdByNo(String drugNo) {
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        return jdbc.queryForObject("SELECT id FROM drug WHERE drug_no = ?", Long.class, drugNo);
    }
}
