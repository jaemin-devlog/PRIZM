package com.prizm.search.evaluation;

/** 개인 원문을 포함하지 않는 안전한 평가 데이터 형식 오류다. */
public class SearchEvaluationDataException extends IllegalArgumentException {

    public SearchEvaluationDataException(String message) {
        super(message);
    }

    public SearchEvaluationDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
