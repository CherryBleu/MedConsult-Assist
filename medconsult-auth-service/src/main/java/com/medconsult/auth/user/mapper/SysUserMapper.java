package com.medconsult.auth.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.auth.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys_user Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
