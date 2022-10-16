package com.project.ecommerce.perfume.service.Impl;

import com.project.ecommerce.perfume.entity.User;
import com.project.ecommerce.perfume.enums.AuthProvider;
import com.project.ecommerce.perfume.enums.Role;
import com.project.ecommerce.perfume.exception.ApiRequestException;
import com.project.ecommerce.perfume.exception.EmailException;
import com.project.ecommerce.perfume.exception.PasswordConfirmationException;
import com.project.ecommerce.perfume.exception.PasswordException;
import com.project.ecommerce.perfume.repository.UserRepository;
import com.project.ecommerce.perfume.security.JwtProvider;
import com.project.ecommerce.perfume.security.oauth2.OAuth2UserInfo;
import com.project.ecommerce.perfume.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @Value("${hostname}")
    private String hostname;

    @Override
    public Map<String, Object> login(String email, String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ApiRequestException("Email not found.", HttpStatus.NOT_FOUND));
            String userRole = user.getRoles().iterator().next().name();
            String token = jwtProvider.createToken(email, userRole);
            Map<String, Object> response = new HashMap<>();
            response.put("user", user);
            response.put("token", token);
            return response;
        } catch (AuthenticationException e) {
            throw new ApiRequestException("Incorrect password or email", HttpStatus.FORBIDDEN);
        }
    }

    @Override
    @Transactional
    public String registerUser(User user, String password2) {
        if (user.getPassword() != null && !user.getPassword().equals(password2)) {
            throw new PasswordException("Passwords do not match.");
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new EmailException("Email is already used.");
        }
        user.setActive(true);
        user.setRoles(Collections.singleton(Role.USER));
        user.setProvider(AuthProvider.LOCAL);
        user.setActivationCode(UUID.randomUUID().toString());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);

        return "User successfully registered.";
    }

    @Override
    @Transactional
    public User registerOauth2User(String provider, OAuth2UserInfo oAuth2UserInfo) {
        User user = new User();
        user.setEmail(oAuth2UserInfo.getEmail());
        user.setFirstName(oAuth2UserInfo.getFirstName());
        user.setLastName(oAuth2UserInfo.getLastName());
        user.setActive(true);
        user.setRoles(Collections.singleton(Role.USER));
        user.setProvider(AuthProvider.valueOf(provider.toUpperCase()));
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User updateOauth2User(User user, String provider, OAuth2UserInfo oAuth2UserInfo) {
        user.setFirstName(oAuth2UserInfo.getFirstName());
        user.setLastName(oAuth2UserInfo.getLastName());
        user.setProvider(AuthProvider.valueOf(provider.toUpperCase()));
        return userRepository.save(user);
    }

    @Override
    public String getEmailByPasswordResetCode(String code) {
        return userRepository.getEmailByPasswordResetCode(code)
                .orElseThrow(() -> new ApiRequestException("Password reset code is invalid!", HttpStatus.BAD_REQUEST));
    }

    @Override
    @Transactional
    public String sendPasswordResetCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiRequestException("Email not found.", HttpStatus.NOT_FOUND));
        user.setPasswordResetCode(UUID.randomUUID().toString());
        userRepository.save(user);

//        sendEmail(user, "Password reset", "password-reset-template", "resetUrl", "/reset/" + user.getPasswordResetCode());
        return "Reset password code is send to your E-mail";
    }

    @Override
    @Transactional
    public String passwordReset(String email, String password, String password2) {
        if (StringUtils.isEmpty(password2)) {
            throw new PasswordConfirmationException("Password confirmation cannot be empty.");
        }
        if (password != null && !password.equals(password2)) {
            throw new PasswordException("Passwords do not match.");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiRequestException("Email not found.", HttpStatus.NOT_FOUND));
        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordResetCode(null);
        userRepository.save(user);
        return "Password successfully changed!";
    }

    @Override
    @Transactional
    public String activateUser(String code) {
        User user = userRepository.findByActivationCode(code)
                .orElseThrow(() -> new ApiRequestException("Activation code not found.", HttpStatus.NOT_FOUND));
        user.setActivationCode(null);
        user.setActive(true);
        userRepository.save(user);
        return "User successfully activated.";
    }
}
