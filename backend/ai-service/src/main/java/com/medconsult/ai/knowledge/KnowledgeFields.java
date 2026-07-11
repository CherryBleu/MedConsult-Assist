package com.medconsult.ai.knowledge;

import java.util.List;
import java.util.Set;

public final class KnowledgeFields {
    private KnowledgeFields() {
    }

    public static final List<String> STANDARD_METADATA_FIELDS = List.of(
            "category", "prevent", "cause", "yibao_status", "get_prob", "easy_get", "get_way",
            "acompany", "cure_department", "cure_way", "cure_lasttime", "cured_prob",
            "common_drug", "cost_money", "check", "do_eat", "not_eat", "recommand_eat",
            "recommand_drug", "drug_detail"
    );

    public static final Set<String> STANDARD_METADATA_FIELD_SET = Set.copyOf(STANDARD_METADATA_FIELDS);

    public static final List<String> DEFAULT_METADATA_FIELDS = List.of(
            "cause", "cure_department", "check", "cure_way", "yibao_status"
    );
}
