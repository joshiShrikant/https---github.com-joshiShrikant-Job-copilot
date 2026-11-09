package com.eurojobcopilot.modules.auth.security;

import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUserPrincipal loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Email must not be blank");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found for email: " + normalizedEmail));
        return AuthenticatedUserPrincipal.from(user);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserPrincipal loadUserByUuid(String uuid) throws UsernameNotFoundException {
        if (uuid == null || uuid.isBlank()) {
            throw new UsernameNotFoundException("UUID must not be blank");
        }
        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new UsernameNotFoundException("User not found for uuid: " + uuid));
        return AuthenticatedUserPrincipal.from(user);
    }
}

