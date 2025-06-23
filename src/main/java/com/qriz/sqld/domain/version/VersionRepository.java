package com.qriz.sqld.domain.version;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionRepository extends JpaRepository<Version, Long> {
    Optional<Version> findTopByOrderByDateDesc();
}
