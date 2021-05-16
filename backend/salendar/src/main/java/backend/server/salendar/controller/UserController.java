package backend.server.salendar.controller;

import backend.server.salendar.domain.User;
import backend.server.salendar.repository.UserRepository;
import backend.server.salendar.security.JwtTokenProvider;
import backend.server.salendar.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;s
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.*;


@Api(tags = {"1. User"})
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {
    // 기본형
    @Autowired
    UserService userService;

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;


    // 회원 가입
    @ApiOperation(value = "회원 가입", notes = "회원가입")
    @PostMapping("/join")
    public ResponseEntity<String> join(@ApiParam(value = "usrEmail, usrNick, usrPwd", required = true) @RequestBody Map<String, String> user) {
        try {
            userService.validateDuplicateUserNick(user.get("usrNick"));
            userService.validateDuplicateUserEmail(user.get("usrEmail"));
            userRepository.save(User.builder()
                    .usrEmail(user.get("usrEmail"))
                    .usrPwd(passwordEncoder.encode(user.get("usrPwd")))
                    .usrNick(user.get("usrNick"))
                    .roles(Collections.singletonList(user.get("usrEmail").endsWith("@admin.com") ? "ROLE_ADMIN" : "ROLE_USER"))
                    .usrAlarm(Boolean.valueOf(user.get("usrAlarm")))
                    .build());
            return new ResponseEntity<>(user.get("usrNick"), HttpStatus.OK);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(e.toString(), HttpStatus.BAD_REQUEST);
        }
    }


    // 로그인
    @ApiOperation(value = "로그인", notes = "로그인")
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login
    (@ApiParam(value = "usrEmail, usrPwd", required = true) @RequestBody Map<String, String> user) {
        Map<String, Object> response = new HashMap<>();
        HttpStatus status;
        try {
            User member = userRepository.findByUsrEmail(user.get("usrEmail"))
                    .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일 입니다."));
            if (!passwordEncoder.matches(user.get("usrPwd"), member.getPassword())) {
                throw new IllegalArgumentException("잘못된 비밀번호입니다.");
            }
            response.put("token", jwtTokenProvider.createToken(member.getUsername(), member.getRoles()));
            status = HttpStatus.OK;
        } catch (Exception e) {
            response.put("message", e.toString());
            status = HttpStatus.NOT_ACCEPTABLE;
        }
        return new ResponseEntity<>(response, status);
    }


    // 토큰으로 회원조회
    @ApiOperation(value = "token으로 회원 정보 조회")
    @GetMapping(value = "/token/mypage")
    public ResponseEntity<User> getUser(HttpServletRequest request) {
        Optional<User> user = Optional.ofNullable(userService.findByToken(JwtTokenProvider.resolveToken(request)));
        return user
                .map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(null, HttpStatus.NOT_FOUND));
    }


    // 회원 정보 수정
    @ApiOperation(value = "회원 정보 변경", notes = "token 필요")
    @PutMapping(value = "/token/update", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> updateMember(@RequestBody Map<String, String> user, HttpServletRequest request) {
        User curUser = userService.findByToken(JwtTokenProvider.resolveToken(request));
        try {
            if (!user.get("usrNick").equals(curUser.getUsrNick())) {
                userService.validateDuplicateUserNick(user.get("usrNick"));
                curUser.setUsrNick(user.get("usrNick"));
            }
            curUser.setUsrPwd(passwordEncoder.encode(user.get("usrPwd")));
            curUser.setUsrAlarm(Boolean.valueOf(user.get("userAlarm")));
            userRepository.save(curUser);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(e.toString(), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(user.get("usrNick"), HttpStatus.OK);
    }


    //     프로필 이미지 설정 -> 이건 DB에 들어가는 용
    @ApiOperation(value = "프로필 이미지 설정", notes = "Img file, token")
    @PutMapping(value = "/token/profileImg")
    public ResponseEntity<String> setUserProfileImg(@ApiParam(value = "image file") @RequestParam("usrImg") MultipartFile file,
                                                    HttpServletRequest request) {
        try {
            User user = userService.findByToken(JwtTokenProvider.resolveToken(request));
            user.setUsrImg(file.getBytes());
            userRepository.save(user);
            return new ResponseEntity<>("OK", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.toString(), HttpStatus.BAD_REQUEST);
        }
    }


    //    팔로우
    @ApiOperation(value = "매장 팔로우", notes = "token, storeName")
    @PostMapping(value = "/token/follow/{storeName}")
    public ResponseEntity<String> Follow(@PathVariable("storeName") String storeName, HttpServletRequest request) {
        try {
            userService.Follow(JwtTokenProvider.resolveToken(request), storeName);
        } catch (Exception e) {
            return new ResponseEntity<>(e.toString(), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }

    //    언팔로우
    @ApiOperation(value = "매장 언팔로우", notes = "token, storeName")
    @PostMapping(value = "/token/unfollow/{storeName}")
    public ResponseEntity<String> unFollow(@PathVariable("storeName") String storeName, HttpServletRequest request) {
        try {
            userService.unFollow(JwtTokenProvider.resolveToken(request), storeName);
        } catch (Exception e) {
            return new ResponseEntity<>(e.toString(), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }

    //    게시글 좋아요
    @ApiOperation(value = "게시글 좋아요 ", notes = "token, board")
    @PostMapping(value = "/token/like/{boardNo}")
    public ResponseEntity<String> likePost(@PathVariable("boardNo") String no, HttpServletRequest request) {
        try {
            Long boardNo = Long.parseLong(no);
            userService.likePost(JwtTokenProvider.resolveToken(request), boardNo);
        } catch (Exception e) {
            return new ResponseEntity<>(e.toString(), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }

    //    게시글 좋아요 취소
    @ApiOperation(value = "게시글 좋아요 취소", notes = "token, board")
    @PostMapping(value = "/token/unlike/{boardNo}")
    public ResponseEntity<String> unlikePost(@PathVariable("boardNo") String no, HttpServletRequest request) {
        try {
            Long boardNo = Long.parseLong(no);
            userService.unlikePost(JwtTokenProvider.resolveToken(request), boardNo);
        } catch (Exception e) {
            return new ResponseEntity<>(e.toString(), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }

    //    팔로우 조회
    @ApiOperation(value = "팔로우 중 매장 조회")
    @GetMapping(value = "/token/followings")
    public ResponseEntity<Map<String, Boolean>> userFollowings(HttpServletRequest request) {
        try {
            return new ResponseEntity<>(userService.usrFollowings(JwtTokenProvider.resolveToken(request)), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    //  비밀번호 일치 여부
    @ApiOperation(value = "비밀번호 일치 여부")
    @PostMapping(value = "/token/pwdConfirm")
    public HttpStatus pwdConfirm(HttpServletRequest request, @RequestBody String pwd) {
        User user = userService.findByToken(JwtTokenProvider.resolveToken(request));
        if (!passwordEncoder.matches(pwd.substring(0, pwd.length() - 1), user.getPassword())) {
            return HttpStatus.NOT_ACCEPTABLE;
        }
        return HttpStatus.OK;
    }
}
