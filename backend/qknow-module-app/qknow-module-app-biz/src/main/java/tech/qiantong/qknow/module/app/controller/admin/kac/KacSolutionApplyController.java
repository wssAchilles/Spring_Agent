package tech.qiantong.qknow.module.app.controller.admin.kac;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.annotation.Log;
import tech.qiantong.qknow.common.enums.BusinessType;
import tech.qiantong.qknow.common.core.controller.BaseController;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.common.core.utils.object.BeanUtils;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionApplyPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionApplyRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionApplySaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacSolutionApplyDO;
import tech.qiantong.qknow.module.app.service.kac.IKacSolutionApplyService;

import java.util.Arrays;

/**
 * 解决方案应用关联Controller
 *
 * @author qknow
 * @date 2026-06-22
 */
@Tag(name = "解决方案应用关联")
@RestController
@RequestMapping("/kac/solutionApply")
@Validated
public class KacSolutionApplyController extends BaseController {
    @Resource
    private IKacSolutionApplyService kacSolutionApplyService;

    @Operation(summary = "查询解决方案应用关联列表")
    @PreAuthorize("@ss.hasPermi('kac:solutionApply:list')")
    @GetMapping("/list")
    public CommonResult<PageResult<KacSolutionApplyRespVO>> list(KacSolutionApplyPageReqVO kacSolutionApply) {
        PageResult<KacSolutionApplyDO> page = kacSolutionApplyService.getKacSolutionApplyPage(kacSolutionApply);
        return CommonResult.success(BeanUtils.toBean(page, KacSolutionApplyRespVO.class));
    }

    @Operation(summary = "获取解决方案应用关联详细信息")
    @PreAuthorize("@ss.hasPermi('kac:solutionApply:query')")
    @GetMapping(value = "/{id}")
    public CommonResult<KacSolutionApplyRespVO> getInfo(@PathVariable("id") Long id) {
        KacSolutionApplyDO kacSolutionApplyDO = kacSolutionApplyService.getKacSolutionApplyById(id);
        return CommonResult.success(BeanUtils.toBean(kacSolutionApplyDO, KacSolutionApplyRespVO.class));
    }

    @Operation(summary = "新增解决方案应用关联")
    @PreAuthorize("@ss.hasPermi('kac:solutionApply:add')")
    @Log(title = "解决方案应用关联", businessType = BusinessType.INSERT)
    @PostMapping
    public CommonResult<Long> add(@Valid @RequestBody KacSolutionApplySaveReqVO kacSolutionApply) {
        kacSolutionApply.setWorkspaceId(super.getWorkSpaceId());
        kacSolutionApply.setCreatorId(getUserId());
        kacSolutionApply.setCreateBy(getUsername());
        return CommonResult.toAjax(kacSolutionApplyService.createKacSolutionApply(kacSolutionApply));
    }

    @Operation(summary = "修改解决方案应用关联")
    @PreAuthorize("@ss.hasPermi('kac:solutionApply:edit')")
    @Log(title = "解决方案应用关联", businessType = BusinessType.UPDATE)
    @PutMapping
    public CommonResult<Integer> edit(@Valid @RequestBody KacSolutionApplySaveReqVO kacSolutionApply) {
        kacSolutionApply.setUpdateBy(getUsername());
        kacSolutionApply.setUpdaterId(getUserId());
        return CommonResult.toAjax(kacSolutionApplyService.updateKacSolutionApply(kacSolutionApply));
    }

    @Operation(summary = "删除解决方案应用关联")
    @PreAuthorize("@ss.hasPermi('kac:solutionApply:remove')")
    @Log(title = "解决方案应用关联", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public CommonResult<Integer> remove(@PathVariable Long[] ids) {
        return CommonResult.toAjax(kacSolutionApplyService.removeKacSolutionApply(Arrays.asList(ids)));
    }
}
