package io.github.actionguard.store.mysql.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActionOutboxMapper {

    ActionOutboxRow selectById(String id);

    int insert(ActionOutboxRow row);

    int updateOptimistically(ActionOutboxRow row);

    ActionOutboxRow selectByActionInstanceId(String actionInstanceId);
}
