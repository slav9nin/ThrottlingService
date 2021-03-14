package com.secretcompany.mock;

import com.secretcompany.dto.Sla;

import java.util.UUID;

public interface SlaServiceStubConstants {

    String TOKEN_1_1 = UUID.randomUUID().toString();
    String TOKEN_1_2 = UUID.randomUUID().toString();
    String TOKEN_2_1 = UUID.randomUUID().toString();
    String TOKEN_2_2 = UUID.randomUUID().toString();

    String USER_1 = "User1";
    String USER_2 = "User2";

    int USER_1_MAX_RPS = 8;
    int USER_2_MAX_RPS = 16;

    Sla USER_1_SLA = new Sla(USER_1, USER_1_MAX_RPS);
    Sla USER_2_SLA = new Sla(USER_2, USER_2_MAX_RPS);
}
