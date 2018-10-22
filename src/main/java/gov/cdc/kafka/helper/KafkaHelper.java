package gov.cdc.kafka.helper;

import java.io.IOException;
import java.util.Properties;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.log4j.Logger;

import gov.cdc.helper.ResourceHelper;

public class KafkaHelper {

	private static final Logger logger = Logger.getLogger(KafkaHelper.class);

	private static KafkaHelper instance;

	private String kafkaBrokers;
	private String kafkaAcknowledgments;
	private int kafkaRetries;
	private String kafkaKeySerializer;
	private String kafkaValueSerializer;

	public KafkaHelper() throws IOException {
		this.kafkaBrokers = ResourceHelper.getProperty("kafka.brokers");
		this.kafkaAcknowledgments = ResourceHelper.getProperty("kafka.acknowledgments");
		this.kafkaRetries = Integer.parseInt(ResourceHelper.getProperty("kafka.retries"));
		this.kafkaKeySerializer = ResourceHelper.getProperty("kafka.key.serializer");
		this.kafkaValueSerializer = ResourceHelper.getProperty("kafka.value.serializer");
		instance = this;
	}

	public static KafkaHelper getInstance()throws IOException {
		if (instance == null) {
			instance = createNew();
		}
		return instance;
	}

	private static KafkaHelper createNew() throws IOException {
		KafkaHelper helper = new KafkaHelper();
		
		return helper;
	}

	public void sendMessage(String data, String topic) {
		Properties props = new Properties();
		props.put("bootstrap.servers", kafkaBrokers);
		props.put("acks", kafkaAcknowledgments);
		props.put("retries", kafkaRetries);

		props.put("key.serializer", kafkaKeySerializer);
		props.put("value.serializer", kafkaValueSerializer);

		Producer<String, String> producer = new KafkaProducer<String, String>(props);

		logger.debug("Sending message to: " + topic);
		producer.send(new ProducerRecord<String, String>(topic, "message", data), new Callback() {
			@Override
			public void onCompletion(RecordMetadata metadata, Exception e) {
				if (e != null) {
					throw new KafkaException("Kafka issue", e);
				}
			}
		});
		logger.debug("... Sent!");
		producer.close();
	}

}
