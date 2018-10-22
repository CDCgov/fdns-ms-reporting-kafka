export PROFILE_NAME=COMBINER
export GROUP_NAME=reporting-combiner-group
export INCOMING_TOPIC_NAME=reporting-combiner
export ERROR_TOPIC_NAME=reporting-error
export KAFKA_BROKERS=kafka:29092
export STORAGE_URL=http://localhost:8082
export OBJECT_URL=http://localhost:8083
export INDEXING_URL=http://localhost:8084
export COMBINER_URL=http://localhost:8085
export MICROSOFT_UTILS_URL=http://localhost:8086
export OAUTH_URL=
export SSL_VERIFYING_DISABLE=true
export OAUTH_ENABLED=false
export OAUTH_CLIENTID=
export OAUTH_CLIENTSECRET=
export OAUTH_SCOPES=

java -jar ../../target/fdns-ms-reporting-kafka-*-jar-with-dependencies.jar
