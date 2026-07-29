package com.aiprovider.service;

import com.aiprovider.model.vo.PageResultVO;
import com.aiprovider.repository.TableDataRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TableDataService {

    private final TableDataRepository tableDataRepo;

    public TableDataService(TableDataRepository tableDataRepo) {
        this.tableDataRepo = tableDataRepo;
    }

    public Map<String, Object> getData(String tableName, int page, int size) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.TableDataService.getData", new String[] { "tableName", "page", "size" }, new Object[] { tableName, page, size });
    if (!tableDataRepo.isValidTable(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        long total = tableDataRepo.countByTable(tableName);
        page = Math.max(0, page);
        size = Math.max(1, Math.min(200, size));

        List<Map<String, Object>> rows = tableDataRepo.findPage(tableName, page, size);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", tableName);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("rows", rows);
        return com.aiprovider.logging.BusinessOperationLogger.success("service.TableDataService.getData", result);
    }

    public Map<String, Object> getCount(String tableName) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.TableDataService.getCount", new String[] { "tableName" }, new Object[] { tableName });
    if (!tableDataRepo.isValidTable(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        long count = tableDataRepo.countByTable(tableName);
        return com.aiprovider.logging.BusinessOperationLogger.success("service.TableDataService.getCount", new LinkedHashMap<String, Object>() {{
            put("table", tableName); put("count", count);
        }});
    }
}