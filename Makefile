docker-build:
	make up
	docker build \
		-t fdns-ms-reporting-kafka \
		--network=fdns-ms-reporting-kafka_default \
		--build-arg PROFILE_NAME= \
		--build-arg GROUP_NAME= \
		--build-arg INCOMING_TOPIC_NAME= \
		--build-arg OUTGOING_TOPIC_NAME= \
		--build-arg ERROR_TOPIC_NAME=reporting-error \
		--build-arg KAFKA_BROKERS=kafka:29092 \
		--build-arg SCHEMA_REGISTRY_URL= \
		--build-arg STORAGE_URL=http://fdns-ms-storage:8082 \
		--build-arg OBJECT_URL=http://fdns-ms-object:8083 \
		--build-arg INDEXING_URL=http://fdns-ms-indexing:8084 \
		--build-arg COMBINER_URL=http://fdns-ms-combiner:8085 \
		--build-arg MICROSOFT_UTILS_URL=http://fdns-ms-msft-utils:8086 \
		--build-arg OBJECT_BATCH_SIZE=1000 \
		--build-arg INDEXING_BATCH_SIZE=1000 \
		--build-arg OAUTH_URL= \
		--build-arg OAUTH_ENABLED=false \
		--build-arg OAUTH_CLIENTID= \
		--build-arg OAUTH_CLIENTSECRET= \
		--build-arg OAUTH_SCOPES= \
		--build-arg SSL_VERIFYING_DISABLE=false \
		--rm \
		.
	make down

# run from docker
docker-run: docker-start
docker-start:
	make up
	docker run -d \
		--network=fdns-ms-reporting-kafka_default  \
		--name=fdns-ms-reporting-kafka_archiver \
		-e PROFILE_NAME=ARCHIVER \
		-e GROUP_NAME=reporting-archiver-group \
		-e INCOMING_TOPIC_NAME=reporting-archiver \
		-e ERROR_TOPIC_NAME=reporting-error \
		-e KAFKA_BROKERS=kafka:29092 \
		-e STORAGE_URL=http://fdns-ms-storage:8082 \
		-e OBJECT_URL=http://fdns-ms-object:8083 \
		-e INDEXING_URL=http://fdns-ms-indexing:8084 \
		-e OAUTH_URL= \
		-e SSL_VERIFYING_DISABLE=true \
		-e OAUTH_ENABLED=false \
		-e OAUTH_CLIENTID= \
		-e OAUTH_CLIENTSECRET= \
		-e OAUTH_SCOPES= \
		fdns-ms-reporting-kafka
	docker run -d \
		--network=fdns-ms-reporting-kafka_default  \
		--name=fdns-ms-reporting-kafka_combiner \
		-e PROFILE_NAME=COMBINER \
		-e GROUP_NAME=reporting-combiner-group \
		-e INCOMING_TOPIC_NAME=reporting-combiner \
		-e ERROR_TOPIC_NAME=reporting-error \
		-e KAFKA_BROKERS=kafka:29092 \
		-e STORAGE_URL=http://fdns-ms-storage:8082 \
		-e OBJECT_URL=http://fdns-ms-object:8083 \
		-e INDEXING_URL=http://fdns-ms-indexing:8084 \
		-e COMBINER_URL=http://fdns-ms-combiner:8085 \
		-e MICROSOFT_UTILS_URL=http://fdns-ms-msft-utils:8086 \
		-e OAUTH_URL= \
		-e SSL_VERIFYING_DISABLE=true \
		-e OAUTH_ENABLED=false \
		-e OAUTH_CLIENTID= \
		-e OAUTH_CLIENTSECRET= \
		-e OAUTH_SCOPES= \
		fdns-ms-reporting-kafka

# stop from docker
docker-stop:
	docker stop fdns-ms-reporting-kafka_archiver || true
	docker stop fdns-ms-reporting-kafka_combiner || true
	docker rm fdns-ms-reporting-kafka_archiver || true
	docker rm fdns-ms-reporting-kafka_combiner || true
	make down

# setup everything
up:
	docker-compose up -d
	make wait
	make setup

# teardown everything
down:
	docker-compose down

# wait for the services to be up
wait:
	printf 'Waiting for fdns-ms-storage\n'
	until `curl --output /dev/null --silent --head --fail http://localhost:8082`; do printf '.'; sleep 1; done
	printf 'Waiting for fdns-ms-object\n'
	until `curl --output /dev/null --silent --head --fail http://localhost:8083`; do printf '.'; sleep 1; done
	printf 'Waiting for fdns-ms-indexing\n'
	until `curl --output /dev/null --silent --head --fail http://localhost:8084`; do printf '.'; sleep 1; done
	printf 'Waiting for fdns-ms-combiner\n'
	until `curl --output /dev/null --silent --head --fail http://localhost:8085`; do printf '.'; sleep 1; done
	printf 'Waiting for fdns-ms-msft-utils\n'
	until `curl --output /dev/null --silent --head --fail http://localhost:8086`; do printf '.'; sleep 1; done

# setup docker-compose and tests
setup:
	make setup-minio-buckets || true
	make setup-kafka-topics || true

# setup minio buckets
setup-minio-buckets:
	docker run \
		--network=fdns-ms-reporting-kafka_default \
		--entrypoint=/bin/sh \
		minio/mc \
		-c "mc config host add local http://minio:9000 minio minio123 && mc mb local/reporting"		

# setup kafka topics
setup-kafka-topics:
	docker exec -it fdns-ms-reporting-kafka_kafka_1 /usr/bin/kafka-topics --create --zookeeper zookeeper:32181 --replication-factor 1 --partitions 1 --topic reporting-archiver-group
	docker exec -it fdns-ms-reporting-kafka_kafka_1 /usr/bin/kafka-topics --create --zookeeper zookeeper:32181 --replication-factor 1 --partitions 1 --topic reporting-archiver
	docker exec -it fdns-ms-reporting-kafka_kafka_1 /usr/bin/kafka-topics --create --zookeeper zookeeper:32181 --replication-factor 1 --partitions 1 --topic reporting-combiner-group
	docker exec -it fdns-ms-reporting-kafka_kafka_1 /usr/bin/kafka-topics --create --zookeeper zookeeper:32181 --replication-factor 1 --partitions 1 --topic reporting-combiner

# test with sonarqube
sonarqube:
	make up
	docker run -d --name sonarqube -p 9001:9000 -p 9092:9092 sonarqube || true
	mvn clean test sonar:sonar
	make down
