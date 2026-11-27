package co.lacava.gestion.auth.application;

import java.time.Instant;

public record IssuedToken(String token, Instant expiresAt) {
}
