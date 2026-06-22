package tech.qiantong.qknow.module.app.controller.admin.kac;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.qiantong.qknow.common.annotation.Log;
import tech.qiantong.qknow.common.enums.BusinessType;
import tech.qiantong.qknow.common.core.controller.BaseController;
import tech.qiantong.qknow.common.core.domain.AjaxResult;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.common.core.page.PageParam;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.common.core.utils.object.BeanUtils;
import tech.qiantong.qknow.common.core.utils.poi.ExcelUtil;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginSaveReqVO;
import tech.qiantong.qknow.module.app.convert.kac.KacPluginConvert;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacPluginDO;
import tech.qiantong.qknow.module.app.service.kac.IKacPluginService;

import java.util.Arrays;
import java.util.List;

/**
 * 插件管理Controller
 *
 * @author qknow
 * @date 2026-06-22
 */
@Tag(name = "插件管理")
@RestController
@RequestMapping("/kac/plugin")
@Validated
public class KacPluginController extends BaseController {
    @Resource
    private IKacPluginService kacPluginService;

    @Operation(summary = "查询插件管理列表")
    @PreAuthorize("@ss.hasPermi('kac:plugin:list')")
    @GetMapping("/list")
    public CommonResult<PageResult<KacPluginRespVO>> list(KacPluginPageReqVO kacPlugin) {
        PageResult<KacPluginDO> page = kacPluginService.getKacPluginPage(kacPlugin);
        return CommonResult.success(BeanUtils.toBean(page, KacPluginRespVO.class));
    }

    @Operation(summary = "导出插件管理列表")
    @PreAuthorize("@ss.hasPermi('kac:plugin:export')")
    @Log(title = "插件管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, KacPluginPageReqVO exportReqVO) {
        exportReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<KacPluginDO> list = (List<KacPluginDO>) kacPluginService.getKacPluginPage(exportReqVO).getRows();
        ExcelUtil<KacPluginRespVO> util = new ExcelUtil<>(KacPluginRespVO.class);
        util.exportExcel(response, KacPluginConvert.INSTANCE.convertToRespVOList(list), "插件管理数据");
    }

    @Operation(summary = "导入插件管理列表")
    @PreAuthorize("@ss.hasPermi('kac:plugin:import')")
    @Log(title = "插件管理", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception {
        ExcelUtil<KacPluginRespVO> util = new ExcelUtil<>(KacPluginRespVO.class);
        List<KacPluginRespVO> importExcelList = util.importExcel(file.getInputStream());
        String operName = getUsername();
        String message = kacPluginService.importKacPlugin(importExcelList, updateSupport, operName);
        return success(message);
    }

    @Operation(summary = "获取插件管理详细信息")
    @PreAuthorize("@ss.hasPermi('kac:plugin:query')")
    @GetMapping(value = "/{id}")
    public CommonResult<KacPluginRespVO> getInfo(@PathVariable("id") Long id) {
        KacPluginDO kacPluginDO = kacPluginService.getKacPluginById(id);
        return CommonResult.success(BeanUtils.toBean(kacPluginDO, KacPluginRespVO.class));
    }

    @Operation(summary = "新增插件管理")
    @PreAuthorize("@ss.hasPermi('kac:plugin:add')")
    @Log(title = "插件管理", businessType = BusinessType.INSERT)
    @PostMapping
    public CommonResult<Long> add(@Valid @RequestBody KacPluginSaveReqVO kacPlugin) {
        kacPlugin.setWorkspaceId(super.getWorkSpaceId());
        kacPlugin.setCreatorId(getUserId());
        kacPlugin.setCreateBy(getUsername());
        return CommonResult.toAjax(kacPluginService.createKacPlugin(kacPlugin));
    }

    @Operation(summary = "修改插件管理")
    @PreAuthorize("@ss.hasPermi('kac:plugin:edit')")
    @Log(title = "插件管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public CommonResult<Integer> edit(@Valid @RequestBody KacPluginSaveReqVO kacPlugin) {
        kacPlugin.setUpdateBy(getUsername());
        kacPlugin.setUpdaterId(getUserId());
        return CommonResult.toAjax(kacPluginService.updateKacPlugin(kacPlugin));
    }

    @Operation(summary = "删除插件管理")
    @PreAuthorize("@ss.hasPermi('kac:plugin:remove')")
    @Log(title = "插件管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public CommonResult<Integer> remove(@PathVariable Long[] ids) {
        return CommonResult.toAjax(kacPluginService.removeKacPlugin(Arrays.asList(ids)));
    }
}
