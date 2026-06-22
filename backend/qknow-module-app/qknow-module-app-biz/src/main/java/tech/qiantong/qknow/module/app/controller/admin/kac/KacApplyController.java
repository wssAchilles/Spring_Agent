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
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplySaveReqVO;
import tech.qiantong.qknow.module.app.convert.kac.KacApplyConvert;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyDO;
import tech.qiantong.qknow.module.app.service.kac.IKacApplyService;

import java.util.Arrays;
import java.util.List;

/**
 * 应用管理Controller
 *
 * @author qknow
 * @date 2026-06-22
 */
@Tag(name = "应用管理")
@RestController
@RequestMapping("/kac/apply")
@Validated
public class KacApplyController extends BaseController {
    @Resource
    private IKacApplyService kacApplyService;

    @Operation(summary = "查询应用管理列表")
    @PreAuthorize("@ss.hasPermi('kac:apply:list')")
    @GetMapping("/list")
    public CommonResult<PageResult<KacApplyRespVO>> list(KacApplyPageReqVO kacApply) {
        PageResult<KacApplyDO> page = kacApplyService.getKacApplyPage(kacApply);
        return CommonResult.success(BeanUtils.toBean(page, KacApplyRespVO.class));
    }

    @Operation(summary = "导出应用管理列表")
    @PreAuthorize("@ss.hasPermi('kac:apply:export')")
    @Log(title = "应用管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, KacApplyPageReqVO exportReqVO) {
        exportReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<KacApplyDO> list = (List<KacApplyDO>) kacApplyService.getKacApplyPage(exportReqVO).getRows();
        ExcelUtil<KacApplyRespVO> util = new ExcelUtil<>(KacApplyRespVO.class);
        util.exportExcel(response, KacApplyConvert.INSTANCE.convertToRespVOList(list), "应用管理数据");
    }

    @Operation(summary = "导入应用管理列表")
    @PreAuthorize("@ss.hasPermi('kac:apply:import')")
    @Log(title = "应用管理", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception {
        ExcelUtil<KacApplyRespVO> util = new ExcelUtil<>(KacApplyRespVO.class);
        List<KacApplyRespVO> importExcelList = util.importExcel(file.getInputStream());
        String operName = getUsername();
        String message = kacApplyService.importKacApply(importExcelList, updateSupport, operName);
        return success(message);
    }

    @Operation(summary = "获取应用管理详细信息")
    @PreAuthorize("@ss.hasPermi('kac:apply:query')")
    @GetMapping(value = "/{id}")
    public CommonResult<KacApplyRespVO> getInfo(@PathVariable("id") Long id) {
        KacApplyDO kacApplyDO = kacApplyService.getKacApplyById(id);
        return CommonResult.success(BeanUtils.toBean(kacApplyDO, KacApplyRespVO.class));
    }

    @Operation(summary = "新增应用管理")
    @PreAuthorize("@ss.hasPermi('kac:apply:add')")
    @Log(title = "应用管理", businessType = BusinessType.INSERT)
    @PostMapping
    public CommonResult<Long> add(@Valid @RequestBody KacApplySaveReqVO kacApply) {
        kacApply.setWorkspaceId(super.getWorkSpaceId());
        kacApply.setCreatorId(getUserId());
        kacApply.setCreateBy(getUsername());
        return CommonResult.toAjax(kacApplyService.createKacApply(kacApply));
    }

    @Operation(summary = "修改应用管理")
    @PreAuthorize("@ss.hasPermi('kac:apply:edit')")
    @Log(title = "应用管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public CommonResult<Integer> edit(@Valid @RequestBody KacApplySaveReqVO kacApply) {
        kacApply.setUpdateBy(getUsername());
        kacApply.setUpdaterId(getUserId());
        return CommonResult.toAjax(kacApplyService.updateKacApply(kacApply));
    }

    @Operation(summary = "删除应用管理")
    @PreAuthorize("@ss.hasPermi('kac:apply:remove')")
    @Log(title = "应用管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public CommonResult<Integer> remove(@PathVariable Long[] ids) {
        return CommonResult.toAjax(kacApplyService.removeKacApply(Arrays.asList(ids)));
    }
}
