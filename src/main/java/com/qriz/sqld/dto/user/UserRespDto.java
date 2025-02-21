package com.qriz.sqld.dto.user;

import java.time.LocalDateTime;

import com.qriz.sqld.domain.preview.PreviewTestStatus;
import com.qriz.sqld.domain.user.User;
import com.qriz.sqld.util.CustomDateUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

public class UserRespDto {

    @Setter
    @Getter
    public static class LoginRespDto {
        private Long id;
        private String username;
        private String nickname;
        private String createdAt;
        private PreviewTestStatus previewTestStatus;

        public LoginRespDto(User user) {
            this.id = user.getId();
            this.username = user.getUsername();
            this.nickname = user.getNickname();
            this.createdAt = CustomDateUtil.toStringFormat(user.getCreatedAt());
            this.previewTestStatus = user.getPreviewTestStatus();
        }
    }

    @ToString
    @Setter
    @Getter
    public static class JoinRespDto {
        private Long id;
        private String username;
        private String nickname;

        public JoinRespDto(User user) {
            this.id = user.getId();
            this.username = user.getUsername();
            this.nickname = user.getNickname();
        }
    }

    @Getter
    @Setter
    public static class FindUsernameRespDto {
        private Long id;
        private String username;
        private String createdAt;

        public FindUsernameRespDto(User user) {
            this.id = user.getId();
            this.username = user.getUsername();
            this.createdAt = CustomDateUtil.toStringFormat(user.getCreatedAt());
        }
    }

    @Getter
    @Setter
    public static class ChangePwdRespDto {
        private String username;
        private String message;
        
        public ChangePwdRespDto(User user) {
            this.username = user.getUsername();
            this.message = "비밀번호가 변경되었습니다.";
        }

        public ChangePwdRespDto(String username, String message) {
            this.username = username;
            this.message = message;
        }
    }

    @Getter
    @Setter
    public static class UsernameDuplicateRespDto {
        private boolean isAvailable;

        public UsernameDuplicateRespDto(boolean isAvailable) {
            this.isAvailable = isAvailable;
        }
    }

    @Getter
    @Setter
    public static class EmailDuplicateRespDto {
        private boolean isAvailable;

        public EmailDuplicateRespDto(boolean isAvailable) {
            this.isAvailable = isAvailable;
        }
    }

    @Getter
    @Setter
    public static class ProfileRespDto {
        private String nickname;
        private String username;
        private String email; 

        public ProfileRespDto(User user) {
            this.nickname = user.getNickname();
            this.username = user.getUsername();
            this.email = user.getEmail();
        }
    }
}
