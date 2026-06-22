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
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionSaveReqVO;
import tech.qiantong.qknow.module.app.convert.kac.KacSolutionConvert;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacSolutionDO;
import tech.qiantong.qknow.module.app.service.kac.IKacSolutionService;

import java.util.Arrays;
import java.util.List;

/**
 * 解决方案管理Controller
 *
 * @author qknow
 * @date 2026-06-22
 */
@Tag(name = "解决方案管理")
@RestController
@RequestMapping("/kac/solution")
@Validated
public class KacSolutionController extends BaseController {
    @Resource
    private IKacSolutionService kacSolutionService;

    @Operation(summary = "查询解决方案管理列表")
    @PreAuthorize("@ss.hasPermi('kac:solution:list')")
    @GetMapping("/list")
    public CommonResult<PageResult<KacSolutionRespVO>> list(KacSolutionPageReqVO kacSolution) {
        PageResult<KacSolutionDO> page = kacSolutionService.getKacSolutionPage(kacSolution);
        return CommonResult.success(BeanUtils.toBean(page, KacSolutionRespVO.class));
    }

    @Operation(summary = "导出解决方案管理列表")
    @PreAuthorize("@ss.hasPermi('kac:solution:export')")
    @Log(title = "解决方案管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, KacSolutionPageReqVO exportReqVO) {
        exportReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<KacSolutionDO> list = (List<KacSolutionDO>) kacSolutionService.getKacSolutionPage(exportReqVO).getRows();
        ExcelUtil<KacSolutionRespVO> util = new ExcelUtil<>(KacSolutionRespVO.class);
        util.exportExcel(response, KacSolutionConvert.INSTANCE.convertToRespVOList(list), "解决方案管理数据");
    }

    @Operation(summary = "导入解决方案管理列表")
    @PreAuthorize("@ss.hasPermi('kac:solution:import')")
    @Log(title = "解决方案管理", businessType = BusinessType.IMPORT)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception {
        ExcelUtil<KacSolutionRespVO> util = new ExcelUtil<>(KacSolutionRespVO.class);
        List<KacSolutionRespVO> importExcelList = util.importExcel(file.getInputStream());
        String operName = getUsername();
        String message = kacSolutionService.importKacSolution(importExcelList, updateSupport, operName);
        return success(message);
    }

    @Operation(summary = "获取解决方案管理详细信息")
    @PreAuthorize("@ss.hasPermi('kac:solution:query')")
    @GetMapping(value = "/{id}")
    public CommonResult<KacSolutionRespVO> getInfo(@PathVariable("id") Long id) {
        KacSolutionDO kacSolutionDO = kacSolutionService.getKacSolutionById(id);
        return CommonResult.success(BeanUtils.toBean(kacSolutionDO, KacSolutionRespVO.class));
    }

    @Operation(summary = "新增解决方案管理")
    @PreAuthorize("@ss.hasPermi('kac:solution:add')")
    @Log(title = "解决方案管理", businessType = BusinessType.INSERT)
    @PostMapping
    public CommonResult<Long> add(@Valid @RequestBody KacSolutionSaveReqVO kacSolution) {
        kacSolution.setWorkspaceId(super.getWorkSpaceId());
        kacSolution.setCreatorId(getUserId());
        kacSolution.setCreateBy(getUsername());
        return CommonResult.toAjax(kacSolutionService.createKacSolution(kacSolution));
    }

    @Operation(summary = "修改解决方案管理")
    @PreAuthorize("@ss.hasPermi('kac:solution:edit')")
    @Log(title = "解决方案管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public CommonResult<Integer> edit(@Valid @RequestBody KacSolutionSaveReqVO kacSolution) {
        kacSolution.setUpdateBy(getUsername());
        kacSolution.setUpdaterId(getUserId());
        return CommonResult.toAjax(kacSolutionService.updateKacSolution(kacSolution));
    }

    @Operation(summary = "删除解决方案管理")
    @PreAuthorize("@ss.hasPermi('kac:solution:remove')")
    @Log(title = "解决方案管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public CommonResult<Integer> remove(@PathVariable Long[] ids) {
        return CommonResult.toAjax(kacSolutionService.removeKacSolution(Arrays.asList(ids)));
    }
}
