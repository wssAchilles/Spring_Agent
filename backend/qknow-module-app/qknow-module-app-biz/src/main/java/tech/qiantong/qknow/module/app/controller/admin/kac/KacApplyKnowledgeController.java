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
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyKnowledgePageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyKnowledgeRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyKnowledgeSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyKnowledgeDO;
import tech.qiantong.qknow.module.app.service.kac.IKacApplyKnowledgeService;

import java.util.Arrays;

/**
 * 应用知识库关联Controller
 *
 * @author qknow
 * @date 2026-06-22
 */
@Tag(name = "应用知识库关联")
@RestController
@RequestMapping("/kac/knowledge")
@Validated
public class KacApplyKnowledgeController extends BaseController {
    @Resource
    private IKacApplyKnowledgeService kacApplyKnowledgeService;

    @Operation(summary = "查询应用知识库关联列表")
    @PreAuthorize("@ss.hasPermi('kac:knowledge:list')")
    @GetMapping("/list")
    public CommonResult<PageResult<KacApplyKnowledgeRespVO>> list(KacApplyKnowledgePageReqVO kacApplyKnowledge) {
        PageResult<KacApplyKnowledgeDO> page = kacApplyKnowledgeService.getKacApplyKnowledgePage(kacApplyKnowledge);
        return CommonResult.success(BeanUtils.toBean(page, KacApplyKnowledgeRespVO.class));
    }

    @Operation(summary = "获取应用知识库关联详细信息")
    @PreAuthorize("@ss.hasPermi('kac:knowledge:query')")
    @GetMapping(value = "/{id}")
    public CommonResult<KacApplyKnowledgeRespVO> getInfo(@PathVariable("id") Long id) {
        KacApplyKnowledgeDO kacApplyKnowledgeDO = kacApplyKnowledgeService.getKacApplyKnowledgeById(id);
        return CommonResult.success(BeanUtils.toBean(kacApplyKnowledgeDO, KacApplyKnowledgeRespVO.class));
    }

    @Operation(summary = "新增应用知识库关联")
    @PreAuthorize("@ss.hasPermi('kac:knowledge:add')")
    @Log(title = "应用知识库关联", businessType = BusinessType.INSERT)
    @PostMapping
    public CommonResult<Long> add(@Valid @RequestBody KacApplyKnowledgeSaveReqVO kacApplyKnowledge) {
        kacApplyKnowledge.setWorkspaceId(super.getWorkSpaceId());
        kacApplyKnowledge.setCreatorId(getUserId());
        kacApplyKnowledge.setCreateBy(getUsername());
        return CommonResult.toAjax(kacApplyKnowledgeService.createKacApplyKnowledge(kacApplyKnowledge));
    }

    @Operation(summary = "修改应用知识库关联")
    @PreAuthorize("@ss.hasPermi('kac:knowledge:edit')")
    @Log(title = "应用知识库关联", businessType = BusinessType.UPDATE)
    @PutMapping
    public CommonResult<Integer> edit(@Valid @RequestBody KacApplyKnowledgeSaveReqVO kacApplyKnowledge) {
        kacApplyKnowledge.setUpdateBy(getUsername());
        kacApplyKnowledge.setUpdaterId(getUserId());
        return CommonResult.toAjax(kacApplyKnowledgeService.updateKacApplyKnowledge(kacApplyKnowledge));
    }

    @Operation(summary = "删除应用知识库关联")
    @PreAuthorize("@ss.hasPermi('kac:knowledge:remove')")
    @Log(title = "应用知识库关联", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public CommonResult<Integer> remove(@PathVariable Long[] ids) {
        return CommonResult.toAjax(kacApplyKnowledgeService.removeKacApplyKnowledge(Arrays.asList(ids)));
    }
}
