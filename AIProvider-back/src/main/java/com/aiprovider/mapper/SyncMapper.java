package com.aiprovider.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SyncMapper {

    @Insert("INSERT INTO c_SyncRuns(DeviceId, ReceivedCount, UpsertedCount, FailedCount) " +
            "VALUES(#{deviceId}, #{receivedCount}, #{upsertedCount}, 0)")
    void insertSyncRun(@Param("deviceId") String deviceId,
                       @Param("receivedCount") int receivedCount,
                       @Param("upsertedCount") int upsertedCount);

    @Select("SELECT DeviceId, ReceivedCount, UpsertedCount, FailedCount, CreatedAt " +
            "FROM c_SyncRuns ORDER BY Id DESC LIMIT 20")
    List<Map<String, Object>> recentSyncRuns();

    @Select("SELECT COLUMN_NAME, COLUMN_KEY, DATA_TYPE " +
            "FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = #{tableName} " +
            "ORDER BY ORDINAL_POSITION")
    List<Map<String, Object>> getTableColumns(@Param("tableName") String tableName);

    @Select("${sql}")
    List<Map<String, Object>> executeQuery(@Param("sql") String sql);
}
