package io.github.actionguard.store.mysql.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface ActionOutboxMapper {

    ActionOutboxRow selectById(String id);

    int insert(ActionOutboxRow row);

    int updateOptimistically(ActionOutboxRow row);

    ActionOutboxRow selectByActionInstanceId(String actionInstanceId);

    List<ActionOutboxRow> selectRecoverable(
            @Param("availableBeforeOrAt") Timestamp availableBeforeOrAt,
            @Param("claimedBeforeOrAt") Timestamp claimedBeforeOrAt,
            @Param("limit") int limit
    );
}
