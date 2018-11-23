# FDNS Reporting Kafka Workers

Foundation Services (FDNS) Reporting Kafka Workers is the Reporting Kafka data stream workers that package up the documents into the proper formats: JSON, XML, XLSX or CSV.

## Getting Started

To get started with the FDNS Reporting Kafka Workers you can use either `docker stack deploy` or `docker-compose`:

```yaml
version: '3.1'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:3.2.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 32181
      ZOOKEEPER_TICK_TIME: 2000
  kafka:
    ports:
      - "29092:29092"
    image: confluentinc/cp-kafka:3.2.0
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:32181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092
  fdns-ms-reporting-archiver:
    image: cdcgov/fdns-ms-reporting-kafka
    environment:
      PROFILE_NAME: ARCHIVER
      GROUP_NAME: reporting-archiver-group
      INCOMING_TOPIC_NAME: reporting-archiver
      ERROR_TOPIC_NAME: reporting-error
      KAFKA_BROKERS: kafka:29092
      STORAGE_URL: http://fdns-ms-storage:8082
      OBJECT_URL: http://fdns-ms-object:8083
      INDEXING_URL: http://fdns-ms-indexing:8084
  fdns-ms-reporting-combiner:
    image: cdcgov/fdns-ms-reporting-kafka
    environment:
      PROFILE_NAME: COMBINER
      GROUP_NAME: reporting-combiner-group
      INCOMING_TOPIC_NAME: reporting-combiner
      ERROR_TOPIC_NAME: reporting-error
      KAFKA_BROKERS: kafka:29092
      STORAGE_URL: http://fdns-ms-storage:8082
      OBJECT_URL: http://fdns-ms-object:8083
      INDEXING_URL: http://fdns-ms-indexing:8084
      COMBINER_URL: http://fdns-ms-combiner:8085
      MICROSOFT_UTILS_URL: http://fdns-ms-msft-utils:8086
  # NOTE: These services depend on additional FDNS containers running that may be difficult in a low memory environment
  # Please see the docker-compose.yml for more information on dependent services
```

[![Try in PWD](https://raw.githubusercontent.com/play-with-docker/stacks/master/assets/images/button.png)](http://play-with-docker.com?stack=https://raw.githubusercontent.com/CDCgov/fdns-ms-reporting-kafka/master/stack.yml)

## Source Code

Please see [https://github.com/CDCgov/fdns-ms-reporting-kafka](https://github.com/CDCgov/fdns-ms-reporting-kafka) for the fdns-ms-reporting-kafka source repository.

## Public Domain

This repository constitutes a work of the United States Government and is not subject to domestic copyright protection under 17 USC § 105. This repository is in the public domain within the United States, and copyright and related rights in the work worldwide are waived through the [CC0 1.0 Universal public domain dedication](https://creativecommons.org/publicdomain/zero/1.0/). All contributions to this repository will be released under the CC0 dedication.

## License

The repository utilizes code licensed under the terms of the Apache Software License and therefore is licensed under ASL v2 or later.

The container image in this repository is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the Apache Software License for more details.

## Privacy

This repository contains only non-sensitive, publicly available data and information. All material and community participation is covered by the Surveillance Platform [Disclaimer](https://github.com/CDCgov/template/blob/master/DISCLAIMER.md) and [Code of Conduct](https://github.com/CDCgov/template/blob/master/code-of-conduct.md).
For more information about CDC's privacy policy, please visit [http://www.cdc.gov/privacy.html](http://www.cdc.gov/privacy.html).

## Records

This repository is not a source of government records, but is a copy to increase collaboration and collaborative potential. All government records will be published through the [CDC web site](http://www.cdc.gov).