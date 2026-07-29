package com.aiprovider.service;

import com.aiprovider.model.vo.MonitorSummaryVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Service
public class SystemResourceMonitor implements ISystemResourceMonitor {
    private final File diskPath;
    public SystemResourceMonitor(@Value("${monitor.disk-path:.}") String diskPath) { this.diskPath = new File(diskPath); }

    @Override public MonitorSummaryVO.Resource memory() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.SystemResourceMonitor.memory", new String[] {}, new Object[] {});
    try {
            if (Files.isReadable(Paths.get("/proc/meminfo"))) {
                long total = 0, available = 0;
                List<String> lines = Files.readAllLines(Paths.get("/proc/meminfo"));
                for (String line : lines) {
                    if (line.startsWith("MemTotal:")) total = kibibytes(line);
                    else if (line.startsWith("MemAvailable:")) available = kibibytes(line);
                }
                if (total > 0 && available >= 0)
                    return com.aiprovider.logging.BusinessOperationLogger.success("service.SystemResourceMonitor.memory", new MonitorSummaryVO.Resource(Math.max(0, total - available), total, true, null));
            }
            com.sun.management.OperatingSystemMXBean bean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long total = bean.getTotalPhysicalMemorySize();
            long free = bean.getFreePhysicalMemorySize();
            if (total <= 0 || free < 0) throw new IllegalStateException("memory metrics unavailable");
            return com.aiprovider.logging.BusinessOperationLogger.success("service.SystemResourceMonitor.memory", new MonitorSummaryVO.Resource(Math.max(0, total - free), total, true, null));
        } catch (Exception exception) {
            return com.aiprovider.logging.BusinessOperationLogger.success("service.SystemResourceMonitor.memory", new MonitorSummaryVO.Resource(null, null, false, "系统内存数据不可用"));
        }
    }

    private static long kibibytes(String line) {
        String[] parts = line.trim().split("\\s+");
        return parts.length >= 2 ? Long.parseLong(parts[1]) * 1024L : 0L;
    }

    @Override public MonitorSummaryVO.Resource disk() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.SystemResourceMonitor.disk", new String[] {}, new Object[] {});
    try {
            long total = diskPath.getTotalSpace(); long free = diskPath.getUsableSpace();
            if (total <= 0 || free < 0) throw new IllegalStateException("disk metrics unavailable");
            return com.aiprovider.logging.BusinessOperationLogger.success("service.SystemResourceMonitor.disk", new MonitorSummaryVO.Resource(Math.max(0, total - free), total, true, null));
        } catch (Exception exception) {
            return com.aiprovider.logging.BusinessOperationLogger.success("service.SystemResourceMonitor.disk", new MonitorSummaryVO.Resource(null, null, false, "磁盘数据不可用"));
        }
    }
}
