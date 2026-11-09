import { HttpErrorResponse } from '@angular/common/http';

export interface ApiResponse<T> {
  readonly success: boolean;
  readonly timestamp: string;
  readonly data?: T;
  readonly message?: string;
}

export interface ValidationErrorItem {
  readonly field: string;
  readonly rejectedValue?: string;
  readonly message: string;
}

export interface ApiErrorResponse {
  readonly timestamp: string;
  readonly status: number;
  readonly error: string;
  readonly message: string;
  readonly path: string;
  readonly validationErrors?: readonly ValidationErrorItem[];
}

export interface PageResponse<T> {
  readonly content: readonly T[];
  readonly pageNumber: number;
  readonly pageSize: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly isFirst: boolean;
  readonly isLast: boolean;
  readonly hasNext: boolean;
  readonly hasPrevious: boolean;
}

export function extractApiErrorMessage(error: unknown, fallbackMessage: string): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallbackMessage;
  }

  if (error.status === 0) {
    return 'Unable to reach the server. Please verify the backend service is running and try again.';
  }

  const payload = error.error as Partial<ApiErrorResponse> | string | null | undefined;
  if (payload && typeof payload === 'object') {
    if (Array.isArray(payload.validationErrors) && payload.validationErrors.length > 0) {
      const firstValidation = payload.validationErrors[0];
      if (firstValidation?.message) {
        return firstValidation.message;
      }
    }

    if (typeof payload.message === 'string' && payload.message.trim().length > 0) {
      return payload.message;
    }
  } else if (typeof payload === 'string' && payload.trim().length > 0) {
    return payload;
  }

  return fallbackMessage;
}

