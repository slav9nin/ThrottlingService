package com.secretcompany.service.impl;

import com.secretcompany.service.AdvancedThrottlingService;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Optional;

import static com.secretcompany.service.ThrottlingConstants.USER_ID_IS_REQUIRED;

public class AdvancedThrottlingServiceImpl implements AdvancedThrottlingService {
    @Override
    public synchronized boolean isRequestAllowed(final String token, final String userId) {
        Objects.requireNonNull(userId, USER_ID_IS_REQUIRED);
        Optional<String> userToken = Optional.ofNullable(token)
                .filter(StringUtils::isNotBlank);

        //todo
        return false;
    }
}
