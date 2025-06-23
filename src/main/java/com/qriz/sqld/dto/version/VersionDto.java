package com.qriz.sqld.dto.version;

import java.time.LocalDate;

import com.qriz.sqld.domain.version.Version;

import lombok.Getter;

@Getter
public class VersionDto {
    private float versionInfo;
    private String updateInfo;
    private LocalDate date;

    public VersionDto(Version version) {
        this.versionInfo = version.getVersionInfo();
        this.updateInfo = version.getUpdateInfo();
        this.date = version.getDate();
    }
}