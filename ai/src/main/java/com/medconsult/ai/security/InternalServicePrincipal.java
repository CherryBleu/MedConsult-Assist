package com.medconsult.ai.security;

import java.util.List;

public record InternalServicePrincipal(
        String serviceCode,
        List<String> scopes
) {
    public boolean hasScope(String requiredScope) {
        return scopes != null && scopes.contains(requiredScope);
    }
}
