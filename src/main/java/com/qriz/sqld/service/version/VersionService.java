package com.qriz.sqld.service.version;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.qriz.sqld.domain.version.Version;
import com.qriz.sqld.domain.version.VersionRepository;


@Service
@RequiredArgsConstructor
public class VersionService {
    private final VersionRepository versionRepository;

    // 최신 버전 정보 하나 가져오기 (버전 출시일 기준 최신)
    public Version getLatestVersion() {
        return versionRepository.findTopByOrderByDateDesc()
                .orElseThrow(() -> new RuntimeException("No version info found"));
    }
}