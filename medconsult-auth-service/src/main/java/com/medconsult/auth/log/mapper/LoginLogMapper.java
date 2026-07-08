package com.medconsult.auth.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.auth.log.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * login_log Mapper。
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
