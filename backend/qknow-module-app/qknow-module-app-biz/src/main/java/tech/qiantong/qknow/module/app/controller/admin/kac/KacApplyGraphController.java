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
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyGraphPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyGraphRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyGraphSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyGraphDO;
import tech.qiantong.qknow.module.app.service.kac.IKacApplyGraphService;

import java.util.Arrays;

/**
 * 应用图谱关联Controller
 *
 * @author qknow
 * @date 2026-06-22
 */
@Tag(name = "应用图谱关联")
@RestController
@RequestMapping("/kac/graph")
@Validated
public class KacApplyGraphController extends BaseController {
    @Resource
    private IKacApplyGraphService kacApplyGraphService;

    @Operation(summary = "查询应用图谱关联列表")
    @PreAuthorize("@ss.hasPermi('kac:graph:list')")
    @GetMapping("/list")
    public CommonResult<PageResult<KacApplyGraphRespVO>> list(KacApplyGraphPageReqVO kacApplyGraph) {
        PageResult<KacApplyGraphDO> page = kacApplyGraphService.getKacApplyGraphPage(kacApplyGraph);
        return CommonResult.success(BeanUtils.toBean(page, KacApplyGraphRespVO.class));
    }

    @Operation(summary = "获取应用图谱关联详细信息")
    @PreAuthorize("@ss.hasPermi('kac:graph:query')")
    @GetMapping(value = "/{id}")
    public CommonResult<KacApplyGraphRespVO> getInfo(@PathVariable("id") Long id) {
        KacApplyGraphDO kacApplyGraphDO = kacApplyGraphService.getKacApplyGraphById(id);
        return CommonResult.success(BeanUtils.toBean(kacApplyGraphDO, KacApplyGraphRespVO.class));
    }

    @Operation(summary = "新增应用图谱关联")
    @PreAuthorize("@ss.hasPermi('kac:graph:add')")
    @Log(title = "应用图谱关联", businessType = BusinessType.INSERT)
    @PostMapping
    public CommonResult<Long> add(@Valid @RequestBody KacApplyGraphSaveReqVO kacApplyGraph) {
        kacApplyGraph.setWorkspaceId(super.getWorkSpaceId());
        kacApplyGraph.setCreatorId(getUserId());
        kacApplyGraph.setCreateBy(getUsername());
        return CommonResult.toAjax(kacApplyGraphService.createKacApplyGraph(kacApplyGraph));
    }

    @Operation(summary = "修改应用图谱关联")
    @PreAuthorize("@ss.hasPermi('kac:graph:edit')")
    @Log(title = "应用图谱关联", businessType = BusinessType.UPDATE)
    @PutMapping
    public CommonResult<Integer> edit(@Valid @RequestBody KacApplyGraphSaveReqVO kacApplyGraph) {
        kacApplyGraph.setUpdateBy(getUsername());
        kacApplyGraph.setUpdaterId(getUserId());
        return CommonResult.toAjax(kacApplyGraphService.updateKacApplyGraph(kacApplyGraph));
    }

    @Operation(summary = "删除应用图谱关联")
    @PreAuthorize("@ss.hasPermi('kac:graph:remove')")
    @Log(title = "应用图谱关联", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public CommonResult<Integer> remove(@PathVariable Long[] ids) {
        return CommonResult.toAjax(kacApplyGraphService.removeKacApplyGraph(Arrays.asList(ids)));
    }
}
