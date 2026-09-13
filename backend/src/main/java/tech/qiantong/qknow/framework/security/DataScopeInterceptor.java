package tech.qiantong.qknow.framework.security;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.sql.Connection;
import java.util.Properties;

@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class DataScopeInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        
        // 分离代理对象链(由于目标类可能被多个拦截器拦截，从而形成多次代理，通过循环可以分离出最原始的目标类)
        while (metaObject.hasGetter("h")) {
            Object object = metaObject.getValue("h");
            metaObject = SystemMetaObject.forObject(object);
        }
        while (metaObject.hasGetter("target")) {
            Object object = metaObject.getValue("target");
            metaObject = SystemMetaObject.forObject(object);
        }
        
        BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
        String originalSql = boundSql.getSql();
        
        String rewrittenSql = rewriteSql(originalSql);
        
        metaObject.setValue("delegate.boundSql.sql", rewrittenSql);
        
        return invocation.proceed();
    }

    public String rewriteSql(String originalSql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(originalSql);
            if (statement instanceof Select) {
                Select select = (Select) statement;
                SelectBody selectBody = select.getSelectBody();
                if (selectBody instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBody;
                    
                    Expression dataScopeCondition = CCJSqlParserUtil.parseCondExpression("dept_id = ?");
                    Expression where = plainSelect.getWhere();
                    
                    if (where == null) {
                        plainSelect.setWhere(dataScopeCondition);
                    } else {
                        // 防止原条件包含 OR 导致 AND 逻辑短路，所以使用括号包裹原 where
                        String wrappedWhereSql = "(" + where.toString() + ")";
                        Expression wrappedWhere = CCJSqlParserUtil.parseCondExpression(wrappedWhereSql);
                        plainSelect.setWhere(new AndExpression(wrappedWhere, dataScopeCondition));
                    }
                    return select.toString();
                }
            }
            return originalSql;
        } catch (JSQLParserException e) {
            throw new RuntimeException("SQL解析异常，AST安全重写失败: " + originalSql, e);
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
