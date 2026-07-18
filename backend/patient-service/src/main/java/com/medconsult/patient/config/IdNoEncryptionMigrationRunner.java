package com.medconsult.patient.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.common.crypto.CryptoService;
import com.medconsult.common.crypto.IdNoHasher;
import com.medconsult.patient.entity.Patient;
import com.medconsult.patient.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * id_no 加密迁移器（§5.3，2026-07-17）。
 *
 * <p>启动时把存量<b>明文</b> id_no 加密 + 回填 id_no_hash，可重入。
 *
 * <p><b>可重入判定</b>：仅处理 {@code id_no_hash IS NULL}（且 id_no 非空）的记录——
 * 加密后的记录已有 hash，重跑自动跳过。
 *
 * <p><b>读取兼容</b>：TypeHandler 读存量明文时"解密失败原样返回"（见 CryptoService.decrypt），
 * 故 selectList 拿到的 idNo 仍是明文，直接加密 + 算 hash 回写即可。
 *
 * <p><b>未配置加密 key 时不迁移</b>：CryptoService 未装配（CryptoHolder.get()=null）时跳过，
 * 避免在未启用加密的环境误操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(CryptoService.class)  // 未配置加密 key 时（CryptoService 未装配）跳过迁移
@Order(30)  // 晚于业务初始化
public class IdNoEncryptionMigrationRunner implements CommandLineRunner {

    private final PatientMapper patientMapper;
    private final CryptoService cryptoService;

    @Override
    public void run(String... args) {
        // 仅处理 id_no_hash 为空但 id_no 有值的记录（明文存量）
        List<Patient> pending = patientMapper.selectList(
                new QueryWrapper<Patient>().isNull("id_no_hash").isNotNull("id_no"));
        if (pending.isEmpty()) {
            log.debug("[IdNoMigration] 无需迁移（id_no_hash 为空的记录为 0，可能已全部加密）");
            return;
        }
        log.info("[IdNoMigration] 发现 {} 条明文 id_no 待加密迁移", pending.size());
        int migrated = 0;
        int skipped = 0;
        for (Patient p : pending) {
            String idNo = p.getIdNo();
            if (idNo == null || idNo.isBlank()) {
                skipped++;
                continue;
            }
            // 兼容：若 idNo 已是密文（不应发生在此查询条件下），hash 计算会基于密文——但 isNull(id_no_hash)
            // 已保证只处理未加密记录，此处 idNo 是明文
            String encrypted = cryptoService.encrypt(idNo);
            String hash = IdNoHasher.hash(idNo);
            p.setIdNo(encrypted);
            p.setIdNoHash(hash);
            // 直接 updateById：idNo 经 TypeHandler 会再次加密（双加密！）——故绕过 TypeHandler 用 update SQL
            // 简化：用 mapper.update + UpdateWrapper 显式 set 已加密值
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Patient> uw =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            uw.eq("id", p.getId())
                    .set("id_no", encrypted)     // 直接写密文（绕过 TypeHandler，避免二次加密）
                    .set("id_no_hash", hash);
            patientMapper.update(null, uw);
            migrated++;
        }
        log.info("[IdNoMigration] 迁移完成：加密 {} 条，跳过 {} 条", migrated, skipped);
    }
}
