package com.secretcompany.service;

public interface ThrottlingConstants {
    String USER_ID_IS_REQUIRED = "UserId is required";
    String UNAUTHORIZED_USER_ID = "UnauthorizedUser";
    String AUTHORIZED_USER_ID = "AuthorizedUser";
    int LRU_MAX_CACHE_CAPACITY = 10000;
}
