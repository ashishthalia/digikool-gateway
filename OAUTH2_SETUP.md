# OAuth2 Client Setup

## Gateway OAuth2 Client Configuration

The Gateway needs to be registered as an OAuth2 client in your authorization server.

### Client Details

- **Client ID**: `gateway-client`
- **Client Secret**: `secret`
- **Redirect URI**: `http://localhost:6061/oauth2/callback`
- **Grant Types**: 
  - `authorization_code`
  - `refresh_token` (optional)
- **Scopes**: `openid profile email`
- **Token Endpoint Auth Method**: `client_secret_basic` or `client_secret_post`

## Spring Authorization Server Configuration Example

If you're using Spring Authorization Server, add this client:

```java
@Bean
public RegisteredClientRepository registeredClientRepository() {
    RegisteredClient oidcClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("gateway-client")
            .clientSecret("{bcrypt}$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW") // secret
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost:6061/oauth2/callback")
            .scope("openid")
            .scope("profile")
            .scope("email")
            .clientSettings(ClientSettings.builder()
                    .requireProofKey(true) // PKCE
                    .requireAuthorizationConsent(false)
                    .build())
            .build();

    return new InMemoryRegisteredClientRepository(oidcClient);
}
```

## Troubleshooting 401 Unauthorized

If you're getting a 401 error during token exchange:

1. **Verify the client exists** in your auth server
2. **Check the client secret** matches exactly
3. **Verify the redirect URI** is registered
4. **Check grant types** are enabled
5. **Check client authentication method** - must support `client_secret_basic`

## Common Issues

### Issue: "invalid_client"
- Client ID or secret is incorrect
- Solution: Verify credentials in application.yml match auth server

### Issue: "invalid_grant"
- Authorization code is invalid or expired
- Solution: Make sure code is used quickly after generation

### Issue: "redirect_uri_mismatch"
- Redirect URI doesn't match registered URI
- Solution: Ensure redirect URI is exactly `http://localhost:6061/oauth2/callback`
