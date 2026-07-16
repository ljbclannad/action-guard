package io.github.actionguard.store.mysql.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface ActionInstanceMapper {

    ActionInstanceRow selectById(String id);

    ActionInstanceRow selectByActionNameAndBizKey(@Param("actionName") String actionName, @Param("bizKey") String bizKey);

    ActionInstanceRow selectByIdempotencyKey(String idempotencyKey);

    List<ActionInstanceRow> selectByStatusesUpdatedBefore(
            @Param("statuses") List<String> statuses,
            @Param("updatedBeforeOrAt") Timestamp updatedBeforeOrAt,
            @Param("limit") int limit
    );

    int insert(ActionInstanceRow row);

    int updateOptimistically(ActionInstanceRow row);
}
