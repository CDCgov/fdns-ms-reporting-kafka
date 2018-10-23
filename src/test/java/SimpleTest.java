import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import java.time.Instant;
import org.junit.Test;
import java.lang.InterruptedException;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import java.util.UUID;
import org.json.JSONArray;

import gov.cdc.kafka.helper.KafkaHelper;
import gov.cdc.helper.IndexingHelper;
import gov.cdc.helper.ObjectHelper;
import gov.cdc.helper.StorageHelper;
import gov.cdc.helper.CombinerHelper;
import gov.cdc.kafka.AbstractConsumer;
import gov.cdc.kafka.worker.Archiver;
import gov.cdc.kafka.worker.Combiner;

public class SimpleTest {

	// data lake config
	static String db = "test";
	static String collection = "simple";
	static String combinerConfig = "test";
	static String indexingConfig = "test-simple";
	static String storageDrawer = "reporting";

	// default kafka config
	static String outgoingTopicName = "";
	static String errorTopicName = "reporting-error";
	static String kafkaBrokers = "kafka:29092";
	static String schemaRegistryUrl = "";

	// archiver jobs
	String objectJSONJobId = null;
	String objectXMLJobId = null;
	String indexingJSONJobId = null;
	String indexingXMLJobId = null;

	// retry config
	int maxRetries = 60;
	int retrySleep = 1;

	@BeforeClass
	static public void populateDataLake() throws Exception {
		System.out.println("Populating the data lake...");
		// create some test objects
		ObjectHelper.getInstance().createObject(new JSONObject("{ \"foo\":\"bar\", \"messages\": 50 }"), db, collection);
		ObjectHelper.getInstance().createObject(new JSONObject("{ \"foo\":\"baz\", \"messages\": 30 }"), db, collection);

		// create indexing config
		String indexingConfigData = "{\"mongo\":{\"database\":\"" + db + "\",\"collection\":\"" + collection + "\"},\"elastic\":{\"index\":\"test\",\"type\":\"simple\"},\"mapping\":{\"$unset\":[\"_id\"],\"$set\":{}},\"filters\":{}}";
		IndexingHelper.getInstance().createOrUpdateConfig(indexingConfig, new JSONObject(indexingConfigData));
		try {
			IndexingHelper.getInstance().createIndex(indexingConfig);
		} catch (Exception e) {
			System.out.println("Index already created!");
		}

		// index all object ids
		JSONObject json = ObjectHelper.getInstance().find(new JSONObject(), db, collection);
		JSONArray items = json.getJSONArray("items");
		for (int i = 0; i < items.length(); i++) {
			// Get the item
			JSONObject item = items.getJSONObject(i);
			IndexingHelper.getInstance().indexObject(indexingConfig, item.getJSONObject("_id").getString("$oid"));
		}
		
		// create combiner config
		CombinerHelper.getInstance().createOrUpdateConfig(combinerConfig, new JSONObject("{ \"file\": { \"template\": \"fooTest_$DATE$\", \"date-format\": \"YYYYMMddHHmmss\" }, \"rows\": [{ \"label\": \"foo\", \"jsonPath\": \"$.foo\" }] }"));

		System.out.println("Finished populating the data lake!");
	}

	@Test
	public void testArchiver() throws Exception {
		String groupName = "reporting-archiver-group";
		String incomingTopicName = "reporting-archiver";

		AbstractConsumer worker = new Archiver(groupName, incomingTopicName, outgoingTopicName, errorTopicName, kafkaBrokers, schemaRegistryUrl);

		// start a new thread
		Thread workerThread = new Thread(worker);
		workerThread.start();

		// wait a few seconds the thread to boot up
		TimeUnit.SECONDS.sleep(5);

		// create the jobs
		String objectJSONJobId = createJob(incomingTopicName, "objectJSONJob", "{ \"query\":\"messages%3E%3D50\", \"format\":\"json\", \"type\":\"object\", \"database\":\"" + db + "\", \"collection\":\"" + collection + "\" }");
		String objectXMLJobId = createJob(incomingTopicName, "objectXMLJob", "{ \"query\":\"\", \"format\":\"xml\", \"type\":\"object\", \"database\":\"" + db + "\", \"collection\":\"" + collection + "\" }");
		String indexingJSONJobId = createJob(incomingTopicName, "indexingJSONJob", "{ \"query\":\"\", \"format\":\"json\", \"type\":\"indexing\", \"index\":\"" + indexingConfig + "\" }");
		String indexingXMLJobId = createJob(incomingTopicName, "indexingXMLJob", "{ \"query\":\"\", \"format\":\"xml\", \"type\":\"indexing\", \"index\":\"" + indexingConfig + "\" }");

		// wait for the jobs to finish
		// after retry count expires throw an error
		int currentRetry = 0;
		boolean objectJSONCompleted = false;
		boolean objectXMLcompleted = false;
		boolean indexingJSONcompleted = false;
		boolean indexingXMLcompleted = false;
		while(currentRetry <= maxRetries) {
			if (objectJSONCompleted && objectXMLcompleted && indexingJSONcompleted && indexingXMLcompleted)
				break;

			JSONArray nodes = StorageHelper.getInstance().listNodes(storageDrawer, "");
			for (int i = 0; i < nodes.length(); i++) {
				JSONObject node = nodes.getJSONObject(i);

				// check for completed jobs
				if (node.getString("id").contains(objectJSONJobId))
					objectJSONCompleted = true;
				if (node.getString("id").contains(objectXMLJobId))
					objectXMLcompleted = true;
				if (node.getString("id").contains(indexingJSONJobId))
					indexingJSONcompleted = true;
				if (node.getString("id").contains(indexingXMLJobId))
					indexingXMLcompleted = true;
			}

			System.out.println(String.format("Waiting for jobs to complete... %5d / %5d", currentRetry, maxRetries));
			currentRetry += 1;
			TimeUnit.SECONDS.sleep(retrySleep);
		}

		assertTrue((objectJSONCompleted && objectXMLcompleted && indexingJSONcompleted && indexingXMLcompleted) == true);

		// end worker
		workerThread.interrupt();
	}

	@Test
	public void testCombiner() throws Exception {
		String groupName = "reporting-combiner-group";
		String incomingTopicName = "reporting-combiner";

		AbstractConsumer worker = new Combiner(groupName, incomingTopicName, outgoingTopicName, errorTopicName, kafkaBrokers, schemaRegistryUrl);

		// start a new thread
		Thread workerThread = new Thread(worker);
		workerThread.start();

		// wait a few seconds the thread to boot up
		TimeUnit.SECONDS.sleep(5);

		// create the jobs
		String objectCSVJobId = createJob(incomingTopicName, "objectCSVJob", "{ \"query\":\"messages>=50\", \"format\":\"csv\", \"type\":\"object\", \"database\":\"" + db + "\", \"collection\":\"" + collection + "\", \"config\": " + combinerConfig + " }");
		String objectXLSXJobId = createJob(incomingTopicName, "objectXLSXJob", "{ \"query\":\"\", \"format\":\"xlsx\", \"type\":\"object\", \"database\":\"" + db + "\", \"collection\":\"" + collection + "\", \"config\": " + combinerConfig + " }");
		String indexingCSVJobId = createJob(incomingTopicName, "indexingCSVJob", "{ \"query\":\"\", \"format\":\"csv\", \"type\":\"indexing\", \"index\":\"" + indexingConfig + "\", \"config\": " + combinerConfig + " }");
		String indexingXLSXJobId = createJob(incomingTopicName, "indexingXLSXJob", "{ \"query\":\"\", \"format\":\"xlsx\", \"type\":\"indexing\", \"index\":\"" + indexingConfig + "\", \"config\": " + combinerConfig + " }");

		// wait for the jobs to finish
		// after retry count expires throw an error
		int currentRetry = 0;
		boolean objectCSVcompleted = false;
		boolean objectXLSXcompleted = false;
		boolean indexingCSVcompleted = false;
		boolean indexingXLSXcompleted = false;
		while(currentRetry <= maxRetries) {
			if (objectCSVcompleted && objectXLSXcompleted && indexingCSVcompleted && indexingXLSXcompleted)
				break;

			JSONArray nodes = StorageHelper.getInstance().listNodes(storageDrawer, "");
			for (int i = 0; i < nodes.length(); i++) {
				JSONObject node = nodes.getJSONObject(i);

				// check for completed jobs
				if (node.getString("id").contains(objectCSVJobId))
					objectCSVcompleted = true;
				if (node.getString("id").contains(objectXLSXJobId))
					objectXLSXcompleted = true;
				if (node.getString("id").contains(indexingCSVJobId))
					indexingCSVcompleted = true;
				if (node.getString("id").contains(indexingXLSXJobId))
					indexingXLSXcompleted = true;
			}

			System.out.println(String.format("Waiting for jobs to complete... %5d / %5d", currentRetry, maxRetries));
			currentRetry += 1;
			TimeUnit.SECONDS.sleep(retrySleep);
		}

		assertTrue((objectCSVcompleted && objectXLSXcompleted && indexingCSVcompleted && indexingXLSXcompleted) == true);

		// end worker
		workerThread.interrupt();
	}

	private String createJob(String incomingTopicName, String testName, String jsonQuery) throws Exception {
		// create the request
		JSONObject request = new JSONObject(jsonQuery);

		// Create the job ID
		String jobId = UUID.randomUUID().toString();
		request.put("_id", jobId);

		// Initialize the progress
		request.put("progress", 0);
		request.put("status", "RUNNING");

		// Initialize the list of events
		JSONObject event = new JSONObject();
		event.put("timestamp", Instant.now().toString());
		event.put("event", "JOB_CREATED");
		request.put("events", new JSONArray());
		request.getJSONArray("events").put(event);

		// Push to Kafka
		KafkaHelper.getInstance().sendMessage(request.toString(), incomingTopicName);

		// Save it in Mongo
		ObjectHelper.getInstance().createObject(request, jobId);

		System.out.println("Creating " + testName + " job with ID: " + jobId);

		return jobId;
	}
}
