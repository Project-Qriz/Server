package com.qriz.sqld.dto.application;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.qriz.sqld.domain.application.Application;

import lombok.Getter;
import lombok.Setter;

public class ApplicationRespDto {

    @Getter
    @Setter
    public static class ApplyListRespDto {
        private Long registeredApplicationId;
        private Long registeredUserApplyId;
        private List<ApplicationDetail> applications;

        public ApplyListRespDto(Long registeredApplicationId, Long registeredUserApplyId,
                List<ApplicationDetail> applications) {
            this.registeredApplicationId = registeredApplicationId;
            this.registeredUserApplyId = registeredUserApplyId;
            this.applications = applications;
        }

        @Getter
        @Setter
        public static class ApplicationDetail {
            private Long applicationId;
            private Long userApplyId;
            private String examName;
            private String period;
            private String examDate;
            private String releaseDate;

            public ApplicationDetail(Application application, Long userApplyId) {
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM.dd(E)", Locale.KOREAN);
                DateTimeFormatter testDateFormatter = DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN);
                DateTimeFormatter releaseDateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN);

                this.applicationId = application.getId();
                this.userApplyId = userApplyId;
                this.examName = application.getExamName();
                // 접수 기간 포맷 수정
                this.period = application.getStartDate().format(dateFormatter) + " 10:00 ~ "
                        + application.getEndDate().format(dateFormatter) + " 18:00";
                this.examDate = application.getExamDate().format(testDateFormatter);
                this.releaseDate = application.getReleaseDate().format(releaseDateFormatter);
            }
        }
    }

    @Getter
    @Setter
    public static class ApplyRespDto {
        private String examName;
        private String period;
        private String examDate;
        private String releaseDate;

        public ApplyRespDto(String examName, String period, String examDate, String releaseDate) {
            this.examName = examName;
            this.period = period;
            this.examDate = examDate;
            this.releaseDate = releaseDate;
        }
    }

    @Getter
    public static class AppliedRespDto {
        private String examName;
        private String period;
        private String examDate;

        public AppliedRespDto(Application application) {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM.dd(E)", Locale.KOREAN);
            DateTimeFormatter testDateFormatter = DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN);

            this.examName = application.getExamName();
            // 접수 기간 포맷 수정
            this.period = application.getStartDate().format(dateFormatter) + " 10:00 ~ "
                    + application.getEndDate().format(dateFormatter) + " 18:00";
            this.examDate = application.getExamDate().format(testDateFormatter);
        }
    }

    @Getter
    @Setter
    public static class ExamDDayRespDto {
        private Integer dDay;
        private String status; // "before": D-Day, "after": D+Day
        private boolean isEmpty; // 등록된 일정이 없는 경우 true

        public ExamDDayRespDto(Integer dDay, String status, boolean isEmpty) {
            this.dDay = dDay;
            this.status = status;
            this.isEmpty = isEmpty;
        }
    }
}
