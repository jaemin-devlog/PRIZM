package com.prizm.changelog.service;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** 일시적인 DB 접근 오류와 재시도해도 달라지지 않을 전달 실패를 구분한다. */
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
