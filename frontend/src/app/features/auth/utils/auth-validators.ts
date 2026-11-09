import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

// Matches backend RegisterRequest complexity policy verbatim
export const PASSWORD_COMPLEXITY_REGEX =
  /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!.*_?\-]).{8,64}$/;

export interface PasswordRuleResult {
  readonly minLength: boolean;
  readonly maxLength: boolean;
  readonly hasUppercase: boolean;
  readonly hasLowercase: boolean;
  readonly hasDigit: boolean;
  readonly hasSpecialChar: boolean;
  readonly passedCount: number;
  readonly strengthLabel: 'Empty' | 'Weak' | 'Fair' | 'Good' | 'Strong';
  readonly isValid: boolean;
}

export function evaluatePasswordRules(password: string): PasswordRuleResult {
  const value = password ?? '';
  const minLength = value.length >= 8;
  const maxLength = value.length <= 64;
  const hasUppercase = /[A-Z]/.test(value);
  const hasLowercase = /[a-z]/.test(value);
  const hasDigit = /[0-9]/.test(value);
  const hasSpecialChar = /[@#$%^&+=!.*_?\-]/.test(value);

  const checks = [minLength, hasUppercase, hasLowercase, hasDigit, hasSpecialChar];
  const passedCount = checks.filter(Boolean).length;
  const isValid = passedCount === 5 && maxLength;

  let strengthLabel: PasswordRuleResult['strengthLabel'] = 'Empty';
  if (value.length > 0) {
    if (passedCount <= 2) {
      strengthLabel = 'Weak';
    } else if (passedCount === 3) {
      strengthLabel = 'Fair';
    } else if (passedCount === 4 || !maxLength) {
      strengthLabel = 'Good';
    } else {
      strengthLabel = 'Strong';
    }
  }

  return {
    minLength,
    maxLength,
    hasUppercase,
    hasLowercase,
    hasDigit,
    hasSpecialChar,
    passedCount,
    strengthLabel,
    isValid
  };
}

export function passwordsMatchValidator(
  passwordControlName: string,
  confirmControlName: string
): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get(passwordControlName)?.value as string | undefined;
    const confirm = group.get(confirmControlName)?.value as string | undefined;

    if (!password || !confirm) {
      return null;
    }

    return password === confirm ? null : { passwordMismatch: true };
  };
}

