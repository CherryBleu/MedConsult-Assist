package com.medconsult.ai.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.ai.persistence.entity.NegativeRuleEntity;

/** negative_rule Mapper。本轮 assess 未接入，留作未来否定词剔除增量。 */
public interface NegativeRuleMapper extends BaseMapper<NegativeRuleEntity> {
}
