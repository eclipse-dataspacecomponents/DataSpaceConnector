# EndToEnd Transfer Test

This tests verifies some complete scenarios of contract negotiation and data transfer between a consumer and a provider.

## Modules detail

* [control-plane](./control-plane): it's responsible for handling the contract negotiation phase using in-memory persistence
* [data-plane](./data-plane): it handles the data transfer phase acting as a proxy
* [runner](./runner): it contains the test implementation

## Postgresql

To run the `EndToEndTransferPostgresqlTest` you need to spin the postgresql container:

```shell
docker run --rm --name edc-postgres -e POSTGRES_PASSWORD=password -p 5432:5432 -d postgres
```

## CosmosDB

Check the instructions about how to run a local cosmos-emulator instance in the [azure-test README](../../extensions/azure/azure-test/README.md)

## Kafka Transfer tests

To run the `KafkaTransferTest` you need to spin a kafka cluster first:

```shell
docker run -p 9092:9092 -d bashj79/kafka-kraft
```
