package io.github.actionguard.store.mysql.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ActionStepInstanceMapper {

    ActionStepInstanceRow selectById(String id);

    int insert(ActionStepInstanceRow row);

    int updateOptimistically(ActionStepInstanceRow row);

    List<ActionStepInstanceRow> selectByActionInstanceId(String actionInstanceId);
}
