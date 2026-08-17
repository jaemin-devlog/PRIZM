package com.prizm.search.evaluation.judge;

/** Safe external-protocol failure that never includes a prompt, response body, or API key. */
public class EvidenceJudgeProtocolException extends RuntimeException {

    public EvidenceJudgeProtocolException(String message) {
        super(message);
    }

    public EvidenceJudgeProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
