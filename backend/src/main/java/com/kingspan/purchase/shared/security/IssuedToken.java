package com.kingspan.purchase.shared.security;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {}
