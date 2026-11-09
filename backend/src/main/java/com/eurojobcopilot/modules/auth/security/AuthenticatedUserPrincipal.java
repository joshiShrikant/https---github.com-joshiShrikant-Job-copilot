package com.eurojobcopilot.modules.auth.security;

import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Immutable Spring Security principal backed by persisted {@link User}.
 */
@Getter
public class AuthenticatedUserPrincipal implements UserDetails {

    private final Long id;
    private final String uuid;
    private final String email;
    private final String passwordHash;
    private final String firstName;
    private final String lastName;
    private final UserRole role;
    private final AccountStatus accountStatus;
    private final boolean emailVerified;
    private final Collection<? extends GrantedAuthority> authorities;

    private AuthenticatedUserPrincipal(User user) {
        this.id = user.getId();
        this.uuid = user.getUuid();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.role = user.getRole();
        this.accountStatus = user.getAccountStatus();
        this.emailVerified = user.isEmailVerified();
        this.authorities = List.of(new SimpleGrantedAuthority(user.getRole().name()));
    }

    public static AuthenticatedUserPrincipal from(User user) {
        return new AuthenticatedUserPrincipal(user);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountStatus != AccountStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE;
    }
}

