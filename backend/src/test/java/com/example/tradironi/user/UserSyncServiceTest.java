package com.example.tradironi.user;

import com.example.tradironi.user.internal.User;
import com.example.tradironi.user.internal.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void doesNotCreateUserWhenAlreadyExists() {
        when(userRepository.existsByKeycloakId(KEYCLOAK_ID)).thenReturn(true);

        userSyncService.syncUser(KEYCLOAK_ID);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsActiveUserWhenMissing() {
        when(userRepository.existsByKeycloakId(KEYCLOAK_ID)).thenReturn(false);

        userSyncService.syncUser(KEYCLOAK_ID);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getKeycloakId()).isEqualTo(KEYCLOAK_ID);
        assertThat(saved.getStatus()).isEqualTo(UserStatusEnum.ACTIVE);
        assertThat(saved.getNotes()).isNull();
    }
}