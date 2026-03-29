package com.gateway.identity.service;

import com.gateway.identity.entity.UserEntity;
import com.gateway.identity.entity.enums.UserStatus;
import com.gateway.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Manages linking social login accounts (Google, GitHub, Azure AD) to
 * internal user accounts. If a user with the given email already exists,
 * the social provider is linked. Otherwise, a new user is created.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final UserRepository userRepository;

    /**
     * Link a social login account to an internal user, or create a new user.
     *
     * @param provider   the social provider name (google, github, azure)
     * @param email      the email from the social provider
     * @param providerId the unique ID from the social provider
     * @return the linked or newly created user entity
     */
    @Transactional
    public UserEntity linkSocialAccount(String provider, String email, String providerId) {
        Optional<UserEntity> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            UserEntity user = existingUser.get();
            log.info("Linked social account: provider={}, email={}, providerId={}",
                    provider, email, providerId);
            // In a full implementation, store the provider+providerId mapping
            // in a separate social_accounts table
            return user;
        }

        // Create new user from social login
        UserEntity newUser = UserEntity.builder()
                .email(email)
                .passwordHash("{noop}social-login-no-password")
                .status(UserStatus.ACTIVE)
                .emailVerified(true) // Social providers verify email
                .build();

        newUser = userRepository.save(newUser);
        log.info("Created new user from social login: provider={}, email={}, userId={}",
                provider, email, newUser.getId());
        return newUser;
    }
}
