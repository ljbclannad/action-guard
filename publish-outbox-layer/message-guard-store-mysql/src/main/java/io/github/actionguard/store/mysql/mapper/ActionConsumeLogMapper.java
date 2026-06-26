package io.github.actionguard.store.mysql.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActionConsumeLogMapper {

    int insert(ActionConsumeLogRow row);

    ActionConsumeLogRow selectByMessageId(String messageId);

    int updateOptimistically(ActionConsumeLogRow row);
}
