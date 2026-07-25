package com.mrboard.system.sync.dto;

import lombok.Data;

@Data
public class ChangeDTO {
    private String oldPath;
    private String newPath;
    private String status;       // added / modified / deleted / renamed
    private Integer additions;
    private Integer deletions;
    private Boolean newFile;
    private Boolean renamedFile;
    private Boolean deletedFile;
    private String diff;
}
