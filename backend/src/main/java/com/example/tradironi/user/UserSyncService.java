package com.example.tradironi.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final UserRepository userRepository;

    @Transactional
    public void syncIfMissing(Jwt jwt) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());
        if (userRepository.existsByKeycloakId(keycloakId)) {
            return;
        }

        log.info("Creating local User for keycloakId={}", keycloakId);

        User user = User.builder()
                .keycloakId(keycloakId)
                .status(UserStatusEnum.ACTIVE)
                .build();
        userRepository.save(user);
    }
}
