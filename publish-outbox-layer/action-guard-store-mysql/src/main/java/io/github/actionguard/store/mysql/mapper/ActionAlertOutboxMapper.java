package io.github.actionguard.store.mysql.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface ActionAlertOutboxMapper {
    ActionAlertOutboxRow selectById(String id);

    ActionAlertOutboxRow selectByDedupeKey(String dedupeKey);

    int insert(ActionAlertOutboxRow row);

    int updateOptimistically(ActionAlertOutboxRow row);

    List<ActionAlertOutboxRow> selectRecoverable(
            @Param("availableBeforeOrAt") Timestamp availableBeforeOrAt,
            @Param("claimedBeforeOrAt") Timestamp claimedBeforeOrAt,
            @Param("limit") int limit
    );
}
