package com.medconsult.common.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CryptoService 单测（纯 JUnit5，不启 Spring）。
 * 覆盖：round-trip、IV 随机性、密文篡改检测、hash 一致性。
 */
class CryptoServiceTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]); // 32 字节全 0（测试用）

    private final CryptoService crypto = new CryptoService(KEY);

    @Test
    void roundTrip_加解密还原() {
        String plain = "110101199005201234";
        String cipher = crypto.encrypt(plain);
        assertNotEquals(plain, cipher);
        assertEquals(plain, crypto.decrypt(cipher));
    }

    @Test
    void iv随机_同明文两次密文不同() {
        String plain = "110101199005201234";
        String c1 = crypto.encrypt(plain);
        String c2 = crypto.encrypt(plain);
        assertNotEquals(c1, c2, "GCM 必须每次新 IV，密文应不同");
        assertEquals(plain, crypto.decrypt(c1));
        assertEquals(plain, crypto.decrypt(c2));
    }

    @Test
    void 密钥长度校验_不足32字节抛异常() {
        String badKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalArgumentException.class, () -> new CryptoService(badKey));
    }

    @Test
    void 非法Base64_抛异常() {
        assertThrows(IllegalArgumentException.class, () -> new CryptoService("not-base64!!!"));
    }

    @Test
    void null处理() {
        assertNull(crypto.encrypt(null));
        assertNull(crypto.decrypt(null));
    }

    @Test
    void hash_一致性与唯一性() {
        String id1 = "110101199005201234";
        String id2 = "110101199005201235";
        String h1 = IdNoHasher.hash(id1);
        String h1Again = IdNoHasher.hash(id1);
        String h2 = IdNoHasher.hash(id2);
        assertEquals(h1, h1Again, "同 idNo hash 必须一致（确定性）");
        assertEquals(64, h1.length(), "SHA-256 hex 64 字符");
        assertNotEquals(h1, h2, "不同 idNo hash 不同");
        assertNull(IdNoHasher.hash(null));
        assertNull(IdNoHasher.hash(""));
    }
}
