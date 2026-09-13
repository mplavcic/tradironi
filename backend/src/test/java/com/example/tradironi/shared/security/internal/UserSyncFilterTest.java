package com.example.tradironi.shared.security.internal;

import com.example.tradironi.user.UserSyncService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSyncFilterTest {

    @Mock
    private UserSyncService userSyncService;

    @InjectMocks
    private UserSyncFilter userSyncFilter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private Jwt dummyJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", "123e4567-e89b-42d3-a456-426614174000")
                .build();
    }

    @Test
    void syncsWhenJwtAuthenticationPresent() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(dummyJwt(), List.of(new SimpleGrantedAuthority("ROLE_USER")), "user"));

        userSyncFilter.doFilterInternal(request, response, filterChain);

        verify(userSyncService).syncUser(UUID.fromString(dummyJwt().getSubject()));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void skipsSyncWhenNoAuthentication() throws Exception {
        userSyncFilter.doFilterInternal(request, response, filterChain);

        verify(userSyncService, never()).syncUser(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void continuesChainWhenSyncThrows() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(dummyJwt(), List.of(new SimpleGrantedAuthority("ROLE_USER")), "user"));
        doThrow(new RuntimeException("fail")).when(userSyncService).syncUser(any());

        userSyncFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void continuesChainWhenSubjectIsNotAUuid() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(Jwt.withTokenValue("token").header("alg", "HS256").claim("sub", "not-a-uuid").build(),
                        List.of(new SimpleGrantedAuthority("ROLE_USER")), "user"));

        userSyncFilter.doFilterInternal(request, response, filterChain);

        verify(userSyncService, never()).syncUser(any());
        verify(filterChain).doFilter(request, response);
    }
}