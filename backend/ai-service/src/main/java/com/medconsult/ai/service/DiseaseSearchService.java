package com.medconsult.ai.service;

import com.medconsult.ai.client.MilvusRestClient;
import com.medconsult.ai.client.MongoDiseaseRepository;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseCandidate;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;
import com.medconsult.ai.knowledge.EncodingComplaintGuard;
import com.medconsult.ai.knowledge.QueryExpander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class DiseaseSearchService {
    private static final Logger log = LoggerFactory.getLogger(DiseaseSearchService.class);
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 10;
    private static final long DEFAULT_RETRIEVAL_TIMEOUT_MILLIS = 15_000L;

    private final OpenAiCompatibleClient llmClient;
    private final MongoDiseaseRepository mongoDiseaseRepository;
    private final MilvusRestClient milvusRestClient;
    private final DiseaseCacheService cacheService;
    private final long retrievalTimeoutMillis;

    public DiseaseSearchService(
            OpenAiCompatibleClient llmClient,
            MongoDiseaseRepository mongoDiseaseRepository,
            MilvusRestClient milvusRestClient,
            DiseaseCacheService cacheService
    ) {
        this(llmClient, mongoDiseaseRepository, milvusRestClient, cacheService, DEFAULT_RETRIEVAL_TIMEOUT_MILLIS);
    }

    DiseaseSearchService(
            OpenAiCompatibleClient llmClient,
            MongoDiseaseRepository mongoDiseaseRepository,
            MilvusRestClient milvusRestClient,
            DiseaseCacheService cacheService,
            long retrievalTimeoutMillis
    ) {
        this.llmClient = llmClient;
        this.mongoDiseaseRepository = mongoDiseaseRepository;
        this.milvusRestClient = milvusRestClient;
        this.cacheService = cacheService;
        this.retrievalTimeoutMillis = Math.max(1L, retrievalTimeoutMillis);
    }

    public DiseaseIntent extractIntent(String userText) {
        // 症状自诊不调用生成式 LLM（docs/修改建议.md §3.1 P0 铁律）：
        // 意图抽取仅用本地规则（症状口语词映射 + 显式疾病名识别 + 附加字段关键词命中），
        // 不再调用 llmClient.chatJson（生成式）。Embedding 检索仍允许（仅向量化，非生成式）。
        long started = System.currentTimeMillis();
        DiseaseIntent intent = fallbackIntent(userText);
        DiseaseIntent fixedIntent = EncodingComplaintGuard.fixIfNeeded(userText, intent);
        log.info("[AI-TIMER] disease.extractIntent={}ms mode=rule candidates={} requestedFields={}",
                elapsed(started), fixedIntent.candidates().size(), fixedIntent.metadataQuery().requestedFields().size());
        return fixedIntent;
    }

    public List<DiseaseKnowledge> search(String userText, DiseaseIntent intent, int topK) {
        long started = System.currentTimeMillis();
        long deadlineNanos = deadlineFromNow(retrievalTimeoutMillis);
        int limit = normalizeTopK(topK);
        List<DiseaseCandidate> candidates = intent.candidates().stream()
                .filter(item -> !item.isPlaceholderDiseaseName())
                .limit(limit)
                .toList();
        if (candidates.isEmpty()) {
            // 纯症状输入兜底：用户未明确提及疾病名时（如只输入"咳嗽""胸闷"），
            // 所有候选都是"待鉴别"占位符被过滤掉。此时不应直接返回空列表短路，
            // 否则 Milvus 语义检索路径永远走不到。改为用症状关键词 + 原始文本做检索兜底。
            List<DiseaseKnowledge> fallback = searchBySymptomText(userText, intent, limit, deadlineNanos);
            log.info("[AI-TIMER] disease.search={}ms candidates=0 fallback=symptom results={}",
                    elapsed(started), fallback.size());
            return fallback;
        }

        List<DiseaseKnowledge> results = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Callable<DiseaseKnowledge>> tasks = candidates.stream()
                    .<Callable<DiseaseKnowledge>>map(candidate ->
                            () -> standardizeCandidate(userText, intent.metadataQuery(), candidate))
                    .toList();
            long remainingMillis = remainingMillis(deadlineNanos);
            if (remainingMillis <= 0) {
                log.warn("disease candidate search skipped because retrieval budget is exhausted");
                return List.of();
            }
            for (Future<DiseaseKnowledge> future : executor.invokeAll(tasks, remainingMillis, TimeUnit.MILLISECONDS)) {
                try {
                    if (future.isCancelled()) {
                        log.warn("disease candidate search timed out and was skipped");
                        continue;
                    }
                    DiseaseKnowledge knowledge = future.get();
                    if (knowledge != null) {
                        results.add(knowledge);
                    }
                } catch (CancellationException ex) {
                    log.warn("disease candidate search timed out and was cancelled");
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
        } finally {
            executor.shutdownNow();
        }
        List<DiseaseKnowledge> deduped = aggregateEvidence(results).stream().limit(limit).toList();
        log.info("[AI-TIMER] disease.search={}ms candidates={} rawResults={} results={} sources={}",
                elapsed(started), candidates.size(), results.size(), deduped.size(), summarizeSources(deduped));
        return deduped;
    }

    /**
     * 纯症状输入的检索兜底（docs/修改建议.md §3.1 P0 铁律：不调用生成式 LLM）。
     *
     * <p>当用户只输入症状（如"咳嗽""胸闷三天"）而未明确提及疾病名时，意图抽取阶段
     * 只能产出"待鉴别"占位候选，被 {@link #search} 的占位过滤器剔除后候选为空。
     * 此方法接管这种场景，按优先级尝试两条降级路径：
     * <ol>
     *   <li>Mongo 按症状关键词查询（{@link MongoDiseaseRepository#findBySymptom}）：
     *       用 intent 中抽取的症状在 diseases 集合的 symptom 数组字段做 $in 匹配。
     *       只要疾病数据已导入 Mongo，即使 Embedding/Milvus 不可用也能召回。</li>
     *   <li>Milvus 语义检索（本地 Embedding 向量化 + Milvus topK 召回）：
     *       用 {@link QueryExpander#expand} 扩展查询文本后 embedding，再向量检索。
     *       依赖本地 Embedding 容器（localhost:7997）和 Milvus（localhost:19530）。</li>
     * </ol>
     * Mongo 命中数量不足 topK 时继续用 Milvus 语义检索补齐，再按疾病名去重；
     * 两条路径都未命中时返回空列表（由上层 generateFinalAnswer 给出兜底提示）。
     */
    private List<DiseaseKnowledge> searchBySymptomText(String userText, DiseaseIntent intent, int topK, long deadlineNanos) {
        int limit = normalizeTopK(topK);

        List<DiseaseKnowledge> results = new ArrayList<>();

        // 路径1：Mongo 按症状关键词查询（不依赖 Embedding/Milvus，纯规则）
        List<String> symptoms = intent.candidates().stream()
                .flatMap(c -> c.symptoms().stream())
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
        if (!symptoms.isEmpty()) {
            results.addAll(callWithinBudget("mongodb_symptom",
                    () -> mongoDiseaseRepository.findBySymptom(symptoms, limit), deadlineNanos)
                    .orElse(List.of()));
            List<DiseaseKnowledge> mongoMatches = aggregateEvidence(results);
            if (mongoMatches.size() >= limit) {
                List<DiseaseKnowledge> matches = mongoMatches.stream().limit(limit).toList();
                log.info("[AI-TIMER] disease.searchBySymptomText source=mongodb_symptom results={}",
                        matches.size());
                return matches;
            }
            results = new ArrayList<>(mongoMatches);
        }

        // 路径2：Milvus 语义检索（本地 Embedding 向量化，非生成式 LLM）。
        // 当 Mongo 症状召回不足 topK 时补齐语义候选，提高纯症状输入的召回质量。
        int remaining = limit - aggregateEvidence(results).size();
        if (remaining <= 0) {
            return results.stream().limit(limit).toList();
        }
        int semanticLimit = Math.min(limit, Math.max(remaining, remaining * 2));
        DiseaseCandidate fallbackCandidate = new DiseaseCandidate(
                "", symptoms.isEmpty() ? List.of(userText) : symptoms,
                "纯症状输入，基于原始文本做语义检索兜底。");
        String queryText = QueryExpander.expand(userText, fallbackCandidate);
        results.addAll(callWithinBudget("milvus_semantic",
                () -> semanticSearch(queryText, semanticLimit), deadlineNanos).orElse(List.of()));

        List<DiseaseKnowledge> matches = aggregateEvidence(results).stream().limit(limit).toList();
        if (!matches.isEmpty()) {
            log.info("[AI-TIMER] disease.searchBySymptomText source=merged_symptom_semantic results={}",
                    matches.size());
            return matches;
        }

        log.info("[AI-TIMER] disease.searchBySymptomText source=miss results=0");
        return List.of();
    }

    private List<DiseaseKnowledge> semanticSearch(String queryText, int semanticLimit) {
        return llmClient.embedOne(queryText)
                .map(vector -> milvusRestClient.search(vector, semanticLimit))
                .orElse(List.of());
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

    private <T> Optional<T> callWithinBudget(String name, Callable<T> task, long deadlineNanos) {
        long timeoutMillis = remainingMillis(deadlineNanos);
        if (timeoutMillis <= 0) {
            log.warn("disease retrieval step skipped because budget is exhausted, step={}", name);
            return Optional.empty();
        }
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<T> future = executor.submit(task);
        try {
            return Optional.ofNullable(future.get(timeoutMillis, TimeUnit.MILLISECONDS));
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.warn("disease retrieval step timed out and was skipped, step={} budgetMs={}", name, timeoutMillis);
            return Optional.empty();
        } catch (ExecutionException ex) {
            log.warn("disease retrieval step failed and was skipped, step={} error={}", name,
                    ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<DiseaseKnowledge> aggregateEvidence(List<DiseaseKnowledge> results) {
        LinkedHashMap<String, DiseaseKnowledge> aggregated = new LinkedHashMap<>();
        for (DiseaseKnowledge result : results) {
            if (result == null) {
                continue;
            }
            String key = aggregateKey(result);
            aggregated.merge(key, result, DiseaseSearchService::mergeEvidence);
        }
        return new ArrayList<>(aggregated.values());
    }

    private static DiseaseKnowledge mergeEvidence(DiseaseKnowledge left, DiseaseKnowledge right) {
        Map<String, Object> metadata = new LinkedHashMap<>(left.metadata() == null ? Map.of() : left.metadata());
        if (right.metadata() != null) {
            right.metadata().forEach(metadata::putIfAbsent);
        }
        return new DiseaseKnowledge(
                firstNonBlank(left.vectorId(), right.vectorId()),
                firstNonBlank(left.sourceId(), right.sourceId()),
                firstNonBlank(left.diseaseName(), right.diseaseName()),
                firstNonBlank(left.desc(), right.desc()),
                mergeList(left.symptoms(), right.symptoms()),
                metadata,
                mergeFieldNames(left.fieldName(), right.fieldName()),
                mergeChunkText(left.chunkText(), right.chunkText()),
                Math.max(left.score(), right.score()),
                left.source() == null ? right.source() : left.source()
        );
    }

    private static String aggregateKey(DiseaseKnowledge result) {
        return firstNonBlank(result.diseaseName(), result.sourceId(), result.vectorId());
    }

    private static List<String> mergeList(List<String> left, List<String> right) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (left != null) {
            left.stream().filter(item -> item != null && !item.isBlank()).forEach(merged::add);
        }
        if (right != null) {
            right.stream().filter(item -> item != null && !item.isBlank()).forEach(merged::add);
        }
        return new ArrayList<>(merged);
    }

    private static String mergeFieldNames(String left, String right) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        splitFieldNames(left).forEach(fields::add);
        splitFieldNames(right).forEach(fields::add);
        return String.join(",", fields);
    }

    private static List<String> splitFieldNames(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("[,，;；]")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static String mergeChunkText(String left, String right) {
        LinkedHashSet<String> chunks = new LinkedHashSet<>();
        if (left != null && !left.isBlank()) {
            chunks.add(left);
        }
        if (right != null && !right.isBlank()) {
            chunks.add(right);
        }
        return String.join("\n---\n", chunks);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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

    private static long deadlineFromNow(long timeoutMillis) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1L, timeoutMillis));
    }

    private static long remainingMillis(long deadlineNanos) {
        return TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
    }

    private static int normalizeTopK(int topK) {
        if (topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(MAX_TOP_K, topK);
    }
}
