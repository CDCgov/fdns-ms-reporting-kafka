package gov.cdc.kafka.worker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.net.URLDecoder;

import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;

import gov.cdc.helper.IndexingHelper;
import gov.cdc.helper.OAuthHelper;
import gov.cdc.helper.ObjectHelper;
import gov.cdc.helper.ResourceHelper;
import gov.cdc.helper.StorageHelper;
import gov.cdc.kafka.AbstractConsumer;

public class Archiver extends AbstractConsumer {

	private static final Logger logger = Logger.getLogger(Archiver.class);

	public static final String PROFILE_NAME = "ARCHIVER";

	private double PROGRESS_REQUESTINGDATA = 0.8;

	private int objectBatchSize;

	private int indexingBatchSize;
	private String indexingScroll;
	protected String authorizationHeader;

	public Archiver(String groupName, String incomingTopicName, String outgoingTopicName, String errorTopicName, String kafkaBrokers, String schemaRegistryUrl) throws IOException {
		super(groupName, incomingTopicName, outgoingTopicName, errorTopicName, kafkaBrokers, schemaRegistryUrl);
		indexingBatchSize = Integer.parseInt(ResourceHelper.getSysEnvProperty("INDEXING_BATCH_SIZE", true));
		indexingScroll = ResourceHelper.getProperty("indexing.batch.scroll");
		objectBatchSize = Integer.parseInt(ResourceHelper.getSysEnvProperty("OBJECT_BATCH_SIZE", true));
	}

	@Override
	protected String getProfileName() {
		return PROFILE_NAME;
	}

	protected File createObjectArchive(String format, String jobId, JSONObject jobObject, String database, String collection, String query) throws IOException {
		// Create a temporary zip file locally
		File archive = File.createTempFile(jobId, ".zip.tmp");
		FileOutputStream fos = null;
		ZipOutputStream zos = null;

		// try to add to the archive
		try {
			fos = new FileOutputStream(archive);
			zos = new ZipOutputStream(fos);
			logger.debug(String.format(" [%s]   Archive file: %s", jobId, archive.getAbsolutePath()));

			int cursor = 0;
			int archived = 0;

			JSONObject initialJson = ObjectHelper.getInstance(authorizationHeader).search(query, database, collection, 0, 1);
			int total = initialJson.getInt("total");
			while(cursor < total) {
				JSONObject json = ObjectHelper.getInstance(authorizationHeader).search(query, database, collection, cursor, objectBatchSize);
				JSONArray items = json.getJSONArray("items");

				for (int i = 0; i < items.length(); i++) {
					// Get the item
					JSONObject item = items.getJSONObject(i);
					// Get the data to output
					String output = item.toString();
					if ("xml".equalsIgnoreCase(format))
					output = XML.toString(new JSONObject(output.replaceAll("\\$", "")), "source");
					// Define the filename
					String filename = item.getJSONObject("_id").getString("$oid") + "." + format;
					// Create a new entry in the zip file
					ZipEntry ze = new ZipEntry(filename);
					zos.putNextEntry(ze);
					zos.write(output.getBytes());
					zos.closeEntry();

					archived += 1;
				}

				logger.debug(String.format(" [%s]   Archived items: %5d / %5d", jobId, archived, total));

				// update the cursor
				cursor += objectBatchSize;

				// Update progress
				jobObject.put("progress", PROGRESS_REQUESTINGDATA * cursor / total);
				ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);
			}

			logger.debug(String.format(" [%s]   ... Archiving completed: %s", jobId, archive.getAbsolutePath()));
		} catch (Exception e) {
			logger.error(e);
		} finally {
			if (zos != null)
				zos.close();
			if (fos != null)
				fos.close();
		}

		return archive;
	}

	protected File createIndexingArchive(String format, String jobId, JSONObject jobObject, String indexType, String query) throws IOException {
		// Create a temporary zip file locally
		File archive = File.createTempFile(jobId, ".zip.tmp");
		FileOutputStream fos = null;
		ZipOutputStream zos = null;

		// try to add to the archive
		try {
			fos = new FileOutputStream(archive);
			zos = new ZipOutputStream(fos);
			logger.debug(String.format(" [%s]   Archive file: %s", jobId, archive.getAbsolutePath()));

			JSONObject json = IndexingHelper.getInstance(authorizationHeader).search(indexType, query, true, 0, indexingBatchSize, indexingScroll);
			int totalHits = json.getJSONObject("hits").getInt("total");
			String scrollId = json.getString("_scroll_id");
			logger.debug(String.format(" [%s]   Number of hits: %d", jobId, totalHits));
			logger.debug(String.format(" [%s]   Scroll ID: %s", jobId, scrollId));
			int currentStartIndex = 0;
			while (currentStartIndex < totalHits) {
				JSONArray hits = json.getJSONObject("hits").getJSONArray("hits");

				for (int i = 0; i < hits.length(); i++) {
					// Get the hit
					JSONObject hit = hits.getJSONObject(i);
					// Get the data to output
					String output = hit.getJSONObject("_source").toString();
					if ("xml".equalsIgnoreCase(format))
						output = XML.toString(new JSONObject(output.replaceAll("\\$", "")), "source");
					// Define the filename
					String filename = hit.getString("_id") + "." + format;
					// Create a new entry in the zip file
					ZipEntry ze = new ZipEntry(filename);
					zos.putNextEntry(ze);
					zos.write(output.getBytes());
					zos.closeEntry();
				}

				logger.debug(String.format(" [%s]   Archived items: [ %5d ~ %5d ] / %5d", jobId, currentStartIndex, currentStartIndex + indexingBatchSize - 1, totalHits));

				// Update the start index
				currentStartIndex += indexingBatchSize;

				// Update progress
				jobObject.put("progress", PROGRESS_REQUESTINGDATA * currentStartIndex / totalHits);
				ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);

				if (currentStartIndex < totalHits)
					json = IndexingHelper.getInstance(authorizationHeader).scroll(indexType, indexingScroll, scrollId, true);
			}

			// Delete the scroll
			logger.debug(String.format(" [%s]   Deleting Scroll ID", jobId));
			IndexingHelper.getInstance(authorizationHeader).deleteScrollIndex(scrollId);
			logger.debug(String.format(" [%s]   ... Completed", jobId));

			logger.debug(String.format(" [%s]   ... Archiving completed: %s", jobId, archive.getAbsolutePath()));
		} catch (Exception e) {
			logger.error(e);
		} finally {
			if (zos != null)
				zos.close();
			if (fos != null)
				fos.close();
		}

		return archive;
	}

	@Override
	protected void process(ConsumerRecord<String, ?> _record) {
		long recordId = 0;
		String jobId = null;
		JSONObject jobObject = null;
		String indexType = null;
		String database = null;
		String collection = null;
		File archive = null;
		try {
			ConsumerRecord<String, String> record = (ConsumerRecord<String, String>) _record;
			JSONObject data = new JSONObject(record.value().toString());
			String query = URLDecoder.decode(data.getString("query"), "UTF-8");
			String format = data.getString("format");
			String type = data.getString("type");
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

			// Create the archive based on the type
			if (type.equalsIgnoreCase("object")) {
				archive = createObjectArchive(format, jobId, jobObject, database, collection, query);
			} else {
				archive = createIndexingArchive(format, jobId, jobObject, indexType, query);
			}

			// Update progress
			jobObject.put("progress", PROGRESS_REQUESTINGDATA);
			JSONObject event = new JSONObject();
			event.put("event", "DATA_REQUESTED");
			event.put("timestamp", Instant.now().toString());
			jobObject.getJSONArray("events").put(event);
			ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);

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

				s3Node = StorageHelper.getInstance(authorizationHeader).createNode(drawerName, jobId + ".zip", IOUtils.toByteArray(new FileInputStream(archive)), false, false, true);
			} else {
				s3Node = StorageHelper.getInstance(authorizationHeader).createNode(jobId + ".zip", IOUtils.toByteArray(new FileInputStream(archive)), false, false);
			}
			logger.debug(String.format(" [%s]   ... Completed", jobId));

			// Delete the temporary file
			logger.debug(String.format(" [%s]   Deleting the temporary file", jobId));
			logger.debug(String.format(" [%s]   ... Completed: %b", jobId, archive.delete()));

			// Update progress
			jobObject.put("progress", 1);
			jobObject.put("file", s3Node);
			jobObject.put("status", "COMPLETED");
			event = new JSONObject();
			event.put("event", "JOB_COMPLETED");
			event.put("timestamp", Instant.now().toString());
			jobObject.getJSONArray("events").put(event);
			ObjectHelper.getInstance(authorizationHeader).updateObject(jobId, jobObject);

		} catch (Exception e) {
			handleError(PROFILE_NAME, e, Long.toString(recordId));
			logger.error(e);

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
