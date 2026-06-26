package io.github.actionguard.store.mysql.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActionInstanceMapper {

    ActionInstanceRow selectById(String id);

    ActionInstanceRow selectByActionNameAndBizKey(@Param("actionName") String actionName, @Param("bizKey") String bizKey);

    int insert(ActionInstanceRow row);

    int updateOptimistically(ActionInstanceRow row);
}
