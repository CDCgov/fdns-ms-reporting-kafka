package gov.cdc.kafka.worker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.net.URLDecoder;

import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import gov.cdc.helper.CombinerHelper;
import gov.cdc.helper.IndexingHelper;
import gov.cdc.helper.MicrosoftHelper;
import gov.cdc.helper.OAuthHelper;
import gov.cdc.helper.ObjectHelper;
import gov.cdc.helper.ResourceHelper;
import gov.cdc.helper.StorageHelper;
import gov.cdc.kafka.AbstractConsumer;

public class Combiner extends AbstractConsumer {

	private static final Logger logger = Logger.getLogger(Combiner.class);

	public static final String PROFILE_NAME = "COMBINER";

	private double PROGRESS_REQUESTINGDATA = 0.8;

	private int objectBatchSize;

	private int indexingBatchSize;
	private String indexingScroll;
	protected String authorizationHeader;

	public Combiner(String groupName, String incomingTopicName, String outgoingTopicName, String errorTopicName, String kafkaBrokers, String schemaRegistryUrl) throws IOException {
		super(groupName, incomingTopicName, outgoingTopicName, errorTopicName, kafkaBrokers, schemaRegistryUrl);
		indexingBatchSize = Integer.parseInt(ResourceHelper.getSysEnvProperty("INDEXING_BATCH_SIZE", true));
		indexingScroll = ResourceHelper.getProperty("indexing.batch.scroll");
		objectBatchSize = Integer.parseInt(ResourceHelper.getSysEnvProperty("OBJECT_BATCH_SIZE", true));
	}

	@Override
	protected String getProfileName() {
		return PROFILE_NAME;
	}

	protected File createObjectCSV(String format, String combinerConfig, String jobId, JSONObject jobObject, String database, String collection, String query) throws IOException {
		// Create a temporary csv file locally
		File csv = File.createTempFile(jobId, ".csv.tmp");
		FileOutputStream fos = null;

		// try adding to the csv file
		try {
			fos = new FileOutputStream(csv, true);
			logger.debug(String.format(" [%s]   CSV file: %s", jobId, csv.getAbsolutePath()));

			boolean includeHeader = true;
			int cursor = 0;
			int combined = 0;

			JSONObject initialJson = ObjectHelper.getInstance(authorizationHeader).search(query, database, collection, 0, 1);
			int total = initialJson.getInt("total");
			while(cursor < total) {
				JSONObject json = ObjectHelper.getInstance(authorizationHeader).search(query, database, collection, cursor, objectBatchSize);
				JSONArray items = json.getJSONArray("items");

				List<JSONObject> jsons = new ArrayList<>();

				for (int i = 0; i < items.length(); i++) {
					// Get the item
					JSONObject item = items.getJSONObject(i);
					jsons.add(item);

					combined += 1;
				}

				logger.debug(String.format(" [%s]   Collected items: %5d / %5d", jobId, combined, total));

				// Combine the json
				logger.debug(String.format(" [%s]   ... Combining", jobId));
				byte[] csvData = CombinerHelper.getInstance(authorizationHeader).combine("csv", combinerConfig, jsons, "landscape", includeHeader);
				fos.write(csvData);
				logger.debug(String.format(" [%s]   ... Done", jobId));

				// update the cursor
				cursor += objectBatchSize;

				// Update progress
				jobObject.put("progress", PROGRESS_REQUESTINGDATA * cursor + objectBatchSize);
				ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);
			}

			logger.debug(String.format(" [%s]   ... Combinging completed: %s", jobId, csv.getAbsolutePath()));
		} catch (Exception e) {
			logger.error(e);
		} finally {
			if (fos != null)
				fos.close();
		}

		return csv;
	}

	protected File createIndexingCSV(String format, String combinerConfig, String jobId, JSONObject jobObject, String indexType, String query) throws IOException {
		// Create a temporary csv file locally
		File csv = File.createTempFile(jobId, ".csv.tmp");
		FileOutputStream fos = null;

		// try adding to the csv file
		try {
			fos = new FileOutputStream(csv, true);
			logger.debug(String.format(" [%s]   CSV file: %s", jobId, csv.getAbsolutePath()));

			JSONObject json = IndexingHelper.getInstance(authorizationHeader).search(indexType, query, true, 0, indexingBatchSize, indexingScroll);
			int totalHits = json.getJSONObject("hits").getInt("total");
			String scrollId = json.getString("_scroll_id");
			logger.debug(String.format(" [%s]   Number of hits: %d", jobId, totalHits));
			logger.debug(String.format(" [%s]   Scroll ID: %s", jobId, scrollId));
			int currentStartIndex = 0;
			boolean includeHeader = true;

			while (currentStartIndex < totalHits) {
				JSONArray hits = json.getJSONObject("hits").getJSONArray("hits");

				List<JSONObject> jsons = new ArrayList<>();

				for (int i = 0; i < hits.length(); i++) {
					// Get the hit
					JSONObject hit = hits.getJSONObject(i);
					jsons.add(hit.getJSONObject("_source"));
				}

				logger.debug(String.format(" [%s]   Collected items: [ %5d ~ %5d ] / %5d", jobId, currentStartIndex, currentStartIndex + indexingBatchSize - 1, totalHits));

				// Combine the json
				logger.debug(String.format(" [%s]   ... Combining", jobId));
				byte[] csvData = CombinerHelper.getInstance(authorizationHeader).combine("csv", combinerConfig, jsons, "landscape", includeHeader);
				fos.write(csvData);
				logger.debug(String.format(" [%s]   ... Done", jobId));

				// Update the start index
				currentStartIndex += indexingBatchSize;

				// Update progress
				jobObject.put("progress", PROGRESS_REQUESTINGDATA * currentStartIndex / totalHits);
				ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);

				if (currentStartIndex < totalHits) {
					if (includeHeader)
						includeHeader = !includeHeader;
					json = IndexingHelper.getInstance(authorizationHeader).scroll(indexType, indexingScroll, scrollId, true);
				}
			}

			logger.debug(String.format(" [%s]   ... Combinging completed: %s", jobId, csv.getAbsolutePath()));
		} catch (Exception e) {
			logger.error(e);
		} finally {
			if (fos != null)
				fos.close();
		}

		return csv;
	}

	@Override
	protected void process(ConsumerRecord<String, ?> _record) {
		long recordId = 0;
		String jobId = null;
		JSONObject jobObject = null;
		String indexType = null;
		String database = null;
		String collection = null;
		File csv = null;
		try {
			ConsumerRecord<String, String> record = (ConsumerRecord<String, String>) _record;
			JSONObject data = new JSONObject(record.value().toString());
			String query = URLDecoder.decode(data.getString("query"), "UTF-8");
			String format = data.getString("format");
			String type = data.getString("type");
			String combinerConfig = data.getString("config");
			jobId = data.getString("_id");

			// check for object
			if (type.equalsIgnoreCase("object")) {
				database = data.getString("database");
				collection = data.getString("collection");
				logger.debug(String.format(" [%s] Processing record: Format = %s, Query = %s, Type = %s, Database = %s, Collection = %s", jobId, format, query, type, database, collection));
			} else {
				indexType = data.getString("index");
				logger.debug(String.format(" [%s] Processing record: Format = %s, Query = %s, Type = %s, Index = %s", jobId, format, query, type, indexType));
			}

			// Get the authorization header
			authorizationHeader = getAuthorizationHeader();

			int maxRetries = 10;
			int retry = 0;
			while (retry < maxRetries && jobObject == null)
				try {
					// Get the job object
					logger.debug(String.format(" [%s] Getting Job JSON object", jobId));
					jobObject = ObjectHelper.getInstance(authorizationHeader).getObject(jobId);
				} catch (Exception e) {
					logger.error(e);
					retry++;
				}

			if (jobObject == null)
				throw new Exception("Can't retrieve the job object from MongoDB");

			// Get the authorization header with the required scopes
			if (isOauthEnabled()) {
				if (jobObject.has("scopes")) {
					JSONArray scopes = jobObject.getJSONArray("scopes");
					List<String> scopeList = new ArrayList<>();
					for (Object scope : scopes) {
						scopeList.add((String) scope);
					}

					authorizationHeader = "Bearer " + OAuthHelper.getInstance().getToken(scopeList);
					logger.debug(String.format(" [%s]   Update authorization header with the following scopes: %s", jobId, String.join(" ", scopeList)));
					logger.debug(String.format(" [%s]   ... %s", jobId, authorizationHeader));
				}
			}

			// Create the CSV based on the type
			if (type.equalsIgnoreCase("object")) {
				csv = createObjectCSV(format, combinerConfig, jobId, jobObject, database, collection, query);
			} else {
				csv = createIndexingCSV(format, combinerConfig, jobId, jobObject, indexType, query);
			}

			logger.debug(String.format(" [%s]   ... Export completed: %s", jobId, csv.getAbsolutePath()));

			// Update progress
			jobObject.put("progress", PROGRESS_REQUESTINGDATA);
			JSONObject event = new JSONObject();
			event.put("event", "DATA_REQUESTED");
			event.put("timestamp", Instant.now().toString());
			jobObject.getJSONArray("events").put(event);
			ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);

			if ("csv".equalsIgnoreCase(format)) {
				// Save the file in S3
				logger.debug(String.format(" [%s]   Storing in S3", jobId));
				// Check if we need to export the report in a specific drawer
				JSONObject s3Node = null;
				if (data.has("export") && data.getJSONObject("export").has("drawerName")) {
					String drawerName = data.getJSONObject("export").getString("drawerName");

					// Check if the drawer exists
					try {
						StorageHelper.getInstance(authorizationHeader).getDrawer(drawerName);
					} catch (Exception e) {
						logger.debug(String.format(" [%s]   ... The drawer `%s` doesn't seem to exist in S3.", jobId, drawerName));
						try {
							// Try to create the drawer
							StorageHelper.getInstance(authorizationHeader).createDrawer(drawerName);
							logger.debug(String.format(" [%s]   ... The drawer `%s` is now created.", jobId, drawerName));
						} catch (Exception e1) {
							logger.error(e1);
							throw new Exception("Error creating the drawer in S3.");
						}
					}
					
					s3Node = StorageHelper.getInstance(authorizationHeader).createNode(drawerName, jobId + ".csv", IOUtils.toByteArray(new FileInputStream(csv)), false, false, true);
				} else {
					s3Node = StorageHelper.getInstance(authorizationHeader).createNode(jobId + ".csv", IOUtils.toByteArray(new FileInputStream(csv)), false, false);
				}
				logger.debug(String.format(" [%s]   ... Completed", jobId));

				// Update progress
				jobObject.put("progress", 1);
				jobObject.put("file", s3Node);
				jobObject.put("status", "COMPLETED");
				event = new JSONObject();
				event.put("event", "JOB_COMPLETED");
				event.put("timestamp", Instant.now().toString());
				jobObject.getJSONArray("events").put(event);
				ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);
			} else {
				jobObject.put("progress", 0.9);
				ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);

				// Create the XLSX
				logger.debug(String.format(" [%s]   Converting to XLSX", jobId));
				byte[] xlsxData = MicrosoftHelper.getInstance(authorizationHeader).convertCSVToXLSX(jobId + ".csv", IOUtils.toByteArray(new FileInputStream(csv)));
				logger.debug(String.format(" [%s]   ... Completed", jobId));

				jobObject.put("progress", 0.95);
				event = new JSONObject();
				event.put("event", "XLSX_CREATED");
				event.put("timestamp", Instant.now().toString());
				jobObject.getJSONArray("events").put(event);
				ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);

				// Save in S3
				logger.debug(String.format(" [%s]   Storing in S3", jobId));
				// Check if we need to export the report in a specific drawer
				JSONObject s3Node = null;
				if (data.has("export") && data.getJSONObject("export").has("drawerName")) {
					String drawerName = data.getJSONObject("export").getString("drawerName");
					s3Node = StorageHelper.getInstance(authorizationHeader).createNode(drawerName, jobId + ".xlsx", xlsxData, false, false, true);
				} else {
					s3Node = StorageHelper.getInstance(authorizationHeader).createNode(jobId + ".xlsx", xlsxData, false, false);
				}
				logger.debug(String.format(" [%s]   ... Completed", jobId));

				// Update progress
				jobObject.put("progress", 1);
				jobObject.put("file", s3Node);
				jobObject.put("status", "COMPLETED");
				event = new JSONObject();
				event.put("event", "JOB_COMPLETED");
				event.put("timestamp", Instant.now().toString());
				jobObject.getJSONArray("events").put(event);
				ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);
			}

			// Check if we need to delete the config
			if (data.has("deleteConfig") && data.get("deleteConfig") instanceof Boolean && data.getBoolean("deleteConfig")) {
				logger.debug(String.format(" [%s]   Deleting the combiner config", jobId));
				CombinerHelper.getInstance(authorizationHeader).deleteConfig(combinerConfig);
				logger.debug(String.format(" [%s]   ... Completed", jobId));
			}

			// Delete the temporary file
			logger.debug(String.format(" [%s]   Deleting the temporary file", jobId));
			logger.debug(String.format(" [%s]   ... Completed: %b", jobId, csv.delete()));

		} catch (Exception e) {
			logger.error(e);

			handleError(PROFILE_NAME, e, Long.toString(recordId));
			if (jobObject != null) {
				try {
					// Update the object
					jobObject.put("status", "ERROR");
					JSONObject error = new JSONObject();
					error.put("message", e.getMessage());
					error.put("cause", e.getCause());
					StackTraceElement[] stack = e.getStackTrace();
					String exception = "";
					for (StackTraceElement s : stack) {
						exception = exception + s.toString() + "\t";
					}
					error.put("exception", exception);

					jobObject.put("error", error);

					// Update MongoDB
					ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);
				} catch (Exception e2) {
					logger.error(e2);
				}
			}
		}
	}

}
