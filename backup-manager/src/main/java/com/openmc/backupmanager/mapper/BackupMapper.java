package com.openmc.backupmanager.mapper;

import com.openmc.backupmanager.dto.LatestBackupResponse;
import com.openmc.backupmanager.service.BackupService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BackupMapper {

    @Mapping(target = "available", constant = "true")
    LatestBackupResponse toLatestBackupResponse(BackupService.LatestBackupStatus status);
}
