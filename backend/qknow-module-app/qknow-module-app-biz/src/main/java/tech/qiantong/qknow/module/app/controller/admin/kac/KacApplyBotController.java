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
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyBotPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyBotRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyBotSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyBotDO;
import tech.qiantong.qknow.module.app.service.kac.IKacApplyBotService;

import java.util.Arrays;

/**
 * 应用机器人关联Controller
 *
 * @author qknow
 * @date 2026-06-22
 */
@Tag(name = "应用机器人关联")
@RestController
@RequestMapping("/kac/bot")
@Validated
public class KacApplyBotController extends BaseController {
    @Resource
    private IKacApplyBotService kacApplyBotService;

    @Operation(summary = "查询应用机器人关联列表")
    @PreAuthorize("@ss.hasPermi('kac:bot:list')")
    @GetMapping("/list")
    public CommonResult<PageResult<KacApplyBotRespVO>> list(KacApplyBotPageReqVO kacApplyBot) {
        PageResult<KacApplyBotDO> page = kacApplyBotService.getKacApplyBotPage(kacApplyBot);
        return CommonResult.success(BeanUtils.toBean(page, KacApplyBotRespVO.class));
    }

    @Operation(summary = "获取应用机器人关联详细信息")
    @PreAuthorize("@ss.hasPermi('kac:bot:query')")
    @GetMapping(value = "/{id}")
    public CommonResult<KacApplyBotRespVO> getInfo(@PathVariable("id") Long id) {
        KacApplyBotDO kacApplyBotDO = kacApplyBotService.getKacApplyBotById(id);
        return CommonResult.success(BeanUtils.toBean(kacApplyBotDO, KacApplyBotRespVO.class));
    }

    @Operation(summary = "新增应用机器人关联")
    @PreAuthorize("@ss.hasPermi('kac:bot:add')")
    @Log(title = "应用机器人关联", businessType = BusinessType.INSERT)
    @PostMapping
    public CommonResult<Long> add(@Valid @RequestBody KacApplyBotSaveReqVO kacApplyBot) {
        kacApplyBot.setWorkspaceId(super.getWorkSpaceId());
        kacApplyBot.setCreatorId(getUserId());
        kacApplyBot.setCreateBy(getUsername());
        return CommonResult.toAjax(kacApplyBotService.createKacApplyBot(kacApplyBot));
    }

    @Operation(summary = "修改应用机器人关联")
    @PreAuthorize("@ss.hasPermi('kac:bot:edit')")
    @Log(title = "应用机器人关联", businessType = BusinessType.UPDATE)
    @PutMapping
    public CommonResult<Integer> edit(@Valid @RequestBody KacApplyBotSaveReqVO kacApplyBot) {
        kacApplyBot.setUpdateBy(getUsername());
        kacApplyBot.setUpdaterId(getUserId());
        return CommonResult.toAjax(kacApplyBotService.updateKacApplyBot(kacApplyBot));
    }

    @Operation(summary = "删除应用机器人关联")
    @PreAuthorize("@ss.hasPermi('kac:bot:remove')")
    @Log(title = "应用机器人关联", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public CommonResult<Integer> remove(@PathVariable Long[] ids) {
        return CommonResult.toAjax(kacApplyBotService.removeKacApplyBot(Arrays.asList(ids)));
    }
}
