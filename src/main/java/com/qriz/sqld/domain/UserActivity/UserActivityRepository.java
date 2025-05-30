package com.qriz.sqld.domain.UserActivity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.qriz.sqld.domain.exam.UserExamSession;
import com.qriz.sqld.domain.user.User;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {
        List<UserActivity> findByUserId(Long userId);

        List<UserActivity> findByUserIdAndTestInfo(Long userId, String testInfo);

        Optional<UserActivity> findByIdAndUserId(Long activityId, Long userId);

        boolean existsByUserIdAndQuestionCategory(Long userId, int category);

        List<UserActivity> findByUserIdAndQuestionCategory(Long userId, int category);

        Optional<UserActivity> findLatestDailyByUserId(Long userId);

        boolean existsByUserIdAndTestInfo(Long userId, String testInfo);

        List<UserActivity> findByUserIdAndDateBetween(Long userId, LocalDateTime startDate, LocalDateTime endDate);

        Optional<UserActivity> findByUserIdAndTestInfoAndQuestionId(Long userId, String testInfo, Long questionId);

        List<UserActivity> findByUserIdAndTestInfoBetween(Long userId, String startTestInfo, String endTestInfo);

        List<UserActivity> findByUserIdAndTestInfoOrderByQuestionNumAsc(Long userId, String testInfo);

        List<UserActivity> findByUserIdAndTestInfoAndDateAfter(Long userId, String testInfo, LocalDateTime date);

        @Modifying
        @Query("DELETE FROM UserActivity ua WHERE ua.user = :user")
        void deleteByUser(@Param("user") User user);

        @Query("SELECT ua FROM UserActivity ua WHERE ua.user.id = :userId AND ua.testInfo = :testInfo " +
                        "AND ua.date >= :startDate AND ua.date < :endDate " +
                        "ORDER BY ua.date DESC")
        List<UserActivity> findByUserIdAndTestInfoAndDate(
                        @Param("userId") Long userId,
                        @Param("testInfo") String testInfo,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        List<UserActivity> findByExamSession(UserExamSession examSession);

        void deleteByExamSession(UserExamSession examSession);

        // 프리뷰 테스트 삭제용
        void deleteByUserIdAndTestInfo(Long userId, String testInfo);
}
