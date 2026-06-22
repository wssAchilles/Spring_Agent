package tech.qiantong.qknow.module.app.api.service.impl;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.app.api.service.IAppGraphApiService;
import tech.qiantong.qknow.module.app.controller.admin.appGraph.vo.AppGraphRelationshipSaveReqVO;
import tech.qiantong.qknow.module.app.controller.admin.appGraph.vo.AppGraphVO;
import tech.qiantong.qknow.module.app.service.appGraph.AppGraphService;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 图谱
 */
@Service
public class AppGraphApiServiceImpl implements IAppGraphApiService {
    @Autowired(required = false)
    private AppGraphService appGraphService;

    /**
     * 获取图谱数据
     *
     * @param appGraphVO
     * @return
     */
    public Map<String, Object> getGraph(AppGraphVO appGraphVO) {
        if (appGraphService == null) {
            return Collections.emptyMap();
        }
        return appGraphService.getGraph(appGraphVO);
    }

    public Boolean addTripletRel(AppGraphRelationshipSaveReqVO graphRelationshipSaveReqVO) {
        if (appGraphService == null) {
            return false;
        }
        List<AppGraphRelationshipSaveReqVO> appGraphRelationshipSaveReqVOS = Lists.newArrayList(graphRelationshipSaveReqVO);
        return appGraphService.addTripletRel(appGraphRelationshipSaveReqVOS);
    }


}
