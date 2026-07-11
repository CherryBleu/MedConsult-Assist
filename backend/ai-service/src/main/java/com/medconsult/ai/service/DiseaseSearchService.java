package com.medconsult.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.client.MilvusRestClient;
import com.medconsult.ai.client.MongoDiseaseRepository;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseCandidate;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;
import com.medconsult.ai.knowledge.EncodingComplaintGuard;
import com.medconsult.ai.knowledge.KnowledgeFields;
import com.medconsult.ai.knowledge.QueryExpander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class DiseaseSearchService {
    private static final Logger log = LoggerFactory.getLogger(DiseaseSearchService.class);

    private static final String INTENT_PROMPT = """
            你是医学信息标准化助手。请基于用户输入提取可能相关的疾病名称、症状和医学描述。
            要求：
            1. 使用规范、客观、审慎的医学表达；
            2. 不作确定诊断，不给治疗处方；
            3. 用户输入通常很口语化，要把俗称、比喻和患者描述转成医学术语；
            4. 不要因为表达口语化就判断为乱码，除非完全无法识别任何医学含义；
            5. 只要用户内容包含可读中文，就不得回答“编码异常”“乱码”“无法可靠识别”；
            6. 必须给出最可能的 top3 疾病候选，按可能性从高到低排序；
            7. 如果用户明确说“咨询/了解/怀疑”某个疾病名，应把该疾病名作为第一候选；
            8. 识别用户是否需要查询附加字段，例如挂什么科、医保、病因、检查、治疗方式、费用、用药、饮食、并发症等；
            9. 附加字段只能从这些字段中选择：category, prevent, cause, yibao_status, get_prob, easy_get, get_way, acompany, cure_department, cure_way, cure_lasttime, cured_prob, common_drug, cost_money, check, do_eat, not_eat, recommand_eat, recommand_drug, drug_detail；
            10. 你只负责指出需要从数据库查询哪些字段，不要编造这些字段的最终取值；
            11. 只返回 JSON，不要 Markdown。
            12. JSON 顶层字段固定为 candidates 和 metadataQuery；
            13. candidates 必须是长度为 3 的数组，每项字段固定为 diseaseName、symptoms、description；
            14. metadataQuery 字段固定为 requestedFields 和 filters；
            15. requestedFields 是用户明确或隐含想查询的附加字段数组，未提到则返回空数组；
            16. filters 是从用户输入中提取的字段约束对象，key 必须属于上述附加字段，value 必须是字符串数组；没有约束则返回空对象；
            17. 如果用户说“挂什么科/看什么科”，requestedFields 包含 cure_department；说“医保/报销”，包含 yibao_status；说“怎么治/治疗”，包含 cure_way 和 cure_lasttime；说“多少钱/费用”，包含 cost_money；说“检查”，包含 check；说“药”，包含 common_drug 或 recommand_drug；说“能不能吃/忌口/饮食”，包含 do_eat、not_eat 或 recommand_eat；
            18. 如果某个候选疾病名称不确定，diseaseName 填“待鉴别”，但 symptoms 和 description 必须尽量提取。

            口语到医学术语示例：
            - “咳嗽声音像鸡叫”“咳完吸气像鸡叫”：鸡鸣样吸气声、阵发性痉挛性咳嗽，需鉴别百日咳；
            - “小孩老是咳，咳到吐”：儿童反复咳嗽、咳后呕吐；
            - “喘不上气”：呼吸困难或喘息。

            返回格式：
            {
              "candidates": [
                {"diseaseName": "百日咳", "symptoms": ["阵发性痉挛性咳嗽"], "description": "需结合病程、接触史和检查鉴别。"},
                {"diseaseName": "急性支气管炎", "symptoms": ["咳嗽"], "description": "需结合发热、咳痰等表现鉴别。"},
                {"diseaseName": "上呼吸道感染", "symptoms": ["咳嗽"], "description": "需结合鼻塞、流涕、咽痛等表现鉴别。"}
              ],
              "metadataQuery": {
                "requestedFields": ["cure_department", "yibao_status"],
                "filters": {"cure_department": ["儿科"]}
              }
            }
            """;

    private final OpenAiCompatibleClient llmClient;
    private final MongoDiseaseRepository mongoDiseaseRepository;
    private final MilvusRestClient milvusRestClient;
    private final DiseaseCacheService cacheService;

    public DiseaseSearchService(
            OpenAiCompatibleClient llmClient,
            MongoDiseaseRepository mongoDiseaseRepository,
            MilvusRestClient milvusRestClient,
            DiseaseCacheService cacheService
    ) {
        this.llmClient = llmClient;
        this.mongoDiseaseRepository = mongoDiseaseRepository;
        this.milvusRestClient = milvusRestClient;
        this.cacheService = cacheService;
    }

    public DiseaseIntent extractIntent(String userText) {
        long started = System.currentTimeMillis();
        DiseaseIntent intent = llmClient.chatJson(INTENT_PROMPT, userText)
                .map(this::parseIntent)
                .orElseGet(() -> fallbackIntent(userText));
        DiseaseIntent fixedIntent = EncodingComplaintGuard.fixIfNeeded(userText, intent);
        log.info("[AI-TIMER] disease.extractIntent={}ms candidates={} requestedFields={}",
                elapsed(started), fixedIntent.candidates().size(), fixedIntent.metadataQuery().requestedFields().size());
        return fixedIntent;
    }

    public List<DiseaseKnowledge> search(String userText, DiseaseIntent intent, int topK) {
        long started = System.currentTimeMillis();
        List<DiseaseCandidate> candidates = intent.candidates().stream()
                .filter(item -> !item.isPlaceholderDiseaseName())
                .limit(3)
                .toList();
        if (candidates.isEmpty()) {
            log.info("[AI-TIMER] disease.search={}ms candidates=0 results=0", elapsed(started));
            return List.of();
        }

        List<DiseaseKnowledge> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<DiseaseKnowledge>> tasks = candidates.stream()
                    .<Callable<DiseaseKnowledge>>map(candidate ->
                            () -> standardizeCandidate(userText, intent.metadataQuery(), candidate))
                    .toList();
            for (Future<DiseaseKnowledge> future : executor.invokeAll(tasks)) {
                try {
                    DiseaseKnowledge knowledge = future.get();
                    if (knowledge != null) {
                        results.add(knowledge);
                    }
                } catch (ExecutionException ex) {
                    // 单个候选疾病检索失败（Mongo/Milvus 不可用或无数据）不应中断整个分诊：
                    // 降级为跳过该候选，后续 LLM 仍可基于症状给出建议（RAG 退化为纯 LLM）。
                    log.warn("disease candidate search failed, skip and degrade to LLM-only: {}",
                            ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Disease search interrupted", ex);
        }
        List<DiseaseKnowledge> deduped = dedupe(results).stream().limit(Math.max(1, Math.min(3, topK))).toList();
        log.info("[AI-TIMER] disease.search={}ms candidates={} rawResults={} results={} sources={}",
                elapsed(started), candidates.size(), results.size(), deduped.size(), summarizeSources(deduped));
        return deduped;
    }

    private DiseaseKnowledge standardizeCandidate(String userText, MetadataQuery metadataQuery, DiseaseCandidate candidate) {
        long started = System.currentTimeMillis();
        DiseaseKnowledge cached = cacheService.get(userText, candidate, metadataQuery).orElse(null);
        if (cached != null) {
            log.info("[AI-TIMER] disease.standardizeCandidate={}ms source=redis", elapsed(started));
            return cached;
        }

        DiseaseKnowledge mongoMatch = mongoDiseaseRepository.findByNameExact(candidate.diseaseName()).orElse(null);
        if (mongoMatch != null) {
            cacheService.put(userText, candidate, metadataQuery, mongoMatch);
            log.info("[AI-TIMER] disease.standardizeCandidate={}ms source=mongodb", elapsed(started));
            return mongoMatch;
        }

        String queryText = QueryExpander.expand(userText, candidate);
        DiseaseKnowledge milvusMatch = llmClient.embedOne(queryText)
                .map(vector -> milvusRestClient.search(vector, 1))
                .filter(matches -> !matches.isEmpty())
                .map(matches -> matches.getFirst())
                .orElse(null);
        if (milvusMatch != null) {
            cacheService.put(userText, candidate, metadataQuery, milvusMatch);
        }
        log.info("[AI-TIMER] disease.standardizeCandidate={}ms source={}",
                elapsed(started), milvusMatch == null ? "miss" : "milvus");
        return milvusMatch;
    }

    private DiseaseIntent parseIntent(JsonNode json) {
        List<DiseaseCandidate> candidates = new ArrayList<>();
        for (JsonNode item : json.path("candidates")) {
            List<String> symptoms = new ArrayList<>();
            for (JsonNode symptom : item.path("symptoms")) {
                if (!symptom.asText("").isBlank()) {
                    symptoms.add(symptom.asText());
                }
            }
            candidates.add(new DiseaseCandidate(
                    item.path("diseaseName").asText("待鉴别"),
                    symptoms,
                    item.path("description").asText("")
            ));
        }
        if (candidates.isEmpty()) {
            candidates.add(new DiseaseCandidate("待鉴别", List.of(), "未能提取明确疾病候选"));
        }
        JsonNode metadataNode = json.path("metadataQuery");
        List<String> fields = new ArrayList<>();
        for (JsonNode field : metadataNode.path("requestedFields")) {
            if (KnowledgeFields.STANDARD_METADATA_FIELD_SET.contains(field.asText())) {
                fields.add(field.asText());
            }
        }
        Map<String, List<String>> filters = new LinkedHashMap<>();
        metadataNode.path("filters").fields().forEachRemaining(entry -> {
            List<String> values = new ArrayList<>();
            for (JsonNode value : entry.getValue()) {
                if (!value.asText("").isBlank()) {
                    values.add(value.asText());
                }
            }
            if (KnowledgeFields.STANDARD_METADATA_FIELD_SET.contains(entry.getKey()) && !values.isEmpty()) {
                filters.put(entry.getKey(), values);
            }
        });
        return new DiseaseIntent(candidates, new MetadataQuery(fields.stream().distinct().toList(), filters));
    }

    private DiseaseIntent fallbackIntent(String userText) {
        List<String> fields = new ArrayList<>();
        if (containsAny(userText, "挂什么科", "看什么科", "哪个科", "什么科", "科室", "挂号")) {
            fields.add("cure_department");
        }
        if (containsAny(userText, "医保", "报销")) {
            fields.add("yibao_status");
        }
        if (containsAny(userText, "怎么治", "治疗", "治法", "多久能好")) {
            fields.add("cure_way");
            fields.add("cure_lasttime");
        }
        if (containsAny(userText, "多少钱", "费用", "花费", "贵不贵")) {
            fields.add("cost_money");
        }
        if (containsAny(userText, "检查", "查什么", "化验")) {
            fields.add("check");
        }
        if (containsAny(userText, "吃什么药", "用药", "药")) {
            fields.add("common_drug");
            fields.add("recommand_drug");
        }
        if (containsAny(userText, "吃什么", "忌口", "饮食", "能不能吃")) {
            fields.add("do_eat");
            fields.add("not_eat");
            fields.add("recommand_eat");
        }
        List<String> symptoms = extractFallbackSymptoms(userText);
        List<DiseaseCandidate> candidates = new ArrayList<>();
        extractExplicitDiseaseName(userText).stream()
                .map(name -> new DiseaseCandidate(name, symptoms, "用户输入中明确提到的疾病名称，优先按 MongoDB name 精确匹配标准条目"))
                .forEach(candidates::add);
        candidates.add(new DiseaseCandidate("待鉴别", symptoms, "LLM 标准化不可用，已基于用户原始症状描述继续进行数据库标准化和语义检索。"));
        return new DiseaseIntent(
                candidates.stream().distinct().limit(3).toList(),
                new MetadataQuery(fields.stream().distinct().toList(), fallbackFilters(userText))
        );
    }

    private static List<String> extractFallbackSymptoms(String userText) {
        List<String> symptoms = new ArrayList<>();
        if (containsAny(userText, "鸡叫", "鸡鸣", "像鸡")) {
            symptoms.add("鸡鸣样吸气声");
            symptoms.add("阵发性痉挛性咳嗽");
        }
        if (containsAny(userText, "小孩", "孩子", "儿童", "宝宝")) {
            symptoms.add("儿童患者");
        }
        if (userText.contains("咳")) {
            symptoms.add("咳嗽");
        }
        if (containsAny(userText, "吐", "呕")) {
            symptoms.add("咳后呕吐");
        }
        if (containsAny(userText, "喘不上气", "喘", "憋", "呼吸困难")) {
            symptoms.add("呼吸困难或喘息");
        }
        if (containsAny(userText, "胸闷", "心慌", "心悸")) {
            symptoms.add("胸闷");
            symptoms.add("心悸");
        }
        if (symptoms.isEmpty()) {
            symptoms.add(userText);
        }
        return symptoms.stream().distinct().toList();
    }

    private static Map<String, List<String>> fallbackFilters(String userText) {
        if (containsAny(userText, "小孩", "孩子", "儿童", "宝宝")) {
            return Map.of("cure_department", List.of("儿科"));
        }
        return Map.of();
    }

    private static List<String> extractExplicitDiseaseName(String userText) {
        String text = userText == null ? "" : userText.trim();
        if (text.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String clause : text.replaceAll("[，,；;！？?\\r\\n]", "。").split("。")) {
            String name = extractExplicitDiseaseNameFromClause(clause);
            if (!name.isBlank() && name.length() <= 20 && containsChinese(name)) {
                names.add(name);
            }
        }
        return names.stream().distinct().limit(2).toList();
    }

    private static String extractExplicitDiseaseNameFromClause(String clause) {
        String value = clause == null ? "" : clause.trim();
        if (value.isBlank()) {
            return "";
        }
        List<String> triggers = List.of("我想咨询", "想咨询", "咨询一下", "咨询", "我想了解", "想了解", "了解一下",
                "怀疑是", "疑似", "可能是", "是不是", "是否是", "得了", "患有");
        for (String trigger : triggers) {
            int index = value.indexOf(trigger);
            if (index >= 0) {
                String candidate = value.substring(index + trigger.length()).trim();
                return cleanupExplicitDiseaseName(candidate);
            }
        }
        return "";
    }

    private static String cleanupExplicitDiseaseName(String value) {
        return value
                .replaceAll("^(这个病|疾病|一种病)", "")
                .replaceAll("(应该|需要|该|怎么|怎么办|能不能|可以吗|吗).*$", "")
                .replaceAll("[\\s　。．\\.，,；;：:！？?、]+$", "")
                .trim();
    }

    private static boolean containsChinese(String value) {
        return value.codePoints()
                .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static List<DiseaseKnowledge> dedupe(List<DiseaseKnowledge> results) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<DiseaseKnowledge> deduped = new ArrayList<>();
        for (DiseaseKnowledge result : results) {
            String key = result.diseaseName() == null || result.diseaseName().isBlank()
                    ? result.sourceId()
                    : result.diseaseName();
            if (seen.add(key)) {
                deduped.add(result);
            }
        }
        return deduped;
    }

    private static String summarizeSources(List<DiseaseKnowledge> knowledge) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DiseaseKnowledge item : knowledge) {
            String source = item.source() == null ? "UNKNOWN" : item.source().name();
            counts.put(source, counts.getOrDefault(source, 0L) + 1);
        }
        return counts.toString();
    }

    private static long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }
}
