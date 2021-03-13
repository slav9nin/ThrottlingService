package com.secretcompany.exception;

public class NullableUserDataException extends UnExpectedExecutionException {
    public NullableUserDataException() {
        super("User Data and Sla cannot be nullable");
    }
}
