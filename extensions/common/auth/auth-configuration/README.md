# Authentication Configuration

This extension allows to secure a set of APIs grouped by a web context. It inspects
all `web.http.<context>` and if the authentication is configured it applies the `AuthenticationRequestFilter`
to the `<context>` with the chosen `AuthenticationService`. The chosen `AuthenticationService` is currently registered
in the `ApiAuthenticationRegistry`. This will be removed once the `ApiAuthenticationRegistry` will be refactored out.

## Configuration

| Key                             | Description                                                                                | Mandatory | 
|:--------------------------------|:-------------------------------------------------------------------------------------------|-----------|
| web.http.<context>.auth.type    | The type of authentication to apply to the `<context>`                                     |           | 
| web.http.<context>.auth.context | Override the name of the context in the `ApiAuthenticationRegistry` instead of `<context>` |           | 

Depending on the `web.http.<context>.auth.type` chosen, additional properties might be required in order to configure
the `AuthenticationService`.

Example of a complete configuration for a custom context with token based authentication

```properties
web.http.custom.path=/custom
web.http.custom.port=8081
web.http.custom.auth.type=tokenbased
web.http.custom.auth.key=apiKey
```