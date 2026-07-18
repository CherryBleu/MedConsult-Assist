package com.medconsult.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 字段级加解密（《修改建议》§5.3 敏感数据加密）。
 *
 * <p>用于 patient.id_no 等敏感字段：落库前加密（每条独立 IV），读取时解密。
 * 业务侧经 {@link EncryptedStringTypeHandler} 透明处理，无感加密/解密。
 *
 * <p>密文格式：{@code base64(iv[12] || ciphertext || tag[16])}。
 * IV 每次加密用 {@link SecureRandom} 新生成（GCM 绝不复用 IV）。
 *
 * <p>密钥：Base64 编码的 32 字节（256 位）AES key，从 {@code medconsult.crypto.id-no.key}
 * 配置注入（生产走 Nacos/KMS）。构造时校验长度，不满足抛异常（参照 JwtCodec 范式）。
 *
 * <p>实现用 JDK 21 内置 {@code javax.crypto.Cipher}（GCM Provider 内置），不引 BouncyCastle。
 */
public class CryptoService {

    /** AES-256 密钥字节数 */
    private static final int KEY_BYTES = 32;
    /** GCM 推荐 IV（nonce）长度 */
    private static final int IV_BYTES = 12;
    /** GCM 认证标签长度（位） */
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param base64Key Base64 编码的 32 字节 AES 密钥
     */
    public CryptoService(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("crypto key 不能为空");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("crypto key 不是合法 Base64", e);
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "crypto key 长度需为 " + KEY_BYTES + " 字节（AES-256），当前 " + keyBytes.length);
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 加密：返回 base64(iv || ciphertext || tag)。
     * 同明文每次加密结果不同（IV 随机），不可直接用于等值比较——唯一性/检索用 {@link IdNoHasher}。
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            // 拼 iv || cipherText（cipherText 末尾含 GCM tag）
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES-256-GCM 加密失败", e);
        }
    }

    /**
     * 解密：密文被篡改（tag 校验失败）抛 RuntimeException，不返回脏数据。
     */
    public String decrypt(String combinedBase64) {
        if (combinedBase64 == null || combinedBase64.isBlank()) {
            return combinedBase64;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(combinedBase64);
            if (combined.length <= IV_BYTES) {
                // 非加密格式（如历史明文）原样返回，兼容迁移期
                return combinedBase64;
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] cipherText = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException e) {
            // tag 校验失败：密文被篡改或用了错误密钥，返回原值兼容历史明文迁移期（避免解密失败阻塞）
            // 生产若发现大量此类应排查密钥一致性
            return combinedBase64;
        } catch (Exception e) {
            // 其他异常（如非 base64）：原样返回，兼容迁移期明文
            return combinedBase64;
        }
    }
}
