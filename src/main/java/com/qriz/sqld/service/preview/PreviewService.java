package com.qriz.sqld.service.preview;

import com.qriz.sqld.domain.question.Question;
import com.qriz.sqld.domain.question.QuestionRepository;
import com.qriz.sqld.domain.question.option.Option;
import com.qriz.sqld.domain.question.option.OptionRepository;
import com.qriz.sqld.domain.UserActivity.UserActivity;
import com.qriz.sqld.domain.UserActivity.UserActivityRepository;
import com.qriz.sqld.domain.preview.PreviewTestStatus;
import com.qriz.sqld.domain.preview.UserPreviewTest;
import com.qriz.sqld.domain.skill.Skill;
import com.qriz.sqld.domain.skill.SkillRepository;
import com.qriz.sqld.domain.skillLevel.SkillLevel;
import com.qriz.sqld.domain.skillLevel.SkillLevelRepository;
import com.qriz.sqld.domain.survey.Survey;
import com.qriz.sqld.domain.survey.SurveyRepository;
import com.qriz.sqld.domain.user.User;
import com.qriz.sqld.domain.user.UserRepository;
import com.qriz.sqld.dto.exam.ExamReqDto;
import com.qriz.sqld.dto.preview.PreviewTestResult;
import com.qriz.sqld.dto.preview.QuestionDto;
import com.qriz.sqld.dto.preview.ResultDto;
import com.qriz.sqld.handler.ex.CustomApiException;
import com.qriz.sqld.service.daily.DailyPlanService;
import com.qriz.sqld.domain.preview.UserPreviewTestRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class PreviewService {

    private final QuestionRepository questionRepository;
    private final UserActivityRepository userActivityRepository;
    private final UserPreviewTestRepository userPreviewTestRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final DailyPlanService dailyPlanService;
    private final SkillLevelRepository skillLevelRepository;
    private final SurveyRepository surveyRepository;
    private final OptionRepository optionRepository;

    @Transactional
    public PreviewTestResult getPreviewTestQuestions(User user) {
        if (userPreviewTestRepository.existsByUserAndCompleted(user, true)) {
            throw new RuntimeException("User has already completed the preview test");
        }

        List<Skill> selectedSkills = surveyRepository.findByUserAndCheckedTrue(user)
                .stream()
                .map(Survey::getSkill)
                .collect(Collectors.toList());

        List<Question> questions;
        if (selectedSkills.isEmpty()) {
            questions = getRandomPreviewQuestions(21);
        } else {
            List<Long> skillIds = selectedSkills.stream()
                    .map(Skill::getId)
                    .collect(Collectors.toList());
            questions = getStratifiedRandomQuestions(skillIds, 1, 21);
        }

        long seed = System.currentTimeMillis();
        List<QuestionDto> questionDtos = questions.stream()
                .map(q -> new QuestionDto(q, seed))
                .collect(Collectors.toList());

        int totalTimeLimit = questions.stream()
                .mapToInt(Question::getTimeLimit)
                .sum();

        return new PreviewTestResult(questionDtos, totalTimeLimit);
    }

    private List<Question> getRandomPreviewQuestions(int totalQuestions) {
        List<Long> allSkillIds = questionRepository.findAllSkillIds();
        return getRandomQuestions(allSkillIds, 1, totalQuestions);
    }

    private List<Question> getRandomQuestions(List<Long> skillIds, int category, int sampleSize) {
        long total = questionRepository.countBySkillIdInAndCategory(skillIds, category);
        if (total <= sampleSize) {
            return questionRepository
                    .findBySkillIdInAndCategory(skillIds, category, Pageable.unpaged())
                    .getContent();
        }
        int maxPage = (int) Math.ceil((double) total / sampleSize);
        int page = new Random().nextInt(maxPage);
        PageRequest pr = PageRequest.of(page, sampleSize);
        return questionRepository
                .findBySkillIdInAndCategory(skillIds, category, pr)
                .getContent();
    }

    private List<Question> getStratifiedRandomQuestions(List<Long> skillIds, int category, int totalQuestions) {
        int base = totalQuestions / skillIds.size();
        int rem = totalQuestions % skillIds.size();
        List<Question> result = new ArrayList<>();

        for (Long sid : skillIds) {
            int quota = base + (rem-- > 0 ? 1 : 0);
            if (quota <= 0)
                continue;

            long count = questionRepository.countBySkillIdInAndCategory(
                    Collections.singletonList(sid), category);
            if (count == 0)
                continue;

            int pages = (int) Math.ceil((double) count / quota);
            int page = new Random().nextInt(pages);
            PageRequest pr = PageRequest.of(page, quota);

            result.addAll(questionRepository
                    .findBySkillIdInAndCategory(
                            Collections.singletonList(sid), category, pr)
                    .getContent());
        }

        int need = totalQuestions - result.size();
        if (need > 0) {
            result.addAll(getRandomQuestions(skillIds, category, need));
        }

        Collections.shuffle(result);
        return result.subList(0, totalQuestions);
    }

    @Transactional
    public void processPreviewResults(Long userId, List<ExamReqDto.ExamSubmitReqDto> activities) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 프리뷰 테스트 완료 상태 업데이트
        user.updatePreviewTestStatus(PreviewTestStatus.PREVIEW_COMPLETED);
        userRepository.save(user);

        // 활동들을 스킬별로 그룹화
        Map<Long, List<ExamReqDto.ExamSubmitReqDto>> activityBySkill = activities.stream()
                .collect(Collectors.groupingBy(activity -> {
                    Question q = questionRepository.findById(activity.getQuestion().getQuestionId())
                            .orElseThrow(() -> new RuntimeException("Question not found"));
                    return q.getSkill().getId();
                }));

        // 각 스킬별 결과 처리
        for (Map.Entry<Long, List<ExamReqDto.ExamSubmitReqDto>> entry : activityBySkill.entrySet()) {
            Long skillId = entry.getKey();
            List<ExamReqDto.ExamSubmitReqDto> skillActivities = entry.getValue();

            Skill skill = skillRepository.findById(skillId)
                    .orElseThrow(() -> new RuntimeException("Skill not found"));

            UserPreviewTest userPreviewTest = new UserPreviewTest();
            userPreviewTest.setUser(user);
            userPreviewTest.setSkill(skill);
            userPreviewTest.setCompleted(true);
            userPreviewTest.setCompletionDate(LocalDate.now());
            userPreviewTestRepository.save(userPreviewTest);

            Map<Integer, Integer> difficultyTotalMap = new HashMap<>();
            Map<Integer, Integer> difficultyCorrectMap = new HashMap<>();

            for (ExamReqDto.ExamSubmitReqDto activity : skillActivities) {
                Question question = questionRepository.findById(activity.getQuestion().getQuestionId())
                        .orElseThrow(() -> new RuntimeException("Question not found"));

                UserActivity userActivity = new UserActivity();
                userActivity.setUser(user);
                userActivity.setQuestion(question);
                userActivity.setTestInfo("Preview Test");
                userActivity.setQuestionNum(activity.getQuestionNum());

                // 체크된 옵션 ID (null 허용)
                Long optionId = activity.getOptionId();
                userActivity.setChecked(optionId);

                // 정답 여부 판단 (null이면 오답)
                boolean isCorrect = false;
                if (optionId != null) {
                    Option submittedOption = optionRepository.findById(optionId)
                            .orElseThrow(() -> new RuntimeException("Option not found"));
                    isCorrect = submittedOption.isAnswer();
                }
                userActivity.setCorrection(isCorrect);
                userActivity.setScore(isCorrect ? 100.0 / 21 : 0.0);
                userActivity.setDate(LocalDateTime.now());

                userActivityRepository.save(userActivity);

                int difficulty = question.getDifficulty();
                difficultyTotalMap.merge(difficulty, 1, Integer::sum);
                if (isCorrect) {
                    difficultyCorrectMap.merge(difficulty, 1, Integer::sum);
                }
            }

            // 스킬별 난이도에 따른 정확도 업데이트
            for (int difficulty : difficultyTotalMap.keySet()) {
                int total = difficultyTotalMap.get(difficulty);
                int correct = difficultyCorrectMap.getOrDefault(difficulty, 0);
                float accuracy = (float) correct / total;

                SkillLevel skillLevel = skillLevelRepository.findByUserAndSkillAndDifficulty(user, skill, difficulty)
                        .orElse(new SkillLevel(user, skill, difficulty));

                skillLevel.setCurrentAccuracy(accuracy);
                skillLevel.setLastUpdated(LocalDateTime.now());
                skillLevelRepository.save(skillLevel);
            }
        }

        // 30일 플랜 생성
        dailyPlanService.generateDailyPlan(userId);
    }

    public ResultDto.Response analyzePreviewTestResult(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<UserActivity> activities = userActivityRepository.findByUserIdAndTestInfo(userId, "Preview Test");

        // 실전 예상 점수 계산
        double estimatedScore = calculateEstimatedScore(activities);

        // 점수 분석
        ResultDto.ScoreBreakdown scoreBreakdown = analyzeScore(activities);

        // 취약 영역 분석
        ResultDto.WeakAreaAnalysis weakAreaAnalysis = analyzeWeakAreas(activities);

        // 보충해야 할 개념 Top 2
        List<String> topConceptsToImprove = getTopConceptsToImprove(weakAreaAnalysis);

        return ResultDto.Response.builder()
                .scoreBreakdown(scoreBreakdown)
                .weakAreaAnalysis(weakAreaAnalysis)
                .topConceptsToImprove(topConceptsToImprove)
                .estimatedScore(estimatedScore)
                .build();
    }

    private int calculateEstimatedScore(List<UserActivity> activities) {
        // 1) static 중요도 가중치 맵
        Map<String, Double> weights = getWeights();

        // 2) 토픽별 총 문항 수·정답 수 집계
        Map<String, Long> totalByTopic = activities.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getQuestion().getSkill().getKeyConcepts(),
                        Collectors.counting()));
        Map<String, Long> correctByTopic = activities.stream()
                .filter(UserActivity::isCorrection)
                .collect(Collectors.groupingBy(
                        a -> a.getQuestion().getSkill().getKeyConcepts(),
                        Collectors.counting()));

        // 3) 출제된 토픽에 해당하는 가중치만 뽑아서 합계 계산
        double sumWeights = totalByTopic.keySet().stream()
                .mapToDouble(topic -> weights.getOrDefault(topic, 0.0))
                .sum();

        // 4) 가중합 계산
        double weightedSum = totalByTopic.entrySet().stream()
                .mapToDouble(e -> {
                    String topic = e.getKey();
                    long total = e.getValue();
                    long correct = correctByTopic.getOrDefault(topic, 0L);
                    double accuracy = (total > 0) ? correct / (double) total : 0;
                    double w = weights.getOrDefault(topic, 0.0);
                    return accuracy * w;
                })
                .sum();

        // 5) 출제된 토픽 비율로 정규화 후 100점 환산
        double normalized = (sumWeights > 0) ? (weightedSum / sumWeights) : 0;
        return (int) Math.round(normalized * 100);
    }

    private Map<String, Double> getWeights() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("SELECT 문", 0.13);
        weights.put("조인", 0.13);
        weights.put("데이터모델의 이해", 0.09);
        weights.put("함수", 0.09);
        weights.put("WHERE 절", 0.08);
        weights.put("속성", 0.07);
        weights.put("서브 쿼리", 0.07);
        weights.put("정규화", 0.05);
        weights.put("DML", 0.04);
        weights.put("계층형 질의와 셀프 조인", 0.04);
        weights.put("DDL", 0.03);
        weights.put("식별자", 0.03);
        weights.put("집합 연산자", 0.03);
        weights.put("윈도우 함수", 0.03);
        weights.put("DCL", 0.02);
        weights.put("그룹 함수", 0.02);
        weights.put("ORDER BY 절", 0.02);
        weights.put("PIVOT 절과 UNPIVOT 절", 0.02);
        weights.put("Top N 쿼리", 0.02);
        weights.put("정규 표현식", 0.02);
        weights.put("TCL", 0.02);
        weights.put("GROUP BY, HAVING 절", 0.02);
        weights.put("표준 조인", 0.02);
        weights.put("관계형 데이터 베이스 개요", 0.01);
        weights.put("엔터티", 0.01);
        weights.put("관계", 0.01);
        weights.put("모델이 표현하는 트랜잭션의 이해", 0.01);
        weights.put("Null 속성의 이해", 0.01);
        weights.put("본질 식별자 vs 인조 식별자", 0.01);

        return weights;
    }

    private ResultDto.ScoreBreakdown analyzeScore(List<UserActivity> activities) {
        double part1Score = 0;
        double part2Score = 0;

        for (UserActivity activity : activities) {
            Double score = activity.getScore();
            if (score != null) {
                if (activity.getQuestion().getSkill().getTitle().equals("1과목")) {
                    part1Score += score;
                } else {
                    part2Score += score;
                }
            }
        }

        // part1과 part2의 점수를 정수로 반올림
        int part1ScoreAdjusted = (int) Math.round(part1Score);
        int part2ScoreAdjusted = (int) Math.round(part2Score);

        // totalScore는 part1과 part2의 합, 최대 100점
        int totalScoreAdjusted = Math.min(100, part1ScoreAdjusted + part2ScoreAdjusted);

        return ResultDto.ScoreBreakdown.builder()
                .totalScore(totalScoreAdjusted)
                .part1Score(part1ScoreAdjusted)
                .part2Score(part2ScoreAdjusted)
                .build();
    }

    private ResultDto.WeakAreaAnalysis analyzeWeakAreas(List<UserActivity> activities) {
        Map<String, Integer> incorrectCounts = new HashMap<>();

        for (UserActivity activity : activities) {
            if (!activity.isCorrection()) {
                String topic = activity.getQuestion().getSkill().getKeyConcepts();
                incorrectCounts.put(topic, incorrectCounts.getOrDefault(topic, 0) + 1);
            }
        }

        List<ResultDto.WeakArea> weakAreas = incorrectCounts.entrySet().stream()
                .map(entry -> ResultDto.WeakArea.builder()
                        .topic(entry.getKey())
                        .incorrectCount(entry.getValue())
                        .build())
                .sorted(Comparator.comparingInt(ResultDto.WeakArea::getIncorrectCount).reversed())
                .collect(Collectors.toList());

        return ResultDto.WeakAreaAnalysis.builder()
                .totalQuestions(activities.size())
                .weakAreas(weakAreas)
                .build();
    }

    private List<String> getTopConceptsToImprove(ResultDto.WeakAreaAnalysis weakAreaAnalysis) {
        List<String> topConcepts = new ArrayList<>();

        // 틀린 문제에서 추출한 개념들
        List<String> weakTopics = weakAreaAnalysis.getWeakAreas().stream()
                .map(ResultDto.WeakArea::getTopic)
                .collect(Collectors.toList());

        // 틀린 개념 먼저 추가
        topConcepts.addAll(weakTopics);

        // 만약 틀린 개념이 2개 미만이라면, 가중치가 높은 순으로 추가
        if (topConcepts.size() < 2) {
            List<String> highWeightTopics = getWeights().entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .filter(topic -> !topConcepts.contains(topic))
                    .collect(Collectors.toList());

            // 필요한 만큼만 추가
            int needed = 2 - topConcepts.size();
            topConcepts.addAll(highWeightTopics.subList(0, needed));
        }

        return topConcepts;
    }

    @Transactional
    public void resetPreviewTest(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 프리뷰 테스트 활동 삭제
        userActivityRepository.deleteByUserIdAndTestInfo(userId, "Preview Test");

        // 스킬 정확도 초기화 (프리뷰에서만 저장된 항목에 한함)
        List<SkillLevel> previewSkillLevels = skillLevelRepository.findByUser(user);
        for (SkillLevel level : previewSkillLevels) {
            level.setCurrentAccuracy(0f);
            level.setLastUpdated(null);
        }
        skillLevelRepository.saveAll(previewSkillLevels);

        // 프리뷰 테스트 기록 삭제
        userPreviewTestRepository.deleteByUser(user);

        // 상태 초기화
        user.updatePreviewTestStatus(PreviewTestStatus.NOT_STARTED);
        userRepository.save(user);
    }

    @Transactional
    public void resetSurveyAndPreview(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomApiException("User not found"));

        // 1. 설문조사 삭제
        surveyRepository.deleteByUserId(userId);

        // 2. 프리뷰 테스트 기록 삭제
        userPreviewTestRepository.deleteByUser(user);
        userActivityRepository.deleteByUserIdAndTestInfo(userId, "Preview Test");

        // 3. SkillLevel (정확도) 초기화
        List<SkillLevel> skillLevels = skillLevelRepository.findByUser(user);
        for (SkillLevel level : skillLevels) {
            level.setCurrentAccuracy(0f);
            level.setLastUpdated(null);
        }
        skillLevelRepository.saveAll(skillLevels);

        // 4. 사용자 상태 초기화
        user.updatePreviewTestStatus(PreviewTestStatus.NOT_STARTED);
        userRepository.save(user);
    }
}