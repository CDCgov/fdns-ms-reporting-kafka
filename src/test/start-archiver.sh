export PROFILE_NAME=ARCHIVER
export GROUP_NAME=reporting-archiver-group
export INCOMING_TOPIC_NAME=reporting-archiver
export ERROR_TOPIC_NAME=reporting-error
export KAFKA_BROKERS=kafka:29092
export STORAGE_URL=http://localhost:8082
export OBJECT_URL=http://localhost:8083
export INDEXING_URL=http://localhost:8084
export OAUTH_URL=
export SSL_VERIFYING_DISABLE=true
export OAUTH_ENABLED=false
export OAUTH_CLIENTID=
export OAUTH_CLIENTSECRET=
export OAUTH_SCOPES=

java -jar ../../target/fdns-ms-reporting-kafka-*-jar-with-dependencies.jar
