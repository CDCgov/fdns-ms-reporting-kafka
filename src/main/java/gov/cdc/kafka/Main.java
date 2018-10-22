package gov.cdc.kafka;

import org.apache.log4j.Logger;

import gov.cdc.helper.ResourceHelper;
import gov.cdc.kafka.common.WorkerException;
import gov.cdc.kafka.worker.Archiver;
import gov.cdc.kafka.worker.Combiner;

public class Main {

	private static final Logger logger = Logger.getLogger(Main.class);

	public static void main(String args[]) {

		try {
			// Get Profile Name
			String profileName = ResourceHelper.getSysEnvProperty(ResourceHelper.CONST_ENV_VAR_PROFILE_NAME, true);
			logger.debug("Profile: " + profileName);

			// Get group name
			String groupName = ResourceHelper.getSysEnvProperty(ResourceHelper.CONST_ENV_VAR_GROUP_NAME, true);
			logger.debug("Group Name: " + groupName);

			// Incoming topic name
			String incomingTopicName = ResourceHelper.getSysEnvProperty(ResourceHelper.CONST_ENV_VAR_INCOMING_TOPIC_NAME, true);
			logger.debug("Incoming Topic Name: " + incomingTopicName);

			// Outgoing topic name
			String outgoingTopicName = ResourceHelper.getSysEnvProperty(ResourceHelper.CONST_ENV_VAR_OUTGOING_TOPIC_NAME, false);
			logger.debug("Outgoing Topic Name: " + outgoingTopicName);

			// Error topic name
			String errorTopicName = ResourceHelper.getSysEnvProperty(ResourceHelper.CONST_ENV_VAR_ERROR_TOPIC_NAME, true);
			logger.debug("Error Topic Name: " + errorTopicName);

			// Kafka brokers
			String kafkaBrokers = ResourceHelper.getSysEnvProperty(ResourceHelper.CONST_ENV_VAR_KAFKA_BROKERS, true);
			logger.debug("Kafka Brokers: " + kafkaBrokers);

			// Schema Registry
			String schemaRegistryUrl = ResourceHelper.getSysEnvProperty(ResourceHelper.CONST_ENV_VAR_SCHEMA_REGISTRY_URL, false);
			logger.debug("Schema registry URL: " + schemaRegistryUrl);

			AbstractConsumer worker;
			if (Archiver.PROFILE_NAME.equalsIgnoreCase(profileName)) {
				worker = new Archiver(groupName, incomingTopicName, outgoingTopicName, errorTopicName, kafkaBrokers, schemaRegistryUrl);
			} else if (Combiner.PROFILE_NAME.equalsIgnoreCase(profileName)) {
				worker = new Combiner(groupName, incomingTopicName, outgoingTopicName, errorTopicName, kafkaBrokers, schemaRegistryUrl);
			} else
				throw new WorkerException("The following profile name is not valid: " + profileName);
			// And start a new thread
			(new Thread(worker)).start();
		} catch (Exception e) {
			logger.error(e);
		}
	}

}
