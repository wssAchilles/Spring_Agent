package tech.qiantong.qknow.common.core.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import tech.qiantong.qknow.common.constant.HttpStatus;
import tech.qiantong.qknow.common.core.page.PageDomain;
import tech.qiantong.qknow.common.core.page.TableDataInfo;
import tech.qiantong.qknow.common.core.page.TableSupport;
import tech.qiantong.qknow.common.utils.DateUtils;
import tech.qiantong.qknow.common.core.utils.PageUtils;
import tech.qiantong.qknow.common.core.utils.SecurityUtils;
import tech.qiantong.qknow.common.utils.StringUtils;
import tech.qiantong.qknow.common.utils.sql.SqlUtil;
import tech.qiantong.qknow.common.core.domain.AjaxResult;
import tech.qiantong.qknow.common.core.domain.model.LoginUser;

import java.beans.PropertyEditorSupport;
import java.util.Date;
import java.util.List;

/**
 * web层通用数据处理
 *
 * @author qknow
 */
public class BaseController
{
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 将前台传递过来的日期格式的字符串，自动转化为Date类型
     */
    @InitBinder
    public void initBinder(WebDataBinder binder)
    {
        // Date 类型转换
        binder.registerCustomEditor(Date.class, new PropertyEditorSupport()
        {
            @Override
            public void setAsText(String text)
            {
                setValue(DateUtils.parseDate(text));
            }
        });
    }

    /**
     * 设置请求分页数据
     */
    protected void startPage()
    {
        PageUtils.startPage();
    }

    /**
     * 设置请求排序数据
     */
    protected void startOrderBy()
    {
        PageDomain pageDomain = TableSupport.buildPageRequest();
        if (StringUtils.isNotEmpty(pageDomain.getOrderBy()))
        {
            String orderBy = SqlUtil.escapeOrderBySql(pageDomain.getOrderBy());
            PageHelper.orderBy(orderBy);
        }
    }

    /**
     * 清理分页的线程变量
     */
    protected void clearPage()
    {
        PageUtils.clearPage();
    }

    /**
     * 响应请求分页数据
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected TableDataInfo getDataTable(List<?> list)
    {
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setMsg("查询成功");
        rspData.setRows(list);
        rspData.setTotal(new PageInfo(list).getTotal());
        return rspData;
    }

    /**
     * 返回成功
     */
    public AjaxResult success()
    {
        return AjaxResult.success();
    }

    /**
     * 返回失败消息
     */
    public AjaxResult error()
    {
        return AjaxResult.error();
    }

    /**
     * 返回成功消息
     */
    public AjaxResult success(String message)
    {
        return AjaxResult.success(message);
    }

    /**
     * 返回成功消息
     */
    public AjaxResult success(Object data)
    {
        return AjaxResult.success(data);
    }

    /**
     * 返回失败消息
     */
    public AjaxResult error(String message)
    {
        return AjaxResult.error(message);
    }

    /**
     * 返回警告消息
     */
    public AjaxResult warn(String message)
    {
        return AjaxResult.warn(message);
    }

    /**
     * 响应返回结果
     *
     * @param rows 影响行数
     * @return 操作结果
     */
    protected AjaxResult toAjax(int rows)
    {
        return rows > 0 ? AjaxResult.success() : AjaxResult.error();
    }

    /**
     * 响应返回结果
     *
     * @param result 结果
     * @return 操作结果
     */
    protected AjaxResult toAjax(boolean result)
    {
        return result ? success() : error();
    }

    /**
     * 页面跳转
     */
    public String redirect(String url)
    {
        return StringUtils.format("redirect:{}", url);
    }

    /**
     * 获取用户缓存信息
     */
    public LoginUser getLoginUser()
    {
        return SecurityUtils.getLoginUser();
    }

    /**
     * 获取登录用户id
     */
    public Long getUserId()
    {
        return getLoginUser().getUserId();
    }

    /**
     * 获取登录部门id
     */
    public Long getDeptId()
    {
        return getLoginUser().getDeptId();
    }

    /**
     * 获取登录用户名
     */
    public String getUsername()
    {
        return getLoginUser().getUsername();
    }

    /**
     * 获取登录人姓名
     */
    public String getNickName()
    {
        return getLoginUser().getUser().getNickName();
    }

    /**
     * 获取工作区id，严禁盲信客户端传入的任意请求头，防范跨租户越权漏洞 (R6)
     * 1. 优先从当前登录用户上下文获取安全工作区 (如用户的 deptId，或保底 1001L)
     * 2. 若客户端传入 workspace-id / X-Workspace-Id 请求头，必须校验其合法性：
     *    - 超级管理员允许切换指定有效工作区
     *    - 普通用户仅允许访问其所属的合法工作区，若传入越权工作区则拒绝并安全回退
     * 3. 若无请求头或校验不通过，安全回退至当前登录用户的合法默认工作区
     */
    public Long getWorkSpaceId()
    {
        Long defaultWorkspaceId = 1001L;
        boolean isAdmin = false;
        try
        {
            // 通过反射或 SecurityFrameworkUtils 获取当前登录用户，避免 qknow-common 产生强循环依赖
            Class<?> secClazz = Class.forName("tech.qiantong.qknow.security.utils.SecurityFrameworkUtils");
            java.lang.reflect.Method getWsMethod = secClazz.getMethod("getLoginUserWorkspaceId");
            Long userWs = (Long) getWsMethod.invoke(null);
            if (userWs != null && userWs > 0) {
                defaultWorkspaceId = userWs;
            }
            java.lang.reflect.Method getLoginUserMethod = secClazz.getMethod("getLoginUser");
            Object loginUserObj = getLoginUserMethod.invoke(null);
            if (loginUserObj != null) {
                java.lang.reflect.Method getUserIdMethod = loginUserObj.getClass().getMethod("getUserId");
                Long userId = (Long) getUserIdMethod.invoke(loginUserObj);
                if (userId != null && userId == 1L) {
                    isAdmin = true;
                }
            }
        }
        catch (Exception ignored)
        {
            // 无安全框架上下文（如单元测试或异步调度），安全保底 1001L
        }

        try
        {
            jakarta.servlet.http.HttpServletRequest request = tech.qiantong.qknow.common.utils.ServletUtils.getRequest();
            if (request != null)
            {
                String header = request.getHeader("workspace-id");
                if (cn.hutool.core.util.StrUtil.isBlank(header)) {
                    header = request.getHeader("X-Workspace-Id");
                }
                if (cn.hutool.core.util.StrUtil.isNotBlank(header)) {
                    long requestedId = Long.parseLong(header.trim());
                    if (requestedId > 0 && (isAdmin || java.util.Objects.equals(requestedId, defaultWorkspaceId))) {
                        return requestedId;
                    } else {
                        logger.warn("检测到越权或非法的 workspace-id 请求头: requested={}, fallbackTo={}", requestedId, defaultWorkspaceId);
                    }
                }
            }
        }
        catch (Exception ignored)
        {
        }
        return defaultWorkspaceId;
    }
}
