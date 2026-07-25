package com.mrboard.system.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class MrExportDTO {

    @ExcelProperty("MR标题")
    private String title;

    @ExcelProperty("项目")
    private String projectName;

    @ExcelProperty("作者")
    private String authorName;

    @ExcelProperty("源分支")
    private String sourceBranch;

    @ExcelProperty("目标分支")
    private String targetBranch;

    @ExcelProperty("看板状态")
    private String boardStatus;

    @ExcelProperty("CI状态")
    private String ciStatus;

    @ExcelProperty("是否有冲突")
    private String hasConflict;

    @ExcelProperty("创建时间")
    private String createdAt;

    @ExcelProperty("更新时间")
    private String updatedAt;

    @ExcelProperty("合并时间")
    private String mergedAt;

    @ExcelProperty("Web链接")
    private String webUrl;
}
