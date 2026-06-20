package tech.qiantong.qknow.module.kb.service.codeNative;

import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.module.kb.controller.admin.codeNative.vo.KbCodeNativeSaveReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.codeNative.KbCodeNativeDO;

import java.util.Collection;

/**
 * 白盒化开发Service接口
 *
 * @author qknow
 * @date 2026-04-09
 */
public interface IKbCodeNativeService extends IService<KbCodeNativeDO> {

    /**
     * 删除白盒化开发
     *
     * @param idList 白盒化开发编号
     */
    int removeKbCodeNative(Collection<Long> idList);

    /**
     * 获得白盒化开发详情
     *
     * @param botId 白盒化开发编号
     * @return 白盒化开发
     */
    KbCodeNativeDO getKbCodeNativeByBotId(Long botId);

    /**
     * 提交白盒化开发数据
     * @param kbCodeNative 白盒化开发数据
     * @return 操作是否成功
     */
    Boolean submit(KbCodeNativeSaveReqVO kbCodeNative);
}
