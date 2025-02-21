package com.qriz.sqld.domain.preview;

import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.qriz.sqld.domain.skill.Skill;
import com.qriz.sqld.domain.user.User;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
public class UserPreviewTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preview_id")
    private Long id;

    // 사용자 외래 키
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // 개념 외래 키
    @ManyToOne
    @JoinColumn(name = "skill_id")
    private Skill skill;

    // 완료 여부
    private boolean completed;

    // 완료 날짜
    private LocalDate completionDate;
}