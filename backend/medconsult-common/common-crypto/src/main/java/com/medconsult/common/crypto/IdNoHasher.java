package com.medconsult.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 证件号确定性指纹（《修改建议》§5.3 id_no 加密配套）。
 *
 * <p>用途：AES-GCM 密文每次 IV 不同，无法直接做唯一性 eq / 检索。{@code id_no_hash} 列存
 * SHA-256(id_no) 的全量 hex（64 字符），唯一键挂此列，业务侧用 {@code .eq("id_no_hash", hash(plain))}
 * 实现证件查重/精确检索。
 *
 * <p>身份证号本身是半公开、高熵字段，SHA-256（无 pepper）的唯一性足够。若需抗彩虹表，
 * 可后续加 HMAC pepper（{@code medconsult.crypto.id-no.pepper}），本批暂不做。
 */
public final class IdNoHasher {

    private IdNoHasher() {}

    /**
     * 计算 id_no 的 SHA-256 hex（64 字符小写）。null/空返回 null。
     */
    public static String hash(String idNo) {
        if (idNo == null || idNo.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(idNo.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }
}
