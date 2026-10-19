package cl.duoc.pedidos360.admin.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class CognitoTokenValidator
        implements OAuth2TokenValidator<Jwt> {

    private final String clientId;

    public CognitoTokenValidator(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {

        String tokenClientId =
            jwt.getClaimAsString("client_id");

        String tokenUse =
            jwt.getClaimAsString("token_use");

        boolean clienteValido =
            clientId.equals(tokenClientId);

        boolean tipoValido =
            "access".equals(tokenUse);

        if (clienteValido && tipoValido) {
            return OAuth2TokenValidatorResult.success();
        }

        OAuth2Error error = new OAuth2Error(
            "invalid_token",
            "El token no pertenece al App Client esperado",
            null
        );

        return OAuth2TokenValidatorResult.failure(error);
    }
}