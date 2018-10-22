[![Build Status](https://travis-ci.org/CDCgov/fdns-ms-reporting-kafka.svg?branch=master)](https://travis-ci.org/CDCgov/fdns-ms-reporting-kafka)

# FDNS Reporting Kafka Workers
This is the repository with the Reporting Kafka data stream workers that package up the documents into the proper formats: JSON, XML, XLSX or CSV.

This project contains two Kafka workers that handle report generation:

* __Archiver:__ Collects data from either `fdns-ms-indexing` or `fdns-ms-object`, generates a ZIP archive, and posts it to S3
* __Combiner:__ Collects data from either `fdns-ms-indexing` or `fdns-ms-object`, generates a CSV or XLSX document, and posts it to S3

## Running locally
Carefully read the following instructions for information on how to build, run, and test this microservice in your local environment.

### Before you start
You will need to have the following software installed to run this microservice in your local environment:

- Docker, [Installation guides](https://docs.docker.com/install/)
- Docker Compose, [Installation guides](https://docs.docker.com/compose/install/)
- **Windows Users**: This project uses `Make`. Please use [Cygwin](https://www.cygwin.com/) or the [Windows Subsystem for Linux](https://docs.microsoft.com/en-us/windows/wsl/install-win10) for running the commands in this README

### Build
There are two ways to build and run these workers:

#### Building the Docker Image
If you want to run from Docker you'll need to build the Docker image. You can build the image by running the following command:

```
make docker-build
```

#### Building the JAR
The Kafka workers are contained in a JAR file. The JAR file can be compiled by running the following command:

```
sh generate-jar.sh
```

The JAR file can also be compiled using Maven:

```
mvn clean package assembly:single -DskipTests=true
```

### Run
First, you need to have all Kafka Docker containers up and running. Please look at the [docker-compose](./docker-compose.yml) file for more information. You can also use the `make up` command to set up your local environment. There is also `make down` to teardown the local environment.

When everything is up and running, you will need to run one instance of the JAR file for each worker. You can use the pre-configured bash scripts in `src/test`. (see below)

You can also run the workers in Docker using the `make docker-start` command.

#### Kafka Workers

Now, you just run the JAR file that has been generated with different values for the following environment variables:

* PROFILE_NAME
* GROUP_NAME
* INCOMING_TOPIC_NAME
* OUTGOING_TOPIC_NAME
* ERROR_TOPIC_NAME
* KAFKA_BROKERS
* SCHEMA_REGISTRY_URL
* OBJECT_URL
* STORAGE_URL
* INDEXING_URL
* COMBINER_URL
* MICROSOFT_UTILS_URL
* OAUTH_URL
* OAUTH_ENABLED
* OAUTH_CLIENTID
* OAUTH_CLIENTSECRET
* OAUTH_SCOPES
* SSL_VERIFYING_DISABLE

The best way to do that is to use the bash scripts pre-configured for you in `src/test`. If you need sample values, you can check the bash scripts too.

#### Performance Statistics

Archiving to JSON or XML

```json
{
	"query": "",
	"format": "xml",
	"type": "indexing",
	"index": "test-simple"
}
```
```
For   5 000 items: (17:58:50 --> 18:00:00) 1m10s
For  10 000 items: (19:48:14 --> 19:50:20) 2m06s
For  20 000 items: (13:31:22 --> 13:35:08) 3m46s
For  50 000 items: (14:54:19 --> 15:03:38) 9m19s
For  75 000 items: (16:12:21 --> 16:22:35) 10m14s
For 100 000 items: (11:44:17 --> 11:57:08) 12m45s
```

Combining to CSV

```json
{
	"query": "",
	"format": "csv",
	"type": "indexing",
	"index": "test-simple",
	"config": "test-simple"
}
```
```
For   5 000 items: (18:58:46 --> 19:00:34) 1m48s
For  10 000 items: (19:53:48 --> 19:57:07) 3m19s
For  20 000 items: (13:38:19 --> 13:44:03) 6m44s
For  50 000 items: (15:04:55 --> 15:19:43) 14m48s
For  75 000 items: (16:29:38 --> 16:41:48) 12m10s
For 100 000 items: (11:58:04 --> 12:17:16) 19m12s
```

Combining to XLSX

```json
{
	"query": "",
	"format": "xlsx",
	"type": "indexing",
	"index": "test-simple",
	"config": "test-simple"
}
```
```
For   5 000 items: (19:09:04 --> 19:10:52) 1m48s
For  10 000 items: (19:58:04 --> 20:02:19) 4m15s
For  20 000 items: (13:46:56 --> 13:53:20) 6m24s
For  50 000 items: (15:20:27 --> 15:35:26) 14m59s
For  75 000 items: (16:49:00 --> 17:02:01) 13m01s
For 100 000 items: (12:41:08 --> 13:01:14) 20m06s
```

## Public Domain
This repository constitutes a work of the United States Government and is not
subject to domestic copyright protection under 17 USC § 105. This repository is in
the public domain within the United States, and copyright and related rights in
the work worldwide are waived through the [CC0 1.0 Universal public domain dedication](https://creativecommons.org/publicdomain/zero/1.0/).
All contributions to this repository will be released under the CC0 dedication. By
submitting a pull request you are agreeing to comply with this waiver of
copyright interest.

## License
The repository utilizes code licensed under the terms of the Apache Software
License and therefore is licensed under ASL v2 or later.

The source code in this repository is free: you can redistribute it and/or modify it under
the terms of the Apache Software License version 2, or (at your option) any
later version.

The source code in this repository is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the Apache Software License for more details.

You should have received a copy of the Apache Software License along with this
program. If not, see https://www.apache.org/licenses/LICENSE-2.0.html.

The source code forked from other open source projects will inherit its license.


## Privacy
This repository contains only non-sensitive, publicly available data and
information. All material and community participation is covered by the
Surveillance Platform [Disclaimer](https://github.com/CDCgov/template/blob/master/DISCLAIMER.md)
and [Code of Conduct](https://github.com/CDCgov/template/blob/master/code-of-conduct.md).
For more information about CDC's privacy policy, please visit [http://www.cdc.gov/privacy.html](http://www.cdc.gov/privacy.html).

## Contributing
Anyone is encouraged to contribute to the repository by [forking](https://help.github.com/articles/fork-a-repo)
and submitting a pull request. (If you are new to GitHub, you might start with a
[basic tutorial](https://help.github.com/articles/set-up-git).) By contributing
to this project, you grant a world-wide, royalty-free, perpetual, irrevocable,
non-exclusive, transferable license to all users under the terms of the
[Apache Software License v2](https://www.apache.org/licenses/LICENSE-2.0.html) or
later.

All comments, messages, pull requests, and other submissions received through
CDC including this GitHub page are subject to the [Presidential Records Act](https://www.archives.gov/about/laws/presidential-records.html)
and may be archived. Learn more at [https://www.cdc.gov/other/privacy.html](https://www.cdc.gov/other/privacy.html).

## Records
This repository is not a source of government records, but is a copy to increase
collaboration and collaborative potential. All government records will be
published through the [CDC web site](https://www.cdc.gov).

## Notices
Please refer to [CDC's Template Repository](https://github.com/CDCgov/template)
for more information about [contributing to this repository](https://github.com/CDCgov/template/blob/master/CONTRIBUTING.md),
[public domain notices and disclaimers](https://github.com/CDCgov/template/blob/master/DISCLAIMER.md),
and [code of conduct](https://github.com/CDCgov/template/blob/master/code-of-conduct.md).
