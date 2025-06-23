package com.qriz.sqld.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qriz.sqld.domain.version.Version;
import com.qriz.sqld.dto.version.VersionDto;
import com.qriz.sqld.service.version.VersionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class VersionController {

    private final VersionService versionService;

    @GetMapping("/version")
    public VersionDto getLatestVersion() {
        Version version = versionService.getLatestVersion();
        return new VersionDto(version);
    }

}
