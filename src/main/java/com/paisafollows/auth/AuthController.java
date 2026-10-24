package com.paisafollows.auth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final SignupService signupService;
    private final LoginService loginService;
    private final CurrentUser currentUser;
    private final SecurityContextRepository securityContextRepository;
    private final Set<String> adminEmails;

    public AuthController(SignupService signupService, LoginService loginService,
                          CurrentUser currentUser, SecurityContextRepository securityContextRepository,
                          @Value("${app.admin-emails:}") String adminEmails) {
        this.signupService = signupService;
        this.loginService = loginService;
        this.currentUser = currentUser;
        this.securityContextRepository = securityContextRepository;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(String::trim).map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return signupService.register(request);
    }

    @PostMapping("/auth/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body,
                            HttpServletRequest request, HttpServletResponse response) {
        // 1. Check email + password (throws 401 if wrong)
        AppUser user = loginService.authenticate(body.email(), body.password());

        // 2. Give the browser a fresh session id (protects against "session fixation" attacks)
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        // 3. Roles: everyone is USER; emails listed in app.admin-emails are also ADMIN
        List<GrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (adminEmails.contains(user.email().toLowerCase())) {
            roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        // 4. Record "this session is logged in as this user" and save it in the session
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(user, null, roles);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return loginService.me(user);
    }

    @GetMapping("/me")
    public MeResponse me() {
        return loginService.me(currentUser.get());
    }
}