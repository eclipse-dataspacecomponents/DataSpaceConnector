# Adding an additional parameter to the `CatalogRequest` (Management API)

## Decision

The `POST /catalog/request` endpoint to request a catalog will receive a new optional field in the request body with
which the requesting participant (=consumer) can insert additional scopes into the DCP interaction.

## Rationale

In typical DCP interactions, the consumer derives the required scopes from the current request (e.g. policies, or
existing agreements). This does not work for a Catalog request, because there is no request context.

Further, in many dataspaces there are "default scopes", i.e. scopes that must be present on every DSP interaction, but
these are static - they can't be changed at runtime.

So if a provider offers assets, that are only available is a special (non-default) credential is presented, the consumer
must be able to attach the respective scope strings to the access token.

_NB: the information which scopes must be added, has to be conveyed out-of-band._

In other words, a consumer may know that a provider has certain assets available, but they have an access policy
constraint on them, which makes them "invisible" unless a certain credential is presented.

## Approach

The `CatalogRequest` will receive a new nullable field `additionalScopes` which is a list of scope strings (as
per [DCP Specification, Section 3.1](https://github.com/eclipse-tractusx/identity-trust/blob/main/specifications/verifiable.presentation.protocol.md#31-access-scopes)):

```json
{
  "@type": "CatalogRequest",
  "counterPartyAddress": "http://provider-address.com",
  "counterPartyId": "providerId",
  "protocol": "dataspace-protocol-http",
  "querySpec": {
    //...
  },
  "additionalScopes": [
    "org.eclipse.edc.vc.type.AdditionalCredentialType1:read",
    "org.eclipse.edc.vc.type.AdditionalCredentialType2:*"
  ]
}
```

Scope strings provided in that fashion will get added to the default scopes.

NB: the `additionalScopes` entry is _OPTIONAL_.

This feature will be added in an Alpha version of the Management API first, specifically of `3.1.0-alpha`. The
respective URL path will be `/v3.1alpha/api/management/catalog/request` as per
our [deprecation policy](https://github.com/eclipse-edc/docs/tree/main/developer/decision-records/2024-05-27-maturity-levels-deprecation-policy).
