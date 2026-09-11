package com.example.tradironi.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

    private static final UUID KEYCLOAK_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserSyncService userSyncService;

    private Jwt jwtWithSubject(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", subject)
                .build();
    }

    @Test
    void doesNotCreateUserWhenAlreadyExists() {
        when(userRepository.existsByKeycloakId(KEYCLOAK_ID)).thenReturn(true);

        userSyncService.syncIfMissing(jwtWithSubject(KEYCLOAK_ID.toString()));

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsActiveUserWhenMissing() {
        when(userRepository.existsByKeycloakId(KEYCLOAK_ID)).thenReturn(false);

        userSyncService.syncIfMissing(jwtWithSubject(KEYCLOAK_ID.toString()));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getKeycloakId()).isEqualTo(KEYCLOAK_ID);
        assertThat(saved.getStatus()).isEqualTo(UserStatusEnum.ACTIVE);
        assertThat(saved.getNotes()).isNull();
    }

    @Test
    void throwsWhenSubjectIsNotAUuid() {
        assertThatThrownBy(() -> userSyncService.syncIfMissing(jwtWithSubject("not-a-uuid")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}