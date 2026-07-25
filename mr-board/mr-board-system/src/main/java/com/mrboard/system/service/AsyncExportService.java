package com.mrboard.system.service;

import com.mrboard.system.export.ExportTask;
import com.mrboard.system.export.ExportTaskManager;
import com.mrboard.system.export.ExportTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncExportService {

    private final ReportService reportService;
    private final ExportTaskManager taskManager;

    @Async("exportExecutor")
    public void exportExcelAsync(String taskId) {
        executeExport(taskId, "xlsx", out -> reportService.exportExcel(out));
    }

    @Async("exportExecutor")
    public void exportCsvAsync(String taskId) {
        executeExport(taskId, "csv", out -> reportService.exportCsv(out));
    }

    private void executeExport(String taskId, String ext, ExportConsumer consumer) {
        try {
            taskManager.updateStatus(taskId, ExportTaskStatus.RUNNING);
            Path tempDir = Files.createTempDirectory("mr-export");
            String fileName = taskId + "." + ext;
            File file = tempDir.resolve(fileName).toFile();
            try (OutputStream out = new FileOutputStream(file)) {
                consumer.write(out);
            }
            taskManager.markCompleted(taskId, file.getAbsolutePath());
            log.info("Export task {} completed, file={}", taskId, file.getAbsolutePath());
        } catch (Exception e) {
            log.error("Export task {} failed", taskId, e);
            taskManager.markFailed(taskId, e.getMessage());
        }
    }

    @FunctionalInterface
    interface ExportConsumer {
        void write(OutputStream out) throws Exception;
    }
}
