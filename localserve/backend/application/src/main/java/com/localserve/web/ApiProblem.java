package com.localserve.web;

import java.time.Instant;
import java.util.Map;

public record ApiProblem(String type, String title, int status, String code, String detail,
                         String instance, String correlationId, Instant timestamp,
                         Map<String, Object> metadata) { }
