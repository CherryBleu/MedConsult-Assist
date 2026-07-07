package com.medconsult.common.web;

/**
 * 脱敏类型与规则（架构文档 §3.2 / 《修改建议》§5.3）。
 *
 * <p>对照《接口文档》中已有的脱敏示例：
 * <ul>
 *   <li>{@code phoneMasked: "138****0001"} → {@link #PHONE}</li>
 *   <li>{@code idNoMasked: "110101********0011"} → {@link #ID_NO}</li>
 * </ul>
 */
public enum MaskType {

    /**
     * 手机号：保留前 3 后 4，中间 4 位打码。
     * 例：13800000001 → 138****0001
     */
    PHONE {
        @Override
        public String mask(String s) {
            if (s == null || s.length() < 7) return s;
            return s.substring(0, 3) + "****" + s.substring(s.length() - 4);
        }
    },

    /**
     * 身份证号：保留前 6 后 4，中间打码（与《接口文档》示例一致）。
     * 例：110101198805120011 → 110101********0011
     */
    ID_NO {
        @Override
        public String mask(String s) {
            if (s == null || s.length() < 10) return s;
            int len = s.length();
            String head = s.substring(0, 6);
            String tail = s.substring(len - 4);
            return head + "*".repeat(len - 10) + tail;
        }
    },

    /**
     * 姓名：保留首字，其余打 *（适中文姓名）。
     * 例：张三 → 张*；欧阳娜娜 → 欧***
     */
    NAME {
        @Override
        public String mask(String s) {
            if (s == null || s.length() <= 1) return s;
            return s.charAt(0) + "*".repeat(s.length() - 1);
        }
    },

    /**
     * 邮箱：保留首字符与 @ 后域名，本地部分打码。
     * 例：zhangsan@example.com → z******@example.com
     */
    EMAIL {
        @Override
        public String mask(String s) {
            if (s == null) return s;
            int at = s.indexOf('@');
            if (at <= 1) return s;
            return s.charAt(0) + "*".repeat(at - 1) + s.substring(at);
        }
    },

    /**
     * 完整打码（如病历号、卡号等只需"存在性"展示的字段）。
     * 例：MR202607060001 → ************
     */
    FULL {
        @Override
        public String mask(String s) {
            if (s == null) return s;
            return "*".repeat(s.length());
        }
    };

    /**
     * 应用脱敏规则。null 或长度不足时原样返回（不打码失败也不抛异常，保证响应可用）。
     */
    public abstract String mask(String s);
}
