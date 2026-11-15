package com.kingspan.purchase.user.application;

import java.time.Instant;

public record AuthenticatedSession(String accessToken, Instant expiresAt, UserAccount user) {}
