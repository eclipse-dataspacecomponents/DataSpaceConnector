# Api Authentication Configuration

## Decision

A new extension will be introduced for configuring the `ApiAuthenticationRegistry`

## Rationale

Recently, the `ApiAuthenticationRegistry` was introduced for associating a web context to a `AuthenticationService` in
order to use different auth mechanism for different contexts. Currently though, this association is expressed
in each `AuthenticationService` extension, which makes ti difficult to apply an `AuthenticationService` to a different
context compared to the current hardcoded one.

## Approach

Each implementor of `AuthenticationService` will also implement an `ApiAuthenticationProvider` which will provide an
instance of `AuthenticationService` based on the input configuration.

```java
public interface ApiAuthenticationProvider {

    Result<AuthenticationService> provide(Config config);
}
```

Those providers can be registered in a registry `ApiAuthenticationProviderRegistry`, associated with the auth type (
basic,token, delegated, ...)

```java
public interface ApiAuthenticationProviderRegistry {

    void register(String type, ApiAuthenticationProvider provider);

    Result<ApiAuthenticationProvider> resolve(String type);
}
```

Then the new extension, leveraging the partition mechanism of EDC `web.http` config, will configure the association
between the context and the auth type in the prepare phase.

For example if a user wants to configure the `TokenBasedAuthenticationService` for the `management` context, a
configuration like this could be used:

```
web.http.management.auth.type=tokenbased
web.http.management.auth.key.alias=vaultAlias
```

For each web context the extension will read the `auth.type` if present, and will invoke the provider for that type with
the input configuration, associating then the created instance with the configured `context` in
the `ApiAuthenticationRegistry`.

> For backward compatibility we will leave in place the current hardcoded association
> context <-> `AuthenticationService`
