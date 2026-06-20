package tech.qiantong.qknow.module.kmc.api.service;


import tech.qiantong.qknow.module.kmc.api.kmcCategory.dto.KmcCategoryRespDTO;

import java.util.List;

public interface IKmcCategoryApiService {
    public List<KmcCategoryRespDTO> kmcCategoryList();

}
