package com.qriz.sqld.dto.daily;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class DaySubjectDetailsDto {

    @Getter
    @AllArgsConstructor
    public static class Response {
        private String dayNumber;
        private boolean passed;
        private Map<String, Double> items;
        private List<DailyResultDto> subjectResultsList;

        public double getTotalScore() {
            return items.values().stream()
                    .mapToDouble(Double::doubleValue)
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
}
