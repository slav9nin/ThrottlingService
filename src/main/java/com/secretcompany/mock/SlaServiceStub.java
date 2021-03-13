package com.secretcompany.mock;

import com.secretcompany.dto.Sla;
import com.secretcompany.service.SlaService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SlaServiceStub implements SlaService {

    private static final String TOKEN_1_1 = UUID.randomUUID().toString();
    private static final String TOKEN_1_2 = UUID.randomUUID().toString();
    private static final String TOKEN_2_1 = UUID.randomUUID().toString();
    private static final String TOKEN_2_2 = UUID.randomUUID().toString();

    private static final String USER_1 = "User1";
    private static final String USER_2 = "User2";

    private static final int USER_1_MAX_RPS = 8;
    private static final int USER_2_MAX_RPS = 16;

    private final Map<String, Sla> predefinedMapping;

    public SlaServiceStub() {
        predefinedMapping = initializeStub();
    }

    @Override
    public CompletableFuture<Sla> getSlaByToken(final String token) {
        return CompletableFuture.completedFuture(predefinedMapping.get(token));
    }

    private Map<String, Sla> initializeStub() {
        final Map<String, Sla> predefinedMapping = new HashMap<>();
        final Sla sla1 = new Sla(USER_1, USER_1_MAX_RPS);
        final Sla sla2 = new Sla(USER_2, USER_2_MAX_RPS);

        predefinedMapping.put(TOKEN_1_1, sla1);
        predefinedMapping.put(TOKEN_1_2, sla1);
        predefinedMapping.put(TOKEN_2_1, sla2);
        predefinedMapping.put(TOKEN_2_2, sla2);

        return predefinedMapping;
    }
}
