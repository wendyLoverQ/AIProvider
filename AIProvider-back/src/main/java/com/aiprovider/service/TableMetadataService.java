package com.aiprovider.service;

import com.aiprovider.repository.TableMetadataRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TableMetadataService {

    private final TableMetadataRepository tableMetadataRepo;

    public TableMetadataService(TableMetadataRepository tableMetadataRepo) {
        this.tableMetadataRepo = tableMetadataRepo;
    }

    public List<Map<String, Object>> listTables() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.TableMetadataService.listTables", new String[] {}, new Object[] {});
    return com.aiprovider.logging.BusinessOperationLogger.success("service.TableMetadataService.listTables", tableMetadataRepo.listTables());
    }

    public List<Map<String, Object>> getColumns(String tableName) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.TableMetadataService.getColumns", new String[] { "tableName" }, new Object[] { tableName });
    return com.aiprovider.logging.BusinessOperationLogger.success("service.TableMetadataService.getColumns", tableMetadataRepo.getColumns(tableName));
    }
}