package net.linlan.dsh.cases.orchestration;

/**
 * Standard checked-exception boundary for case orchestration.
 */
public class CaseExecutionException extends RuntimeException {

    public CaseExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
