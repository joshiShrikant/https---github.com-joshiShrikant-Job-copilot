import { FormControl, FormGroup } from '@angular/forms';
import {
  PASSWORD_COMPLEXITY_REGEX,
  evaluatePasswordRules,
  passwordsMatchValidator
} from './auth-validators';

describe('AuthValidators', () => {
  it('should evaluate empty password cleanly', () => {
    const result = evaluatePasswordRules('');
    expect(result.strengthLabel).toBe('Empty');
    expect(result.passedCount).toBe(0);
    expect(result.isValid).toBeFalse();
  });

  it('should identify missing password complexity requirements', () => {
    const missingSpecial = evaluatePasswordRules('SecurePass2026');
    expect(missingSpecial.hasSpecialChar).toBeFalse();
    expect(missingSpecial.isValid).toBeFalse();
    expect(missingSpecial.strengthLabel).toBe('Good');
    expect(PASSWORD_COMPLEXITY_REGEX.test('SecurePass2026')).toBeFalse();
  });

  it('should accept compliant password meeting all 5 backend complexity rules', () => {
    const strong = evaluatePasswordRules('SecureP@ssw0rd2026!');
    expect(strong.minLength).toBeTrue();
    expect(strong.maxLength).toBeTrue();
    expect(strong.hasUppercase).toBeTrue();
    expect(strong.hasLowercase).toBeTrue();
    expect(strong.hasDigit).toBeTrue();
    expect(strong.hasSpecialChar).toBeTrue();
    expect(strong.passedCount).toBe(5);
    expect(strong.strengthLabel).toBe('Strong');
    expect(strong.isValid).toBeTrue();
    expect(PASSWORD_COMPLEXITY_REGEX.test('SecureP@ssw0rd2026!')).toBeTrue();
  });

  it('should detect mismatched confirmation passwords in FormGroup', () => {
    const validator = passwordsMatchValidator('password', 'confirmPassword');
    const group = new FormGroup(
      {
        password: new FormControl('SecureP@ssw0rd2026!'),
        confirmPassword: new FormControl('Different@2026!')
      },
      { validators: [validator] }
    );

    expect(group.hasError('passwordMismatch')).toBeTrue();

    group.controls.confirmPassword.setValue('SecureP@ssw0rd2026!');
    expect(group.hasError('passwordMismatch')).toBeFalse();
  });
});

