package com.kingspan.purchase.user.web;

import com.kingspan.purchase.user.application.AuthenticatedSession;
import java.time.Instant;

record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {

    static LoginResponse from(AuthenticatedSession session) {
        return new LoginResponse(
                session.accessToken(), "Bearer", session.expiresAt(), UserResponse.from(session.user()));
    }
}
