package tech.qiantong.qknow.framework.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataScopeInterceptorTest {

    @Test
    public void testNoWhereCondition() {
        DataScopeInterceptor interceptor = new DataScopeInterceptor();
        String originalSql = "SELECT id, name FROM sys_user";
        String expectedSql = "SELECT id, name FROM sys_user WHERE dept_id = ?";
        
        String rewritten = interceptor.rewriteSql(originalSql);
        assertEquals(expectedSql, rewritten);
    }

    @Test
    public void testWithWhereCondition() {
        DataScopeInterceptor interceptor = new DataScopeInterceptor();
        String originalSql = "SELECT id, name FROM sys_user WHERE status = 1";
        String expectedSql = "SELECT id, name FROM sys_user WHERE (status = 1) AND dept_id = ?";
        
        String rewritten = interceptor.rewriteSql(originalSql);
        assertEquals(expectedSql, rewritten);
    }

    @Test
    public void testWithOrCondition() {
        DataScopeInterceptor interceptor = new DataScopeInterceptor();
        String originalSql = "SELECT id, name FROM sys_user WHERE status = 1 OR status = 2";
        String expectedSql = "SELECT id, name FROM sys_user WHERE (status = 1 OR status = 2) AND dept_id = ?";
        
        String rewritten = interceptor.rewriteSql(originalSql);
        assertEquals(expectedSql, rewritten);
    }
    
    @Test
    public void testWithPlaceholder() {
        DataScopeInterceptor interceptor = new DataScopeInterceptor();
        String originalSql = "SELECT id, name FROM sys_user WHERE username = ?";
        String expectedSql = "SELECT id, name FROM sys_user WHERE (username = ?) AND dept_id = ?";
        
        String rewritten = interceptor.rewriteSql(originalSql);
        assertEquals(expectedSql, rewritten);
    }
}
