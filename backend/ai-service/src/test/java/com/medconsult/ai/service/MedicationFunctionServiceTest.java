package com.medconsult.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MedicationFunctionService#containsAny(String, String...)} 词边界匹配的单元测试。
 *
 * <p>纯 JUnit5，不启动 Spring 容器。核心是医疗安全回归保护：
 * 拼接子串（如 "ibuprofenspecial"）必须被词边界阻断，避免 AI 给出错误的用药相互作用警告。
 */
class MedicationFunctionServiceTest {

    @Test
    void concatenatedSubstringShouldNotMatch() {
        // 本次修复的核心 case：药名被直接拼进另一个单词，前后都是单词字符，无词边界
        assertFalse(MedicationFunctionService.containsAny("ibuprofenspecial", "ibuprofen"));
        assertFalse(MedicationFunctionService.containsAny("aspirinxyz", "aspirin"));
    }

    @Test
    void shouldMatchCaseInsensitively() {
        // term 小写、text 含大写：CASE_INSENSITIVE 保证匹配（旧代码 text 未 lower 会漏配）
        assertTrue(MedicationFunctionService.containsAny("Ibuprofen 200mg", "ibuprofen"));
        assertTrue(MedicationFunctionService.containsAny("ASPIRIN tablet", "aspirin"));
    }

    @Test
    void hyphenIsWordBoundarySoExtendedReleaseStillMatches() {
        // 连字符是词边界：缓释片 (ibuprofen-ER) 与原药同效，应当匹配
        assertTrue(MedicationFunctionService.containsAny("ibuprofen-er", "ibuprofen"));
    }

    @Test
    void uppercaseInTextShouldStillMatch() {
        // 回归保护：text 首字母大写，term 全小写
        assertTrue(MedicationFunctionService.containsAny("aspirin", "Aspirin"));
        assertTrue(MedicationFunctionService.containsAny("Take Ibuprofen daily", "ibuprofen"));
    }

    @Test
    void chineseShouldUseContainsSemantics() {
        // 中文无空格分词，走 contains：肠溶片剂型匹配通用名"阿司匹林"
        assertTrue(MedicationFunctionService.containsAny("阿司匹林肠溶片", "阿司匹林"));
        assertTrue(MedicationFunctionService.containsAny("布洛芬缓释胶囊", "布洛芬"));
    }

    @Test
    void nullTextShouldNotThrow() {
        assertFalse(MedicationFunctionService.containsAny(null, "ibuprofen"));
        assertFalse(MedicationFunctionService.containsAny(null, (String) null));
    }

    @Test
    void nullOrEmptyTermsShouldBeSkipped() {
        assertFalse(MedicationFunctionService.containsAny("ibuprofen", (String) null));
        assertFalse(MedicationFunctionService.containsAny("ibuprofen", ""));
        // 混入 null/空 term 不应影响后续有效 term 的匹配
        assertTrue(MedicationFunctionService.containsAny("ibuprofen 200mg", null, "", "ibuprofen"));
    }

    @Test
    void multipleTermsAnyMatchShouldReturnTrue() {
        assertTrue(MedicationFunctionService.containsAny("amoxicillin capsule", "ibuprofen", "aspirin", "amoxicillin"));
    }

    @Test
    void noMatchShouldReturnFalse() {
        assertFalse(MedicationFunctionService.containsAny("paracetamol 500mg", "ibuprofen", "aspirin"));
    }

    @Test
    void containsLatinHelper() {
        assertTrue(MedicationFunctionService.containsLatin("ibuprofen"));
        assertTrue(MedicationFunctionService.containsLatin("阿司匹林 aspirin"));
        assertFalse(MedicationFunctionService.containsLatin("阿司匹林"));
        assertFalse(MedicationFunctionService.containsLatin("布洛芬"));
        assertFalse(MedicationFunctionService.containsLatin(""));
    }
}
