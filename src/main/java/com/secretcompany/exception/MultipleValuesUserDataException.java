package com.secretcompany.exception;

public class MultipleValuesUserDataException extends UnExpectedExecutionException {
    public MultipleValuesUserDataException() {
        super("Multiple different Slas for single user");
    }
}
