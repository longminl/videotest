package com.videocollect.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;

/**
 * MyBatis 拦截器：在 UPDATE 执行前自动注入 updatedAt 参数。
 * 兼容 MySQL（与 ON UPDATE CURRENT_TIMESTAMP 不冲突）和 SQLite（无 ON UPDATE 能力）。
 * 各 UPDATE Mapper 需在 SQL 末尾添加 ", updated_at = #{updatedAt}"。
 */
@Component
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class UpdateTimestampInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(UpdateTimestampInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        // 只对 UPDATE 生效，INSERT/DELETE 不做处理
        if (ms.getSqlCommandType() == SqlCommandType.UPDATE) {
            Object param = invocation.getArgs()[1];
            if (param instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> paramMap = (Map<String, Object>) param;
                if (!paramMap.containsKey("updatedAt")) {
                    paramMap.put("updatedAt", LocalDateTime.now());
                }
            } else {
                log.warn("UPDATE 参数不是 Map 类型 (class={})，跳过 updated_at 注入", param.getClass().getName());
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {}
}
