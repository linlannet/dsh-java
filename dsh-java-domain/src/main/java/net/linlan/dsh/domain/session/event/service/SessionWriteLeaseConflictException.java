package net.linlan.dsh.domain.session.event.service;

public final class SessionWriteLeaseConflictException extends RuntimeException {

    public SessionWriteLeaseConflictException(String message) {
        super(message);
    }
}
