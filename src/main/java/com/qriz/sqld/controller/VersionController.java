package com.qriz.sqld.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qriz.sqld.domain.version.Version;
import com.qriz.sqld.dto.ResponseDto;
import com.qriz.sqld.dto.version.VersionDto;
import com.qriz.sqld.service.version.VersionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class VersionController {

    private final VersionService versionService;

    @GetMapping("/version")
    public ResponseEntity<?> getLatestVersion() {
        Version version = versionService.getLatestVersion();
        VersionDto respDto = new VersionDto(version);
        return new ResponseEntity<>(new ResponseDto<>(1, "버전 불러오기 성공", respDto), HttpStatus.OK);
    }

}
