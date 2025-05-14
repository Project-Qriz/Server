package com.qriz.sqld.dto.daily;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class DaySubjectDetailsDto {

    @Getter
    @AllArgsConstructor
    public static class SubItemDto {
        private Long skillId;
        private double score;
    }

    @Getter
    @AllArgsConstructor
    public static class Response {
        private String dayNumber;
        private boolean passed;
        private boolean reviewDay;
        private boolean comprehensiveReviewDay;
        private List<SubItemDto> items;
        private List<DailyResultDto> subjectResultsList;

        public double getTotalScore() {
            return items.stream()
                    .mapToDouble(SubItemDto::getScore)
                    .sum();
        }
    }

    @Getter
    @AllArgsConstructor
    public static class DailyResultDto {
        private Long questionId;
        private String detailType;
        private String question;
        private boolean correction;
    }

    @Getter
    @AllArgsConstructor
    public static class WeeklyReviewDto {
        private String dayNumber;
        private boolean passed;
        private boolean reviewDay;
        private boolean comprehensiveReviewDay;
        private double totalScore;
        private List<SimpleMajorItem> majorItems;
    }

    @Getter
    @AllArgsConstructor
    public static class SimpleMajorItem {
        private String majorItem;
        private double score;
    }

    @Getter
    @AllArgsConstructor
    public static class WeeklyReviewRspDto {
        private List<DailySubjectDetails> subjects;
        private double totalScore;
    }

    @Getter
    @AllArgsConstructor
    public static class DailySubjectDetails {
        private String title; // "1과목", "2과목"
        private double totalScore;
        private List<MajorItemDetail> majorItems;

        @Getter
        @AllArgsConstructor
        public static class MajorItemDetail {
            private String majorItem; // 예: "데이터 모델과 SQL"
            private double score;
            private List<SubItemScore> subItemScores;
        }

        @Getter
        @AllArgsConstructor
        public static class SubItemScore {
            private String subItem; // 예: "정규화"
            private double score;
        }
    }
}
