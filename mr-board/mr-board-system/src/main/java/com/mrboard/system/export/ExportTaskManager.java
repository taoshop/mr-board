package com.mrboard.system.export;

import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExportTaskManager {

    private final Map<String, ExportTask> tasks = new ConcurrentHashMap<>();

    public ExportTask createTask(String type) {
        ExportTask task = new ExportTask();
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        task.setType(type);
        task.setStatus(ExportTaskStatus.PENDING);
        task.setCreatedAt(LocalDateTime.now());
        tasks.put(task.getId(), task);
        return task;
    }

    public ExportTask getTask(String id) {
        return tasks.get(id);
    }

    public void updateStatus(String id, ExportTaskStatus status) {
        ExportTask task = tasks.get(id);
        if (task != null) {
            task.setStatus(status);
        }
    }

    public void markCompleted(String id, String filePath) {
        ExportTask task = tasks.get(id);
        if (task != null) {
            task.setStatus(ExportTaskStatus.COMPLETED);
            task.setFilePath(filePath);
            task.setCompletedAt(LocalDateTime.now());
        }
    }

    public void markFailed(String id, String errorMsg) {
        ExportTask task = tasks.get(id);
        if (task != null) {
            task.setStatus(ExportTaskStatus.FAILED);
            task.setErrorMsg(errorMsg);
            task.setCompletedAt(LocalDateTime.now());
        }
    }

    public void cleanupOldTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        tasks.entrySet().removeIf(entry -> {
            ExportTask t = entry.getValue();
            boolean old = t.getCreatedAt().isBefore(cutoff);
            if (old && t.getFilePath() != null) {
                new File(t.getFilePath()).delete();
            }
            return old;
        });
    }
}
