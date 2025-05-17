package com.qriz.sqld.service.daily;

import com.qriz.sqld.domain.question.Question;
import com.qriz.sqld.domain.question.QuestionRepository;
import com.qriz.sqld.domain.question.option.Option;
import com.qriz.sqld.domain.question.option.OptionRepository;
import com.qriz.sqld.domain.skill.Skill;
import com.qriz.sqld.domain.user.User;
import com.qriz.sqld.domain.user.UserRepository;
import com.qriz.sqld.domain.UserActivity.UserActivity;
import com.qriz.sqld.domain.UserActivity.UserActivityRepository;
import com.qriz.sqld.domain.clip.ClipRepository;
import com.qriz.sqld.domain.clip.Clipped;
import com.qriz.sqld.domain.daily.UserDaily;
import com.qriz.sqld.domain.daily.UserDailyRepository;
import com.qriz.sqld.dto.daily.ResultDetailDto;
import com.qriz.sqld.dto.daily.DailyScoreDto;
import com.qriz.sqld.dto.daily.DaySubjectDetailsDto;
import com.qriz.sqld.dto.daily.UserDailyDto;
import com.qriz.sqld.dto.daily.UserDailyDto.DailyDetailAndStatusDto;
import com.qriz.sqld.dto.daily.WeeklyTestResultDto;
import com.qriz.sqld.dto.test.TestReqDto;
import com.qriz.sqld.dto.test.TestRespDto;
import com.qriz.sqld.handler.ex.CustomApiException;
import com.qriz.sqld.util.WeekendPlanUtil;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class DailyService {

        private final QuestionRepository questionRepository;
        private final UserActivityRepository userActivityRepository;
        private final UserRepository userRepository;
        private final UserDailyRepository userDailyRepository;
        private final ClipRepository clipRepository;
        private final DailyPlanService dailyPlanService;
        private final DKTService dktService;
        private final OptionRepository optionRepository;

        private final Logger log = LoggerFactory.getLogger(DailyService.class);

        @Lazy
        @Autowired
        private final WeekendPlanUtil weekendPlanUtil;

        /**
         * 오늘의 데일리 테스트 문제를 가져오기
         */
        @Transactional
        public List<TestRespDto.DailyRespDto> getDailyTestQuestionsByDay(Long userId, String dayNumber) {
                UserDaily userDaily = userDailyRepository.findByUserIdAndDayNumber(userId, dayNumber)
                                .orElseThrow(() -> new CustomApiException("해당 일자의 데일리 플랜을 찾을 수 없습니다."));

                if (!dailyPlanService.canAccessDay(userId, userDaily.getDayNumber())) {
                        throw new CustomApiException("이 테스트에 아직 접근할 수 없습니다.");
                }

                if (userDaily.isPassed() || (userDaily.getAttemptCount() > 0 && !userDaily.isRetestEligible())) {
                        throw new CustomApiException("이미 완료된 테스트이거나 재시험 자격이 없습니다.");
                }

                // 첫 시도와 재시험 모두 동일한 로직으로 처리
                List<Question> questions;
                if (userDaily.getPlannedSkills() == null) {
                        questions = getWeekFourQuestions(userId, userDaily);
                } else if (userDaily.isReviewDay()) {
                        questions = weekendPlanUtil.getWeekendQuestions(userId, userDaily);
                } else {
                        questions = getRegularDayQuestions(userDaily);
                }

                // 모든 경우에 랜덤화된 선택지만 반환하도록 통일
                return questions.stream()
                                .map(q -> new TestRespDto.DailyRespDto(q, Objects.hash(q.getId(), userId, dayNumber)))
                                .collect(Collectors.toList());

        }

        private List<Question> getWeekFourQuestions(Long userId, UserDaily todayPlan) {
                LocalDateTime startDateTime = todayPlan.getPlanDate().minusWeeks(3).atStartOfDay();
                LocalDateTime endDateTime = todayPlan.getPlanDate().atTime(23, 59, 59);
                List<UserActivity> activities = userActivityRepository.findByUserIdAndDateBetween(
                                userId, startDateTime, endDateTime);
                List<Double> predictions = dktService.getPredictions(userId, activities);
                return getQuestionsBasedOnPredictions(predictions);
        }

        private List<Question> getRegularDayQuestions(UserDaily todayPlan) {
                return questionRepository.findRandomQuestionsBySkillsAndCategory(
                                todayPlan.getPlannedSkills(),
                                2, // 데일리 카테고리 값
                                20 // 문제 수
                );
        }

        private List<Question> getQuestionsBasedOnPredictions(List<Double> predictions) {
                List<Long> sortedSkillIds = IntStream.range(0, predictions.size())
                                .boxed()
                                .sorted(Comparator.comparingDouble(predictions::get))
                                .map(Long::valueOf)
                                .limit(5)
                                .collect(Collectors.toList());
                return questionRepository.findRandomQuestionsBySkillIdsAndCategory(sortedSkillIds, 2, 10);
        }

        /**
         * 데일리 테스트 제출 처리
         */
        @Transactional
        public List<TestRespDto.TestSubmitRespDto> processDailyTestSubmission(Long userId, String dayNumber,
                        TestReqDto testSubmitReqDto) {
                // 1) 활성화된 플랜만 조회
                UserDaily userDaily = userDailyRepository
                                .findByUserIdAndDayNumberAndIsArchivedFalse(userId, dayNumber)
                                .orElseThrow(() -> new CustomApiException("해당 일자의 활성화된 데일리 플랜을 찾을 수 없습니다."));

                // 2) 첫 시도 실패 후 재시험이라면, 기존 UserActivity 기록 삭제
                if (userDaily.getAttemptCount() > 0) {
                        userActivityRepository.deleteByUserIdAndTestInfo(userId, dayNumber);
                }

                // 3) 통과 여부 및 재시험 자격 체크
                if (userDaily.isPassed() || (userDaily.getAttemptCount() > 0 && !userDaily.isRetestEligible())) {
                        throw new CustomApiException("이미 완료된 테스트이거나 재시험 자격이 없습니다.");
                }

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new CustomApiException("사용자를 찾을 수 없습니다."));

                List<TestRespDto.TestSubmitRespDto> results = new ArrayList<>();

                // 4) 새로운 활동 저장
                for (TestReqDto.TestSubmitReqDto activity : testSubmitReqDto.getActivities()) {
                        Question question = questionRepository.findById(activity.getQuestion().getQuestionId())
                                        .orElseThrow(() -> new CustomApiException("문제를 찾을 수 없습니다."));

                        Long optionId = activity.getOptionId();
                        boolean isCorrect = false;

                        if (optionId != null) {
                                Option submittedOption = optionRepository.findById(optionId)
                                                .orElseThrow(() -> new CustomApiException("선택한 옵션을 찾을 수 없습니다."));
                                if (!submittedOption.getQuestion().getId().equals(question.getId())) {
                                        throw new CustomApiException("선택한 옵션이 해당 문제와 일치하지 않습니다.");
                                }
                                isCorrect = submittedOption.isAnswer();
                        }

                        UserActivity userActivity = new UserActivity();
                        userActivity.setUser(user);
                        userActivity.setQuestion(question);
                        userActivity.setTestInfo(dayNumber);
                        userActivity.setQuestionNum(activity.getQuestionNum());
                        userActivity.setChecked(optionId);
                        userActivity.setTimeSpent(activity.getTimeSpent());
                        userActivity.setCorrection(isCorrect);
                        userActivity.setScore(isCorrect ? getPointsForDifficulty(question.getDifficulty()) : 0.0);
                        userActivity.setDate(LocalDateTime.now());
                        userActivity.setUserDaily(userDaily);

                        userActivityRepository.save(userActivity);

                        results.add(new TestRespDto.TestSubmitRespDto(
                                        userActivity.getId(),
                                        userId,
                                        new TestRespDto.TestSubmitRespDto.QuestionRespDto(
                                                        question.getId(),
                                                        getCategoryName(question.getCategory())),
                                        activity.getQuestionNum(),
                                        optionId != null ? String.valueOf(optionId) : null,
                                        activity.getTimeSpent(),
                                        isCorrect));
                }

                // 5) 점수 집계 및 플랜 상태 업데이트 (기존 로직 유지)
                double totalPossibleScore = testSubmitReqDto.getActivities().stream()
                                .mapToDouble(act -> {
                                        Question q = questionRepository.findById(act.getQuestion().getQuestionId())
                                                        .orElseThrow(() -> new CustomApiException("문제를 찾을 수 없습니다."));
                                        return getPointsForDifficulty(q.getDifficulty());
                                }).sum();

                double userScore = testSubmitReqDto.getActivities().stream()
                                .mapToDouble(act -> {
                                        Long optId = act.getOptionId();
                                        if (optId == null)
                                                return 0.0;
                                        Question q = questionRepository.findById(act.getQuestion().getQuestionId())
                                                        .orElseThrow(() -> new CustomApiException("문제를 찾을 수 없습니다."));
                                        Option o = optionRepository.findById(optId)
                                                        .orElseThrow(() -> new CustomApiException(
                                                                        "선택한 옵션을 찾을 수 없습니다."));
                                        return o.isAnswer() ? getPointsForDifficulty(q.getDifficulty()) : 0.0;
                                }).sum();

                boolean isPassed = userScore >= totalPossibleScore * 0.7;
                userDaily.updateTestStatus(isPassed);
                if (isPassed) {
                        userDaily.setPassed(true);
                        userDaily.setRetestEligible(false);
                } else if (userDaily.getAttemptCount() >= 2) {
                        userDaily.setRetestEligible(false);
                }
                userDailyRepository.save(userDaily);

                // 6) 통과 시 클립 저장, 주말 플랜 업데이트 등 (기존 로직)
                if (isPassed || userDaily.getAttemptCount() >= 2) {
                        for (TestRespDto.TestSubmitRespDto result : results) {
                                UserActivity ua = userActivityRepository.findById(result.getActivityId())
                                                .orElseThrow(() -> new CustomApiException("UserActivity를 찾을 수 없습니다."));
                                Clipped clipped = new Clipped();
                                clipped.setUserActivity(ua);
                                clipped.setDate(LocalDateTime.now());
                                clipRepository.save(clipped);
                        }
                }
                int day = Integer.parseInt(dayNumber.replace("Day", ""));
                if (day % 7 == 5 && day <= 19) {
                        dailyPlanService.updateWeekendPlan(userId, day);
                }

                return results;
        }

        private int getPointsForDifficulty(Integer difficulty) {
                return 5;
        }

        private String getCategoryName(int category) {
                switch (category) {
                        case 1:
                                return "진단";
                        case 2:
                                return "데일리";
                        case 3:
                                return "모의고사";
                        default:
                                return "알 수 없음";
                }
        }

        /**
         * 오늘의 공부 결과 - 문제 상세보기
         * 수정: ResultDetailDto.from() 메서드를 사용하여 Option 엔티티 기반 정보를 반영
         */
        @Transactional(readOnly = true)
        public ResultDetailDto getDailyResultDetail(Long userId, String dayNumber, Long questionId) {
                log.info("Getting daily result detail for userId: {}, dayNumber: {}, questionId: {}",
                                userId, dayNumber, questionId);
                String testInfo = dayNumber;
                UserActivity userActivity = userActivityRepository
                                .findByUserIdAndTestInfoAndQuestionId(userId, testInfo, questionId)
                                .orElseThrow(() -> new CustomApiException("해당 문제의 풀이 결과를 찾을 수 없습니다."));
                Question question = userActivity.getQuestion();
                // ResultDetailDto.from() 내부에서 getSortedOptions()를 사용하여 변경된 구조를 반영
                ResultDetailDto result = ResultDetailDto.from(question, userActivity);
                return result;
        }

        /**
         * 특정 Day 가 포함된 주의 과목별 테스트 결과 점수
         * 
         * @param userId
         * @param dayNumber
         * @return
         */
        public WeeklyTestResultDto getDetailedWeeklyTestResult(Long userId, String dayNumber) {
                log.info("Starting getDetailedWeeklyTestResult for userId: {} and dayNumber: {}", userId, dayNumber);

                UserDaily currentDaily = userDailyRepository.findByUserIdAndDayNumber(userId, dayNumber)
                                .orElseThrow(() -> new CustomApiException("Daily plan not found"));

                LocalDate startDate = currentDaily.getPlanDate().with(DayOfWeek.MONDAY);
                LocalDate endDate = startDate.plusDays(6);

                log.info("Fetching activities between {} and {}", startDate, endDate);
                List<UserActivity> activities = userActivityRepository.findByUserIdAndDateBetween(
                                userId, startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX));

                Map<String, DailyScoreDto> dailyScores = new HashMap<>();

                for (UserActivity activity : activities) {
                        log.debug("Processing activity: {}", activity.getId());

                        UserDaily daily = userDailyRepository
                                        .findByUserIdAndPlanDate(userId, activity.getDate().toLocalDate())
                                        .orElseThrow(() -> new CustomApiException(
                                                        "Daily plan not found for date: "
                                                                        + activity.getDate().toLocalDate()));

                        String dayNum = daily.getDayNumber();

                        Optional.ofNullable(activity.getQuestion())
                                        .map(Question::getSkill)
                                        .ifPresentOrElse(
                                                        skill -> {
                                                                log.debug("Adding score for skill: {}",
                                                                                skill.getTitle());
                                                                dailyScores.computeIfAbsent(dayNum,
                                                                                k -> new DailyScoreDto())
                                                                                .addScore(skill.getTitle(),
                                                                                                activity.getScore());
                                                        },
                                                        () -> log.warn("Question or Skill is null for activity: {}",
                                                                        activity.getId()));
                }

                log.info("Completed processing for getDetailedWeeklyTestResult");
                return new WeeklyTestResultDto(dailyScores);
        }

        @Transactional(readOnly = true)
        public DaySubjectDetailsDto.Response getDaySubjectDetails(Long userId, String dayNumber) {
                // 1) UserDaily 조회
                UserDaily userDaily = userDailyRepository
                                .findByUserIdAndDayNumberAndIsArchivedFalse(userId, dayNumber)
                                .orElseThrow(() -> new CustomApiException("해당 일자의 데일리 플랜을 찾을 수 없습니다."));
                boolean passed = userDaily.isPassed();
                boolean reviewDay = userDaily.isReviewDay();
                boolean comprehensiveReviewDay = userDaily.isComprehensiveReviewDay();

                // 2) 활동(Activity) 전체 조회
                List<UserActivity> activities = userActivityRepository.findByUserIdAndTestInfo(userId, dayNumber);

                // 3) skillId 별 점수 누적용 Map
                Map<Long, Double> itemMap = new LinkedHashMap<>();
                List<DaySubjectDetailsDto.DailyResultDto> dailyResults = new ArrayList<>();

                for (UserActivity activity : activities) {
                        Long skillId = activity.getQuestion().getSkill().getId();
                        double score = activity.isCorrection()
                                        ? getPointsForDifficulty(activity.getQuestion().getDifficulty())
                                        : 0.0;
                        itemMap.merge(skillId, score, Double::sum);

                        dailyResults.add(new DaySubjectDetailsDto.DailyResultDto(
                                        activity.getQuestion().getId(),
                                        activity.getQuestion().getSkill().getKeyConcepts(),
                                        activity.getQuestion().getQuestion(),
                                        activity.isCorrection()));
                }

                // 4) Map<Long,Double> → List<SubItemDto>
                List<DaySubjectDetailsDto.SubItemDto> subItems = itemMap.entrySet().stream()
                                .map(e -> new DaySubjectDetailsDto.SubItemDto(e.getKey(), e.getValue()))
                                .collect(Collectors.toList());

                // 5) Response 생성
                return new DaySubjectDetailsDto.Response(
                                dayNumber,
                                passed,
                                reviewDay,
                                comprehensiveReviewDay,
                                subItems,
                                dailyResults);
        }

        @Transactional(readOnly = true)
        public DailyDetailAndStatusDto getDailyDetailWithStatus(Long userId, String dayNumber) {
                // 해당 일자의 daily plan 조회
                UserDaily userDaily = userDailyRepository.findByUserIdAndDayNumber(userId, dayNumber)
                                .orElseThrow(() -> new CustomApiException("해당 일자의 데일리 플랜을 찾을 수 없습니다."));

                // skills 정보 구성
                List<DailyDetailAndStatusDto.SkillDetailDto> skills = userDaily.getPlannedSkills().stream()
                                .map(skill -> DailyDetailAndStatusDto.SkillDetailDto.builder()
                                                .id(skill.getId())
                                                .keyConcepts(skill.getKeyConcepts())
                                                .description(skill.getDescription())
                                                .build())
                                .collect(Collectors.toList());

                // UserActivity를 통한 총 점수 계산
                List<UserActivity> activities = userActivityRepository.findByUserIdAndTestInfo(userId, dayNumber);
                double totalScore;
                if (activities.isEmpty()) {
                        totalScore = 0.0;
                } else {
                        totalScore = activities.stream()
                                        .mapToDouble(a -> a.getScore() != null ? a.getScore() : 0.0)
                                        .sum();
                }

                // 기존 testStatus 정보 구성
                int attemptCount = userDaily.getAttemptCount();
                boolean passed = userDaily.isPassed();
                boolean retestEligible = userDaily.isRetestEligible();

                // 추가: 진행 가능 여부 계산
                boolean available = false;
                if ("Day1".equals(dayNumber)) {
                        available = true; // 첫 날은 항상 진행 가능
                } else {
                        // 이전 Day 계산 (예: "Day2" → "Day1")
                        int currentDayNum = Integer.parseInt(dayNumber.replace("Day", ""));
                        String previousDay = "Day" + (currentDayNum - 1);
                        UserDaily previousDaily = userDailyRepository.findByUserIdAndDayNumber(userId, previousDay)
                                        .orElse(null);
                        if (previousDaily != null && previousDaily.getCompletionDate() != null) {
                                // 이전 Day가 완료되었고, 오늘의 계획일자가 이전 완료일 다음 날(또는 이후)이어야 진행 가능
                                LocalDate allowedDate = previousDaily.getCompletionDate().plusDays(1);
                                available = !userDaily.getPlanDate().isBefore(allowedDate);
                        }
                }

                DailyDetailAndStatusDto.StatusDto status = DailyDetailAndStatusDto.StatusDto.builder()
                                .attemptCount(attemptCount)
                                .passed(passed)
                                .retestEligible(retestEligible)
                                .totalScore(totalScore)
                                .available(available)
                                .build();

                return DailyDetailAndStatusDto.builder()
                                .dayNumber(userDaily.getDayNumber())
                                .skills(skills)
                                .status(status)
                                .build();
        }

        @Transactional(readOnly = true)
        public List<UserDailyDto.DailySkillDto> getDailyConcepts(Long userId, String dayNumber) {
                // 해당 사용자의 daily plan 조회
                UserDaily userDaily = userDailyRepository.findByUserIdAndDayNumber(userId, dayNumber)
                                .orElseThrow(() -> new CustomApiException("해당 일자의 데일리 플랜을 찾을 수 없습니다."));

                List<Skill> skills = userDaily.getPlannedSkills();
                if (skills == null || skills.isEmpty()) {
                        throw new CustomApiException("해당 일자에 배정된 개념이 없습니다.");
                }

                // plannedSkills에 배정된 모든 스킬을 DTO로 변환하여 리스트로 반환
                return skills.stream().map(skill -> {
                        UserDailyDto.DailySkillDto dto = new UserDailyDto.DailySkillDto();
                        dto.setSkillId(skill.getId());
                        dto.setKeyConcepts(skill.getKeyConcepts());
                        dto.setDescription(skill.getDescription());
                        return dto;
                }).collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public List<DaySubjectDetailsDto.DailySubjectDetails> getWeeklyReviewBySubject(
                        Long userId, String dayNumber, String subjectParam) {

                // 1) 영어 코드를 한글 과목명으로 매핑 (파라미터가 없으면 null)
                final String mappedSubject = (subjectParam != null && !subjectParam.isBlank())
                                ? mapSubject(subjectParam)
                                : null;

                // 2) 이번 주 월~일 활동 조회
                UserDaily current = userDailyRepository
                                .findByUserIdAndDayNumberAndIsArchivedFalse(userId, dayNumber)
                                .orElseThrow(() -> new CustomApiException("해당 일자의 플랜이 없습니다."));
                LocalDate weekStart = current.getPlanDate().with(DayOfWeek.MONDAY);
                LocalDate weekEnd = weekStart.plusDays(6);

                List<UserActivity> acts = userActivityRepository
                                .findByUserIdAndDateBetween(
                                                userId,
                                                weekStart.atStartOfDay(),
                                                weekEnd.atTime(LocalTime.MAX));
                Set<String> subjectsInActs = acts.stream()
                                .map(ua -> ua.getQuestion().getSkill().getTitle())
                                .collect(Collectors.toSet());
                log.info("🔍 이번 주 활동에 포함된 과목 코드들: {}", subjectsInActs);

                // 3) mappedSubject(한글) 이 있으면, Skill.title(한글)과 비교해서 필터링
                if (mappedSubject != null) {
                        acts = acts.stream()
                                        .filter(ua -> mappedSubject.equals(
                                                        ua.getQuestion().getSkill().getTitle()))
                                        .collect(Collectors.toList());
                }

                // 4) 과목별 그룹핑 (Skill.title 기준, 한글 과목명)
                Map<String, List<UserActivity>> bySubject = acts.stream()
                                .collect(Collectors.groupingBy(
                                                ua -> ua.getQuestion().getSkill().getTitle(),
                                                LinkedHashMap::new,
                                                Collectors.toList()));

                List<DaySubjectDetailsDto.DailySubjectDetails> result = new ArrayList<>();
                for (Map.Entry<String, List<UserActivity>> e : bySubject.entrySet()) {
                        String subjectName = e.getKey(); // “1과목” or “2과목”
                        List<UserActivity> subActs = e.getValue();

                        // 5) majorItem → subItem 점수 누적
                        Map<String, Map<String, Double>> majorMap = new LinkedHashMap<>();
                        for (UserActivity ua : subActs) {
                                Question q = ua.getQuestion();
                                Skill skill = q.getSkill();
                                String major = skill.getType();
                                String subItem = skill.getKeyConcepts();
                                int difficulty = q.getDifficulty() != null ? q.getDifficulty() : 0;
                                double pts = ua.isCorrection() ? getPointsForDifficulty(difficulty) : 0.0;

                                majorMap
                                                .computeIfAbsent(major, k -> new LinkedHashMap<>())
                                                .merge(subItem, pts, Double::sum);
                        }

                        // 6) DTO 변환
                        List<DaySubjectDetailsDto.DailySubjectDetails.MajorItemDetail> majors = majorMap.entrySet()
                                        .stream()
                                        .map(me -> {
                                                double majorScore = me.getValue().values().stream()
                                                                .mapToDouble(Double::doubleValue).sum();
                                                List<DaySubjectDetailsDto.DailySubjectDetails.SubItemScore> subList = me
                                                                .getValue().entrySet().stream()
                                                                .map(se -> new DaySubjectDetailsDto.DailySubjectDetails.SubItemScore(
                                                                                se.getKey(), se.getValue()))
                                                                .collect(Collectors.toList());
                                                return new DaySubjectDetailsDto.DailySubjectDetails.MajorItemDetail(
                                                                me.getKey(), majorScore, subList);
                                        })
                                        .collect(Collectors.toList());

                        double totalScore = majors.stream()
                                        .mapToDouble(DaySubjectDetailsDto.DailySubjectDetails.MajorItemDetail::getScore)
                                        .sum();

                        // 7) title: 파라미터 없으면 순서대로 “1과목”, “2과목”… 있으면 mappedSubject
                        String title = (mappedSubject == null)
                                        ? subjectName
                                        : mappedSubject;

                        result.add(new DaySubjectDetailsDto.DailySubjectDetails(
                                        title, totalScore, majors));
                }

                return result;
        }

        /**
         * 영어 코드(subject1/subject2) → 한글 과목명("1과목"/"2과목") 매핑
         */
        private String mapSubject(String subjectCode) {
                if ("subject1".equalsIgnoreCase(subjectCode)) {
                        return "1과목";
                } else if ("subject2".equalsIgnoreCase(subjectCode)) {
                        return "2과목";
                } else {
                        throw new CustomApiException("Unsupported subject: " + subjectCode);
                }
        }

        // 테스트용
        @Transactional
        public void completeDailyTest(Long userId, String dayNumber) {
                log.info("Completing daily test for user {} and day {}", userId, dayNumber);
                dailyPlanService.completeDailyPlan(userId, dayNumber);

                if (dayNumber.equals("Day21")) {
                        log.info("Day21 completed. Updating week four plan.");
                        dailyPlanService.updateWeekFourPlan(userId);
                }
                log.info("Completed daily test for user {} and day {}", userId, dayNumber);
        }
}