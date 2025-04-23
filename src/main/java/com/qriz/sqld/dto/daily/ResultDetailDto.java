package com.qriz.sqld.dto.daily;

import com.qriz.sqld.domain.question.Question;
import com.qriz.sqld.domain.question.option.Option;
import com.qriz.sqld.domain.UserActivity.UserActivity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultDetailDto {
        private String skillName;
        // 문제 본문
        private String questionText;
        // (필요한 경우 문제 번호)
        private int questionNum;
        // 부가 설명 (예: 상황, 테이블 구조 등)
        private String description;
        private String option1;
        private String option2;
        private String option3;
        private String option4;
        // 정답 (Option 엔티티에서 isAnswer가 true인 선택지의 번호, 1부터 시작)
        private Integer answer;
        // 해설
        private String solution;
        // 사용자가 선택한 값 (선택지 번호, 사용자가 풀지 않은 경우 null)
        private Integer checked;
        // 채점 결과
        private boolean correction;
        // 그 외 추가 정보 (필요에 따라 사용)
        private String testInfo;
        private Long skillId;
        private String title;
        private String keyConcepts;

        /**
         * Question 엔티티와 UserActivity 엔티티를 기반으로 ResultDetailDto를 생성
         */
        public static ResultDetailDto from(Question question, UserActivity userActivity) {
                List<Option> sortedOptions = question.getSortedOptions();

                String op1 = sortedOptions.size() > 0 ? sortedOptions.get(0).getContent() : null;
                String op2 = sortedOptions.size() > 1 ? sortedOptions.get(1).getContent() : null;
                String op3 = sortedOptions.size() > 2 ? sortedOptions.get(2).getContent() : null;
                String op4 = sortedOptions.size() > 3 ? sortedOptions.get(3).getContent() : null;

                // 사용자 선택한 옵션의 인덱스 찾기 (1부터 시작)
                Integer userCheckedOption = null;
                if (userActivity.getChecked() != null) {
                        for (int i = 0; i < sortedOptions.size(); i++) {
                                if (sortedOptions.get(i).getId().equals(userActivity.getChecked())) {
                                        userCheckedOption = i + 1;
                                        break;
                                }
                        }
                }

                // 정답 옵션의 인덱스 찾기 (1부터 시작)
                Integer correctAnswer = null;
                for (int i = 0; i < sortedOptions.size(); i++) {
                        if (sortedOptions.get(i).isAnswer()) {
                                correctAnswer = i + 1;
                                break;
                        }
                }

                return ResultDetailDto.builder()
                                .skillName(question.getSkill().getKeyConcepts()) // 기존에 사용하던 값 (필요에 따라 title로 변경 가능)
                                .questionText(question.getQuestion())
                                .questionNum(userActivity.getQuestionNum())
                                .description(question.getDescription())
                                .option1(op1)
                                .option2(op2)
                                .option3(op3)
                                .option4(op4)
                                .answer(correctAnswer) // 정답 선택지 번호 (int로 반환)
                                .solution(question.getSolution())
                                .checked(userCheckedOption) // 사용자가 선택한 옵션 번호 (int로 반환, 없으면 null)
                                .correction(userActivity.isCorrection())
                                .testInfo(userActivity.getTestInfo())
                                .skillId(question.getSkill().getId())
                                .title(question.getSkill().getTitle())
                                .keyConcepts(question.getSkill().getKeyConcepts())
                                .build();
        }
}
