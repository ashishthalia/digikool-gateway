# OAuth2 Authorization Code Flow Implementation

## Overview

The gateway now supports OAuth2 authorization code flow for UI-based authentication. This allows users to authenticate through the browser and have their tokens stored in secure HTTP-only cookies.

## Flow Description

1. **User clicks "Login"** in the UI
2. **UI redirects to**: `GET /oauth2/authorize`
3. **Gateway generates state parameter** and stores it in a cookie
4. **Gateway redirects** to the authorization server login page
5. **User authenticates** on the authorization server
6. **Authorization server redirects back** to `GET /oauth2/callback?code=xxx&state=xxx`
7. **Gateway validates state** parameter
8. **Gateway exchanges authorization code** for access and refresh tokens
9. **Gateway sets cookies**:
   - `access_token` (HttpOnly, SameSite=Strict, 1 hour expiry)
   - `refresh_token` (HttpOnly, SameSite=Strict, 30 days expiry)
10. **Gateway redirects** user to the UI home page

## API Endpoints

### Initiate OAuth2 Flow
```
GET /oauth2/authorize
```
Redirects user to the authorization server.

**Optional Query Parameters:**
- `redirect_uri`: Custom redirect URI (optional)

### OAuth2 Callback
```
GET /oauth2/callback?code=xxx&state=xxx
```
Handles the callback from the authorization server. This is called automatically by the authorization server after user authentication.

### Logout
```
POST /oauth2/logout
```
Clears authentication cookies and logs out the user.

**Response:**
```json
{
  "message": "Successfully logged out"
}
```

### Check Authentication Status
```
GET /oauth2/status
```
Returns the current authentication status.

**Response (authenticated):**
```json
{
  "authenticated": true,
  "userId": "user123",
  "schoolId": "school456",
  "username": "john.doe",
  "userEmail": "john.doe@example.com",
  "firstName": "John"
}
```

**Response (not authenticated - no token):**
```json
{
  "authenticated": false,
  "message": "No authentication token found"
}
```

**Response (not authenticated - token invalid/expired):**
```json
{
  "authenticated": false,
  "message": "Token is invalid or expired"
}
```

## Configuration

Configure the OAuth2 settings in `application.yml`:

```yaml
oauth2:
  authorization-uri: ${OAUTH2_AUTHORIZATION_URI:http://localhost:9000/oauth2/authorize}
  token-uri: ${OAUTH2_TOKEN_URI:http://localhost:9000/oauth2/token}
  client-id: ${OAUTH2_CLIENT_ID:gateway-client}
  client-secret: ${OAUTH2_CLIENT_SECRET:secret}
  redirect-uri: ${OAUTH2_REDIRECT_URI:http://localhost:6061/oauth2/callback}
  scope: ${OAUTH2_SCOPE:openid profile email}
  cookie-domain: ${OAUTH2_COOKIE_DOMAIN:localhost}
```

### Environment Variables

You can override these values using environment variables:

- `OAUTH2_AUTHORIZATION_URI`: OAuth2 authorization endpoint
- `OAUTH2_TOKEN_URI`: OAuth2 token endpoint
- `OAUTH2_CLIENT_ID`: OAuth2 client ID
- `OAUTH2_CLIENT_SECRET`: OAuth2 client secret
- `OAUTH2_REDIRECT_URI`: Callback URL after authentication
- `OAUTH2_SCOPE`: OAuth2 scopes to request
- `OAUTH2_COOKIE_DOMAIN`: Domain for setting cookies

## Authentication Filter

The `AuthenticationFilter` has been updated to support both:
1. **Bearer tokens** in the `Authorization` header (for API clients)
2. **Cookie-based authentication** (for browser clients)

The filter checks for tokens in this order:
1. First checks the `Authorization: Bearer <token>` header
2. Then checks the `access_token` cookie

## Cookie Security

Cookies are configured with:
- `HttpOnly`: Prevents JavaScript access (XSS protection)
- `SameSite=Strict`: Prevents CSRF attacks
- `Secure`: Should be set to `true` in production with HTTPS
- Expiration: Access token expires in 1 hour, refresh token expires in 30 days

**Important:** In production, you must:
1. Set `oauth2.cookie-domain` to your actual domain
2. Enable HTTPS and set `secure=true` in the cookie configuration
3. Update the `secure` parameter in `AuthController.java` cookie creation

## Usage in UI

### Login Flow

```javascript
// Redirect to gateway's OAuth2 authorize endpoint
window.location.href = 'http://localhost:6061/oauth2/authorize';
```

### Making Authenticated Requests

```javascript
// Cookies are automatically sent by the browser
fetch('http://localhost:6061/api/students', {
  credentials: 'include'  // Important: include cookies
})
  .then(response => response.json())
  .then(data => console.log(data));
```

### Logout

```javascript
fetch('http://localhost:6061/oauth2/logout', {
  method: 'POST',
  credentials: 'include'
})
  .then(() => {
    window.location.href = '/login';
  });
```

### Check Authentication Status

```javascript
fetch('http://localhost:6061/oauth2/status', {
  credentials: 'include'
})
  .then(response => response.json())
  .then(data => {
    if (data.authenticated) {
      console.log('User is authenticated');
      console.log('User ID:', data.userId);
      console.log('Username:', data.username);
      console.log('Email:', data.userEmail);
      console.log('School ID:', data.schoolId);
    } else {
      console.log('User is not authenticated');
      console.log('Reason:', data.message);
    }
  });
```

## Backward Compatibility

The implementation maintains backward compatibility with Bearer token authentication. API clients can continue using:

```
Authorization: Bearer <token>
```

Both authentication methods work seamlessly together.
