package com.qriz.sqld.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qriz.sqld.config.auth.LoginUser;
import com.qriz.sqld.dto.ResponseDto;
import com.qriz.sqld.dto.application.ApplicationReqDto;
import com.qriz.sqld.dto.application.ApplicationRespDto;
import com.qriz.sqld.service.apply.ApplyService;

import lombok.RequiredArgsConstructor;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/applications")
public class ApplyController {

    private final ApplyService applyService;

    // 시험 접수 목록 조회
    @GetMapping
    public ResponseEntity<?> getApplicationList(@AuthenticationPrincipal LoginUser loginUser) {
        ApplicationRespDto.ApplyListRespDto applyListRespDto = applyService.applyList(loginUser);
        return new ResponseEntity<>(new ResponseDto<>(1, "시험 접수 목록 불러오기 성공", applyListRespDto), HttpStatus.OK);
    }

    // 시험 접수
    @PostMapping
    public ResponseEntity<?> apply(@AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApplicationReqDto.ApplyReqDto applyReqDto) {

        ApplicationRespDto.ApplyRespDto applyRespDto = applyService.apply(applyReqDto.getApplyId(), loginUser);
        return new ResponseEntity<>(new ResponseDto<>(1, "시험 등록 성공", applyRespDto), HttpStatus.CREATED);
    }

    // 등록한 시험 접수 정보 조회
    @GetMapping("/applied")
    public ResponseEntity<?> getApplied(@AuthenticationPrincipal LoginUser loginUser) {
        ApplicationRespDto.AppliedRespDto applyRespDto = applyService.getApplied(loginUser.getUser().getId());
        return new ResponseEntity<>(new ResponseDto<>(1, "등록 시험 조회", applyRespDto), HttpStatus.OK);
    }

    // 등록한 시험에 대한 D-Day
    @GetMapping("/applied/d-day")
    public ResponseEntity<?> getDDay(@AuthenticationPrincipal LoginUser loginUser) {
        ApplicationRespDto.ExamDDayRespDto examDDayRespDto = applyService.getDDay(loginUser.getUser().getId());

        String message;
        if (examDDayRespDto.isEmpty()) {
            message = "등록된 일정이 없어요";
        } else {
            message = examDDayRespDto.getStatus().equals("before") ? "시험까지 남은 일수 계산 성공" : "시험 후 경과 일수 계산 성공";
        }

        return new ResponseEntity<>(
                new ResponseDto<>(1, message, examDDayRespDto),
                HttpStatus.OK);
    }

    // 접수 정보 변경
    @PatchMapping("/{uaId}")
    public ResponseEntity<?> modifyApplication(
            @PathVariable Long uaId,
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApplicationReqDto.ModifyReqDto modifyReqDto) {
        ApplicationRespDto.ApplyRespDto modifyRespDto = applyService.modifyApplication(uaId, modifyReqDto.getNewApplyId(), loginUser);
        return new ResponseEntity<>(
                new ResponseDto<>(1, "시험 접수 변경 성공", modifyRespDto),
                HttpStatus.OK);
    }
}
