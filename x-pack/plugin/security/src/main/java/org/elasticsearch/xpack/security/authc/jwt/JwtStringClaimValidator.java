/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.security.authc.jwt;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;

import org.elasticsearch.common.Strings;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.xpack.core.security.support.Automatons;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Validates a string claim against a list of allowed values. The validation is successful
 * if the claim's value matches any of the allowed values.
 * The claim's value can be either a single string or an array of strings. When it is an array
 * of string, the validation passes when any member of the string array matches any of the allowed
 * values.
 * Whether a claim's value can be an array of strings is customised with the {@link #singleValuedClaim}
 * field, which enforces the claim's value to be a single string if it is configured to {@code true}.
 */
public class JwtStringClaimValidator implements JwtFieldValidator {

    public static JwtStringClaimValidator ALLOW_ALL_SUBJECTS = new JwtStringClaimValidator("sub", List.of("*"), true);

    private final String claimName;
    @Nullable
    private final Map<String, String> fallbackClaimNames;
    private final Predicate<String> allowedClaimValuesPredicate;
    // Whether the claim should be a single string
    private final boolean singleValuedClaim;

    public JwtStringClaimValidator(String claimName, Collection<String> allowedClaimValues, boolean singleValuedClaim) {
        this(claimName, null, allowedClaimValues, singleValuedClaim);
    }

    public JwtStringClaimValidator(
        String claimName,
        Map<String, String> fallbackClaimNames,
        Collection<String> allowedClaimValues,
        boolean singleValuedClaim
    ) {
        this.claimName = claimName;
        this.fallbackClaimNames = fallbackClaimNames;
        this.allowedClaimValuesPredicate = Automatons.predicate(allowedClaimValues);
        this.singleValuedClaim = singleValuedClaim;
    }

    @Override
    public void validate(JWSHeader jwsHeader, JWTClaimsSet jwtClaimsSet) {
        final FallbackableClaim fallbackableClaim = new FallbackableClaim(claimName, fallbackClaimNames, jwtClaimsSet);
        final List<String> claimValues = getStringClaimValues(fallbackableClaim);
        if (claimValues == null) {
            throw new IllegalArgumentException("missing required string claim [" + fallbackableClaim + "]");
        }
        if (false == claimValues.stream().anyMatch(allowedClaimValuesPredicate)) {
            throw new IllegalArgumentException(
                "string claim ["
                    + fallbackableClaim
                    + "] has value ["
                    + Strings.collectionToCommaDelimitedString(claimValues)
                    + "] which does not match allowed claim values "
                    + allowedClaimValuesPredicate
            );
        }
    }

    private List<String> getStringClaimValues(FallbackableClaim fallbackableClaim) {
        if (singleValuedClaim) {
            final String claimValue = fallbackableClaim.getStringClaimValue();
            return claimValue != null ? List.of(claimValue) : null;
        } else {
            return fallbackableClaim.getStringListClaimValue();
        }
    }
}
