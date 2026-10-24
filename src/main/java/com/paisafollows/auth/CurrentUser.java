package com.paisafollows.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;

/** Any class can ask this for the logged-in user. Used from Module 7 onward. */
@Component
public class CurrentUser {

    public AppUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUser user) {
            return user;
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED, "Please log in");
    }
}