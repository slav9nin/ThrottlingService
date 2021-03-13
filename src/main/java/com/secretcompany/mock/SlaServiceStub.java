package com.secretcompany.mock;

import com.secretcompany.dto.Sla;
import com.secretcompany.service.SlaService;

import java.util.concurrent.CompletableFuture;

public class SlaServiceStub implements SlaService {

    @Override
    public CompletableFuture<Sla> getSlaByToken(String token) {
        throw new UnsupportedOperationException("SlaServiceStub isn't implemented yet");
    }
}
