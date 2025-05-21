package com.qriz.sqld.service.apply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.qriz.sqld.config.auth.LoginUser;
import com.qriz.sqld.domain.application.Application;
import com.qriz.sqld.domain.application.ApplicationRepository;
import com.qriz.sqld.domain.apply.UserApply;
import com.qriz.sqld.domain.apply.UserApplyRepository;
import com.qriz.sqld.dto.application.ApplicationReqDto;
import com.qriz.sqld.dto.application.ApplicationRespDto;
import com.qriz.sqld.dto.application.ApplicationRespDto.ApplyListRespDto.ApplicationDetail;
import com.qriz.sqld.handler.ex.CustomApiException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class ApplyService {

        private final ApplicationRepository applicationRepository;
        private final UserApplyRepository userApplyRepository;

        private final Logger logger = LoggerFactory.getLogger(ApplyService.class);

        // 시험 접수 목록 조회
        @Transactional
        public ApplicationRespDto.ApplyListRespDto applyList(LoginUser loginUser) {
                Long userId = loginUser.getUser().getId();

                // 1. 현재 사용자가 등록한 시험 정보 조회
                Map<Long, Long> uaMap = userApplyRepository.findAllByUserId(userId)
                                .stream()
                                .collect(Collectors.toMap(
                                                ua -> ua.getApplication().getId(),
                                                UserApply::getId));

                // 2. 전체 시험 목록 조회
                List<Application> apps = applicationRepository.findAll();

                // 3. ApplicationDetail 리스트
                List<ApplicationDetail> details = apps.stream()
                                .map(app -> new ApplicationDetail(app, uaMap.get(app.getId())))
                                .collect(Collectors.toList());

                // 4. 등록한 시험 Id(application) 및 등록한 레코드 Id(userApplyId)
                Long registeredApplicationId = uaMap.keySet().stream().findFirst().orElse(null);
                Long registeredApplyId = uaMap.values().stream().findFirst().orElse(null);

                return new ApplicationRespDto.ApplyListRespDto(registeredApplicationId, registeredApplyId, details);
        }

        // 시험 접수
        public ApplicationRespDto.ApplyRespDto apply(Long applicationId, LoginUser loginUser) {
                Long userId = loginUser.getUser().getId();

                // 1. 이미 어떤 시험에 접수된 상태인지 체크 (새로 접수 불가)
                if (userApplyRepository.existsByUserId(userId)) {
                        throw new CustomApiException("이미 다른 시험에 접수 중이므로, 새로 접수할 수 없습니다.");
                }

                // 2. 해당 사용자가 해당 시험에 접수 중인지 확인
                if (userApplyRepository.existsByUserIdAndApplicationId(userId, applicationId)) {
                        throw new CustomApiException("이미 해당 시험에 접수하였습니다.");
                }

                // 3. 시험이 존재하는지 확인
                Application application = applicationRepository.findById(applicationId)
                                .orElseThrow(() -> new CustomApiException("존재하지 않는 시험입니다."));

                // 4. 사용자 접수 정보 생성
                UserApply userApply = new UserApply(loginUser.getUser(), application);
                userApplyRepository.save(userApply);

                // 5. 응답 데이터 생성
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.KOREAN);
                String period = formatPeriod(application.getStartDate(), application.getEndDate());

                return new ApplicationRespDto.ApplyRespDto(
                                application.getExamName(),
                                period,
                                application.getExamDate().format(dateFormatter),
                                application.getReleaseDate().format(dateFormatter));
        }

        // 등록한 시험 접수 정보 조회
        public ApplicationRespDto.AppliedRespDto getApplied(Long userId) {
                // 1. 사용자 조회
                UserApply userApply = userApplyRepository.findUserApplyByUserId(userId)
                                .orElseThrow(() -> new CustomApiException("등록된 일정이 없어요"));

                Application application = userApply.getApplication();

                return new ApplicationRespDto.AppliedRespDto(application);
        }

        // 등록한 시험에 대한 D-Day
        public ApplicationRespDto.ExamDDayRespDto getDDay(Long userId) {
                // 사용자의 시험 일정 조회
                Optional<UserApply> userApply = userApplyRepository.findUserApplyByUserId(userId);

                // 등록된 일정이 없는 경우
                if (userApply.isEmpty()) {
                        return new ApplicationRespDto.ExamDDayRespDto(null, null, true);
                }

                LocalDate examDate = userApply.get().getApplication().getExamDate();
                LocalDate currentDate = LocalDate.now();

                long daysBetween = ChronoUnit.DAYS.between(currentDate, examDate);

                // D-Day가 지난 경우 D+N으로 표시
                Integer dDay = (daysBetween < 0) ? (int) Math.abs(daysBetween) : (int) daysBetween;
                String status = (daysBetween < 0) ? "after" : "before";

                return new ApplicationRespDto.ExamDDayRespDto(dDay, status, false);
        }

        private String formatPeriod(LocalDate startDate, LocalDate endDate) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM.dd(E)", Locale.KOREAN);
                return startDate.format(formatter) + " ~ " + endDate.format(formatter);
        }

        // 시험 일정 수정
        @Transactional
        public ApplicationRespDto.ApplyRespDto modifyApplication(Long uaId, Long newApplicationId,
                        LoginUser loginUser) {
                // 1. 현재 사용자의 접수 정보 조회
                UserApply ua = userApplyRepository.findById(uaId)
                                .orElseThrow(() -> new CustomApiException("현재 접수된 시험을 찾을 수 없습니다."));
                if (!ua.getUser().getId().equals(loginUser.getUser().getId())) {
                        throw new CustomApiException("권한이 없습니다.");
                }

                // 2. 동일한 시험 선택 시 예외 처리
                Long currentApplyId = ua.getApplication().getId();
                if (currentApplyId.equals(newApplicationId)) {
                        throw new CustomApiException("현재 접수된 시험과 동일한 시험입니다.");
                }

                // 3. 새로운 시험 정보 확인
                Application newApp = applicationRepository.findById(newApplicationId)
                                .orElseThrow(() -> new CustomApiException("변경하려는 시험 정보를 찾을 수 없습니다."));

                // 4. 기존 레코드 업데이트
                ua.setApplication(newApp);
                userApplyRepository.save(ua); // Update

                // 5. 응답 데이터 생성
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.KOREAN);
                String period = formatPeriod(newApp.getStartDate(), newApp.getEndDate());

                return new ApplicationRespDto.ApplyRespDto(
                                newApp.getExamName(),
                                period,
                                newApp.getExamDate().format(dateFormatter),
                                newApp.getReleaseDate().format(dateFormatter));
        }
}
