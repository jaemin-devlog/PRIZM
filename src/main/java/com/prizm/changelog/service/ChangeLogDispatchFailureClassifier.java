package com.prizm.changelog.service;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Dispatch retry가 가능한 일시 DB 오류와 영구 계약 오류만 구분한다. */
@Component
public class ChangeLogDispatchFailureClassifier {

    public ChangeLogDispatchFailureDisposition classify(Throwable failure) {
        if (failure instanceof DataIntegrityViolationException) {
            return ChangeLogDispatchFailureDisposition.PERMANENT;
        }
        if (failure instanceof DataAccessException) {
            return ChangeLogDispatchFailureDisposition.RETRYABLE;
        }
        return ChangeLogDispatchFailureDisposition.PERMANENT;
    }
}
