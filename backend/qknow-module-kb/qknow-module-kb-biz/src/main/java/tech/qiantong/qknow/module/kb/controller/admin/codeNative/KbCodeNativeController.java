package tech.qiantong.qknow.module.kb.controller.admin.codeNative;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.annotation.Log;
import tech.qiantong.qknow.common.enums.BusinessType;
import tech.qiantong.qknow.common.core.controller.BaseController;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.common.core.utils.object.BeanUtils;
import tech.qiantong.qknow.module.kb.controller.admin.codeNative.vo.KbCodeNativeRespVO;
import tech.qiantong.qknow.module.kb.controller.admin.codeNative.vo.KbCodeNativeSaveReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.codeNative.KbCodeNativeDO;
import tech.qiantong.qknow.module.kb.service.codeNative.IKbCodeNativeService;

import java.util.Arrays;

/**
 * 白盒化开发Controller
 *
 * @author qknow
 * @date 2026-04-09
 */
@Tag(name = "白盒化开发")
@RestController
@RequestMapping("/kb/codeNative")
@Validated
public class KbCodeNativeController extends BaseController {
    @Resource
    private IKbCodeNativeService kbCodeNativeService;

    @Operation(summary = "获取白盒化开发详细信息")
//    @PreAuthorize("@ss.hasPermi('kb:codeNative:codenative:query')")
    @GetMapping(value = "/{id}")
    public CommonResult<KbCodeNativeRespVO> getInfoByBotId(@PathVariable("id") Long id) {
        KbCodeNativeDO kbCodeNativeDO = kbCodeNativeService.getKbCodeNativeByBotId(id);
        return CommonResult.success(BeanUtils.toBean(kbCodeNativeDO, KbCodeNativeRespVO.class));
    }

    @Operation(summary = "修改白盒化开发")
//    @PreAuthorize("@ss.hasPermi('kb:codeNative:codenative:edit')")
    @Log(title = "白盒化开发", businessType = BusinessType.UPDATE)
    @PostMapping("/submit")
    public CommonResult<Boolean> submit(@Valid @RequestBody KbCodeNativeSaveReqVO kbCodeNative) {
        kbCodeNative.setWorkspaceId(super.getWorkSpaceId());
        return CommonResult.toAjax(kbCodeNativeService.submit(kbCodeNative));
    }

    @Operation(summary = "删除白盒化开发")
//    @PreAuthorize("@ss.hasPermi('kb:codeNative:codenative:remove')")
    @Log(title = "白盒化开发", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public CommonResult<Integer> remove(@PathVariable Long[] ids) {
        return CommonResult.toAjax(kbCodeNativeService.removeKbCodeNative(Arrays.asList(ids)));
    }

}
