package com.medconsult.ai.knowledge;

import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseCandidate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class FastCleanMatcher {
    private static final Pattern CLEAN_PATTERN = Pattern.compile("[^\\p{IsHan}A-Za-z0-9]+");

    private FastCleanMatcher() {
    }

    public static double weightedScore(String query, DiseaseCandidate candidate) {
        double nameScore = score(query, candidate.diseaseName());
        double symptomScore = score(query, candidate.symptomText());
        double descScore = score(query, candidate.description());
        double fullScore = score(query, candidate.searchText());
        return nameScore * 0.45 + symptomScore * 0.30 + descScore * 0.15 + fullScore * 0.10;
    }

    public static double score(String query, String candidate) {
        String cleanedQuery = clean(query);
        String cleanedCandidate = clean(candidate);
        if (cleanedQuery.isBlank() || cleanedCandidate.isBlank()) {
            return 0;
        }

        Map<String, Integer> queryTerms = terms(cleanedQuery);
        Map<String, Integer> candidateTerms = terms(cleanedCandidate);
        double cosine = cosine(queryTerms, candidateTerms);
        double jaccard = jaccard(queryTerms.keySet(), candidateTerms.keySet());
        double containment = containment(queryTerms.keySet(), candidateTerms.keySet());
        return cosine * 0.65 + jaccard * 0.20 + containment * 0.15;
    }

    private static String clean(String text) {
        String lower = Objects.toString(text, "").toLowerCase(Locale.ROOT);
        return CLEAN_PATTERN.matcher(lower).replaceAll("");
    }

    private static Map<String, Integer> terms(String text) {
        Map<String, Integer> counts = new HashMap<>();
        addTerm(counts, text, 3);
        addNgrams(counts, text, 2);
        addNgrams(counts, text, 3);
        return counts;
    }

    private static void addTerm(Map<String, Integer> counts, String term, int weight) {
        if (!term.isBlank()) {
            counts.merge(term, weight, Integer::sum);
        }
    }

    private static void addNgrams(Map<String, Integer> counts, String text, int n) {
        if (text.length() < n) {
            return;
        }
        for (int i = 0; i <= text.length() - n; i++) {
            counts.merge(text.substring(i, i + n), 1, Integer::sum);
        }
    }

    private static double cosine(Map<String, Integer> left, Map<String, Integer> right) {
        double dot = 0;
        for (Map.Entry<String, Integer> entry : left.entrySet()) {
            dot += entry.getValue() * right.getOrDefault(entry.getKey(), 0);
        }
        double leftNorm = norm(left);
        double rightNorm = norm(right);
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (leftNorm * rightNorm);
    }

    private static double norm(Map<String, Integer> terms) {
        return Math.sqrt(terms.values().stream()
                .mapToDouble(value -> value * value)
                .sum());
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private static double containment(Set<String> queryTerms, Set<String> candidateTerms) {
        if (queryTerms.isEmpty()) {
            return 0;
        }
        long matched = queryTerms.stream()
                .filter(candidateTerms::contains)
                .count();
        return (double) matched / queryTerms.size();
    }
}
