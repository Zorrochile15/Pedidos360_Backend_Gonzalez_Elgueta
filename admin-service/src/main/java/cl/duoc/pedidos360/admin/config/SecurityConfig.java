package cl.duoc.pedidos360.admin.config;

import cl.duoc.pedidos360.admin.security.CognitoTokenValidator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${cognito.client-id}")
    private String clientId;


    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated()
                )

                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                );

        return http.build();
    }


    @Bean
    public JwtDecoder jwtDecoder() {

        JwtDecoder decoder =
                JwtDecoders.fromIssuerLocation(issuerUri);

        if (decoder instanceof NimbusJwtDecoder nimbusJwtDecoder) {

            OAuth2TokenValidator<Jwt> issuerValidator =
                    JwtValidators.createDefaultWithIssuer(
                            issuerUri
                    );

            OAuth2TokenValidator<Jwt> cognitoValidator =
                    new CognitoTokenValidator(
                            clientId
                    );

            nimbusJwtDecoder.setJwtValidator(
                    new DelegatingOAuth2TokenValidator<>(
                            issuerValidator,
                            cognitoValidator
                    )
            );
        }

        return decoder;
    }


    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                new CognitoGroupsConverter()
        );

        return converter;
    }


    private static class CognitoGroupsConverter
            implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {

            List<GrantedAuthority> authorities =
                    new ArrayList<>();

            List<String> groups =
                    jwt.getClaimAsStringList(
                            "cognito:groups"
                    );

            if (groups != null) {

                for (String group : groups) {

                    authorities.add(
                            new SimpleGrantedAuthority(
                                    "ROLE_" + group
                            )
                    );
                }
            }

            return authorities;
        }
    }
}