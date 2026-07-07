package com.medconsult.common.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * common-mybatis 自动装配（架构文档 §3.2）。
 *
 * <p>业务服务引入本模块后：
 * <ul>
 *   <li>注册 {@link AutoFillMetaHandler}（自动填充 created_at/updated_at/deleted）</li>
 *   <li>注册分页插件 {@link PaginationInnerInterceptor}（MySQL 方言）</li>
 *   <li>{@link BaseEntity} 子类自动享受逻辑删除（{@code @TableLogic}）</li>
 * </ul>
 *
 * <p>JSON 字段处理：MyBatis-Plus 内置 {@code JacksonTypeHandler}，实体字段上
 * {@code @TableField(typeHandler = JacksonTypeHandler.class)} + 类上
 * {@code @TableName(autoResultMap = true)} 即可，无需自定义 handler。
 *
 * <p>数据源配置由业务服务自己在 application.yml 提供（spring.datasource.*），
 * 本模块只提供 MP 侧的定制。
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor")
public class MedConsultMybatisAutoConfiguration {

    /**
     * MyBatis-Plus 拦截器链：分页（MySQL 方言）。
     * 业务方需要更多拦截器（如乐观锁）可覆盖此 bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor medconsultMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自动填充处理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public AutoFillMetaHandler medconsultAutoFillMetaHandler() {
        return new AutoFillMetaHandler();
    }
}
