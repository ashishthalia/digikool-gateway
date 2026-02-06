# DigiKool Gateway

A Spring Cloud Gateway implementation with OAuth2 authentication, rate limiting, and microservice routing.

## Features

- **OAuth2 Bearer Token Authentication**: Validates tokens using OAuth2 introspection
- **Token Management**: Complete token lifecycle management (generate, refresh, validate, revoke)
- **Rate Limiting**: Redis-based rate limiting per user and service
- **Microservice Routing**: Routes requests to appropriate microservices based on path
- **User Context Injection**: Extracts user information from tokens and adds headers to downstream requests
- **Circuit Breaker**: Resilience4j circuit breaker for fault tolerance
- **Spring Reactive**: Fully reactive implementation using WebFlux

## Architecture

```
Client Request → Gateway → Authentication Filter → Rate Limiting → Circuit Breaker → Microservice
```

## Services Configuration

The gateway routes requests to the following microservices:

- **Auth Service**: `/auth/**` → Auth Server
- **School Service**: `/api/schools/**` → School Microservice
- **Student Service**: `/api/students/**` → Student Microservice  
- **Teacher Service**: `/api/teachers/**` → Teacher Microservice
- **Course Service**: `/api/courses/**` → Course Microservice
- **Notification Service**: `/api/notifications/**` → Notification Microservice

## Environment Variables

### Required Environment Variables

```bash
# OAuth2 Configuration
INTROSPECTION_URI=http://localhost:9000/oauth2/introspect
INTROSPECTION_CLIENT_ID=school-app-client
INTROSPECTION_CLIENT_SECRET=secret

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Microservice URLs
AUTH_URI=http://localhost:9000
SCHOOL_URI=http://localhost:8082
STUDENT_URI=http://localhost:8083
TEACHER_URI=http://localhost:8084
COURSE_URI=http://localhost:8085
NOTIFICATION_URI=http://localhost:8086

# Rate Limiting (optional - defaults provided)
SCHOOL_RPS=20
SCHOOL_BURST=40
STUDENT_RPS=30
STUDENT_BURST=60
TEACHER_RPS=25
TEACHER_BURST=50
COURSE_RPS=35
COURSE_BURST=70
NOTIFICATION_RPS=50
NOTIFICATION_BURST=100
```

## Authentication

All requests to protected endpoints must include a Bearer token in the Authorization header:

```bash
Authorization: Bearer <access_token>
```

### Public Endpoints

The following endpoints don't require authentication:
- `/auth/**` - Authentication endpoints
- `/api/token/**` - Token management endpoints
- `/actuator/**` - Health check and monitoring
- `/__fallback/**` - Circuit breaker fallback responses

### User Context Headers

For authenticated requests, the gateway adds the following headers to downstream microservices:

- `X-User-Id`: User identifier
- `X-School-Id`: School identifier  
- `X-User-Email`: User email
- `X-Username`: Username
- `X-First-Name`: User's first name
- `X-Permissions`: JSON array of user permissions

## Rate Limiting

Rate limiting is configured per service with the following defaults:

- **School Service**: 20 RPS, 40 burst capacity
- **Student Service**: 30 RPS, 60 burst capacity
- **Teacher Service**: 25 RPS, 50 burst capacity
- **Course Service**: 35 RPS, 70 burst capacity
- **Notification Service**: 50 RPS, 100 burst capacity

Rate limiting is based on user ID when available, falling back to IP address.

## Running the Gateway

### Prerequisites

- Java 21+
- Maven 3.6+
- Redis server
- OAuth2 authorization server running

### Build and Run

```bash
# Build the project
mvn clean package

# Run the gateway
java -jar target/gateway-0.0.1-SNAPSHOT.jar

# Or with environment variables
AUTH_URI=http://localhost:9000 \
SCHOOL_URI=http://localhost:8082 \
REDIS_HOST=localhost \
java -jar target/gateway-0.0.1-SNAPSHOT.jar
```

### Docker Support

```bash
# Build Docker image
docker build -t digikool-gateway .

# Run with Docker Compose (recommended)
docker-compose up
```

## API Examples

### Authenticated Request

```bash
curl -H "Authorization: Bearer <access_token>" \
     -H "Content-Type: application/json" \
     http://localhost:8080/api/schools/123
```

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Token Management

#### Generate Token

```bash
curl -X POST http://localhost:8080/api/token/generate \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user@example.com",
    "password": "password123",
    "userId": "user123",
    "schoolId": "school456",
    "permissions": ["read", "write"]
  }'
```

#### Refresh Token

```bash
curl -X POST http://localhost:8080/api/token/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "your_refresh_token"}'
```

#### Validate Token

```bash
curl -H "Authorization: Bearer your_access_token" \
  http://localhost:8080/api/token/validate
```

#### Revoke Token

```bash
curl -X POST http://localhost:8080/api/token/revoke \
  -H "Content-Type: application/json" \
  -d '{"token": "your_access_token"}'
```

#### Get Token Info

```bash
curl -H "Authorization: Bearer your_access_token" \
  http://localhost:8080/api/token/info
```

### Metrics

```bash
curl http://localhost:8080/actuator/metrics
```

## Monitoring

The gateway exposes several monitoring endpoints:

- `/actuator/health` - Application health
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus metrics
- `/actuator/loggers` - Logger configuration

## Circuit Breaker

Each service has circuit breaker protection with fallback responses available at:

- `/__fallback/schools`
- `/__fallback/students`  
- `/__fallback/teachers`
- `/__fallback/courses`
- `/__fallback/notifications`

## Error Responses

### Authentication Errors

```json
{
  "error": "UNAUTHORIZED",
  "message": "Missing or invalid Authorization header"
}
```

### Rate Limiting Errors

```json
{
  "error": "TOO_MANY_REQUESTS", 
  "message": "Rate limit exceeded"
}
```

### Service Unavailable

```json
{
  "service": "school-service",
  "status": "degraded",
  "message": "School service is temporarily unavailable. Please try again later.",
  "timestamp": "2024-01-15T10:30:00Z",
  "code": 503
}
```

## Development

### Project Structure

```
src/main/java/co/digikool/gateway/
├── config/           # Configuration classes
├── controller/       # REST controllers (fallback)
├── filter/          # Gateway filters
├── model/           # Data models
└── service/         # Business logic services
```

### Key Components

- `AuthenticationFilter`: Validates OAuth2 tokens and extracts user context
- `RateLimitingFilter`: Implements rate limiting using Redis
- `TokenIntrospectionService`: Handles OAuth2 token introspection
- `FallbackController`: Provides fallback responses for circuit breakers

## Troubleshooting

### Common Issues

1. **Redis Connection Failed**: Ensure Redis server is running and accessible
2. **OAuth2 Introspection Failed**: Verify auth server URL and credentials
3. **Microservice Unavailable**: Check if target microservices are running
4. **Rate Limit Exceeded**: Adjust rate limiting configuration or implement backoff

### Logs

Enable debug logging for troubleshooting:

```yaml
logging:
  level:
    co.digikool.gateway: DEBUG
    org.springframework.cloud.gateway: DEBUG
```
