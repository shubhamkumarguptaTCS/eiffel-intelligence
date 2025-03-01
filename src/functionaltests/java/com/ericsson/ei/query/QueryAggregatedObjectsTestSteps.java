package com.ericsson.ei.query;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import com.ericsson.ei.controller.EntryPointConstantsUtils;
import com.ericsson.ei.mongo.MongoCondition;
import com.ericsson.ei.mongo.MongoDBHandler;
import com.ericsson.ei.utils.FunctionalTestBase;
import com.ericsson.ei.utils.HttpRequest;
import com.ericsson.ei.utils.HttpRequest.HttpMethod;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;

@Ignore
@TestPropertySource(properties = {
        "spring.data.mongodb.database: QueryAggregatedObjectsTestSteps",
        "failed.notifications.collection.name: QueryAggregatedObjectsTestSteps-failedNotifications",
        "rabbitmq.exchange.name: QueryAggregatedObjectsTestSteps-exchange",
        "rabbitmq.queue.suffix: QueryAggregatedObjectsTestSteps" })
@AutoConfigureMockMvc
public class QueryAggregatedObjectsTestSteps extends FunctionalTestBase {

    private static final String CONTENT_TYPE = "application/json";

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryAggregatedObjectsTestSteps.class);

    private static final String AGGREGATED_OBJ_JSON_PATH = "src/test/resources/AggregatedDocumentInternalCompositionLatest.json";
    private static final String FAILED_NOTIFICATION_JSON_PATH = "src/test/resources/FailedNotification.json";
    private static final String QUERY_1_FILE_NAME = "src/functionaltests/resources/queryAggregatedObject1.json";
    private static final String QUERY_2_FILE_NAME = "src/functionaltests/resources/queryAggregatedObject2.json";
    private static final String QUERY_3_FILE_NAME = "src/functionaltests/resources/queryAggregatedObject3.json";
    private static final String QUERY_4_FILE_NAME = "src/functionaltests/resources/queryAggregatedObject4.json";
    private static final String QUERY_5_FILE_NAME = "src/functionaltests/resources/queryAggregatedObject5.json";
    private static final String QUERY_6_FILE_NAME = "src/functionaltests/resources/queryAggregatedObject6.json";
    private static final String QUERY_7_FILE_NAME = "src/functionaltests/resources/queryAggregatedObject7.json";
    private static final String QUERY_8_FILE_NAME = "src/functionaltests/resources/queryAggregatedObject8.json";

    final static private String ENDPOINT_QUERY_AGGREGATED_OBJECT = "/queryAggregatedObject";
    final static private String ENDPOINT_QUERY = "/query";
    final static private String ENDPOINT_FAILED_NOTIFICATIONS = "/failed-notifications";

    @LocalServerPort
    private int applicationPort;
    private String hostName = getHostName();
    private ResponseEntity response;

    @Autowired
    private MongoDBHandler mongoDBHandler;

    @Value("${spring.data.mongodb.database}")
    private String database;

    @Value("${aggregations.collection.name}")
    private String aggrCollectionName;

    @Value("${failed.notifications.collection.name}")
    private String failedNotificationCollectionName;

    private String aggrObj;
    private String failedNotificationObj;

    private ObjectMapper objMapper;

    final static private String ENTRY_POINT_QUERY = "/aggregated-objects/query";
    final static private String ENTRY_POINT_FAILED_NOTIFICATIONS = "/failed-notifications";
    final static private String FAILED_NOTIFICATIONS_PARAM = "subscriptionNames";

    public QueryAggregatedObjectsTestSteps() {
        objMapper = new ObjectMapper();
        objMapper.configure(Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

        try {
            aggrObj = FileUtils.readFileToString(new File(AGGREGATED_OBJ_JSON_PATH), "UTF-8");
            failedNotificationObj = FileUtils.readFileToString(
                    new File(FAILED_NOTIFICATION_JSON_PATH), "UTF-8");

        } catch (IOException e) {
            LOGGER.error(
                    "Failed to open test json files for test. Error message\n: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Given("^Aggregated object is created$")
    public void aggregated_object_is_created() throws Throwable {
        final MongoCondition condition = MongoCondition.idCondition(
                "6acc3c87-75e0-4b6d-88f5-b1a5d4e62b43");
        List<String> aggregatedObject = mongoDBHandler.find(database, aggrCollectionName,
                condition);

        boolean aggregatedObjectExists = aggregatedObject.size() > 0;
        if (!aggregatedObjectExists) {
            createDocumentInMongoDb(database, aggrCollectionName, aggrObj);
        }
    }

    @Given("^Failed Notification object is created$")
    public void failed_notification_object_is_created() throws Throwable {
        LOGGER.debug("Failed Notification object has been created in MongoDb");
        createDocumentInMongoDb(database, failedNotificationCollectionName,
                failedNotificationObj);
    }

    @Then("^Perform valid query on created Aggregated object")
    public void perform_valid_query_on_newly_created_aggregated_object() throws Throwable {
        final String expectedTestCaseFinishedEventId = "cb9d64b0-a6e9-4419-8b5d-a650c27c1111";
        final String documentId = "6acc3c87-75e0-4b6d-88f5-b1a5d4e62b43";
        LOGGER.debug("Got AggregateObject actual DocumentId after querying MongoDB: " + documentId);
        HttpRequest getRequest = new HttpRequest(HttpMethod.GET);
        response = getRequest.setPort(applicationPort)
                             .setHost(hostName)
                             .addHeader("content-type", CONTENT_TYPE)
                             .addHeader("Accept", CONTENT_TYPE)
                             .setEndpoint(EntryPointConstantsUtils.AGGREGATED_OBJECTS + "/" + documentId).performRequest();

        LOGGER.debug("Response of /aggregated-objects RestApi, Status Code: " + response.getStatusCodeValue()
                + "\nResponse: " + response.getBody().toString());

        JsonNode jsonNodeResult = objMapper.readValue(response.getBody().toString(),
                JsonNode.class);
        JsonNode responseEntityNode = jsonNodeResult.get("queryResponseEntity");

        String actualTestCaseFinishedEventId = responseEntityNode.get("testCaseExecutions")
                                                                 .get(0)
                                                                 .get("testCaseFinishedEventId")
                                                                 .asText();

        assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
        assertEquals(
                "Failed to compare actual Aggregated Object TestCaseFinishedEventId:\n"
                        + actualTestCaseFinishedEventId
                        + "\nwith expected Aggregated Object TestCaseFinishedEventId:\n"
                        + expectedTestCaseFinishedEventId,
                expectedTestCaseFinishedEventId, actualTestCaseFinishedEventId);
    }

    @And("^Perform an invalid query on same Aggregated object$")
    public void perform_invalid_query_on_created_aggregated_object() throws Throwable {
        final String invalidDocumentId = "6acc3c87-75e0-4aaa-88f5-b1a5d4e6cccc";
        final String expectedResponse = "";

        LOGGER.debug(
                "Trying an invalid query on /queryAggregatedObject RestApi with invalid documentId: "
                        + invalidDocumentId);
        HttpRequest getRequest = new HttpRequest(HttpMethod.GET);
        response = getRequest.setPort(applicationPort)
                             .setHost(hostName)
                             .addHeader("content-type", CONTENT_TYPE)
                             .addHeader("Accept", CONTENT_TYPE)
                             .setEndpoint(EntryPointConstantsUtils.AGGREGATED_OBJECTS + "/" + invalidDocumentId)
                             .performRequest();

        String responseAsString = response.getBody().toString();
        int responseStatusCode = response.getStatusCodeValue();
        LOGGER.debug("Response of /queryAggregatedObject RestApi, Status Code: "
                + responseStatusCode + "\nResponse: "
                + responseAsString);

        assertEquals(HttpStatus.NO_CONTENT.value(), responseStatusCode);
        assertEquals("Differences between actual Aggregated Object:\n" + responseAsString
                + "\nand expected Aggregated Object:\n" + expectedResponse, expectedResponse,
                responseAsString);
    }

    @Then("^Perform several valid freestyle queries on created Aggregated objects$")
    public void perform_several_valid_freestyle_queries_on_created_Aggregated_objects()
            throws Throwable {

        final String expectedAggrId = "6acc3c87-75e0-4b6d-88f5-b1a5d4e62b43";

        String query1 = FileUtils.readFileToString(new File(QUERY_1_FILE_NAME), "UTF-8");
        String query2 = FileUtils.readFileToString(new File(QUERY_2_FILE_NAME), "UTF-8");
        String query3 = FileUtils.readFileToString(new File(QUERY_8_FILE_NAME), "UTF-8");

        List<String> queries = new ArrayList<>();
        queries.add(query1);
        queries.add(query2);
        queries.add(query3);

        for (String query : queries) {
            LOGGER.debug("Freestyle querying for the AggregatedObject with criteria: " + query);
            JsonNode queryJson = objMapper.readValue(query, JsonNode.class);
            String formattedQuery = queryJson.toString();
            HttpRequest postRequest = new HttpRequest(HttpMethod.POST);
            response = postRequest.setPort(applicationPort)
                                  .setHost(hostName)
                                  .addHeader("content-type", CONTENT_TYPE)
                                  .addHeader("Accept", CONTENT_TYPE)
                                  .setEndpoint(ENTRY_POINT_QUERY)
                                  .setBody(formattedQuery)
                                  .performRequest();

            LOGGER.debug("Response of /query RestApi, Status Code: " + response.getStatusCodeValue()
                    + "\nResponse: "
                    + response.getBody().toString());

            JsonNode jsonNodeResult = objMapper.readValue(response.getBody().toString(),
                    JsonNode.class);
            JsonNode aggrObjResponse = objMapper.readValue(jsonNodeResult.get(0).toString(),
                    JsonNode.class);

            String actualAggrObjId = aggrObjResponse.get("id").asText();
            LOGGER.debug("AggregatedObject id from Response: " + actualAggrObjId);

            assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
            assertEquals(
                    "Failed to compare actual Aggregated Object Id:\n" + actualAggrObjId
                            + "\nwith expected Aggregated Object Id:\n" + expectedAggrId,
                    expectedAggrId, actualAggrObjId);
        }
    }

    @And("^Perform an invalid freestyle query on Aggregated object$")
    public void perform_invalid_freestyle_query_on_created_aggregated_object() throws Throwable {
        final String invalidAggrId = "6acc3c87-75e0-4b6d-88f5-b1aee4e62b43";
        final String queryAggrObj = "{\"criteria\" :{\"aggregatedObject.id\" : \"" + invalidAggrId
                + "\" }}";
        final String expectedResponse = "";

        LOGGER.debug("Trying an invalid query on /query RestApi with invalid criteria query: "
                + queryAggrObj);

        HttpRequest getRequest = new HttpRequest(HttpMethod.POST);
        response = getRequest.setPort(applicationPort)
                             .setHost(hostName)
                             .addHeader("content-type", CONTENT_TYPE)
                             .addHeader("Accept", CONTENT_TYPE)
                             .setEndpoint(ENTRY_POINT_QUERY)
                             .setBody(queryAggrObj)
                             .performRequest();

        String responseAsString = response.getBody().toString();
        int responseStatusCode = response.getStatusCodeValue();
        LOGGER.debug(
                "Response of /query RestApi, Status Code: " + responseStatusCode + "\nResponse: "
                        + responseAsString);

        assertEquals(HttpStatus.NO_CONTENT.value(), responseStatusCode);
        assertEquals("Differences between actual Aggregated Object:\n" + responseAsString
                + "\nand expected Aggregated Object:\n" + expectedResponse, expectedResponse,
                responseAsString);
    }

    @And("^Perform a query for failed notification$")
    public void perform_a_query_for_failed_notification() throws Throwable {

        final String subscriptionName = "Subscription_1";
        final String expectedTestCaseStartedEventId = "cb9d64b0-a6e9-4419-8b5d-a650c27c59ca";

        LOGGER.debug("Check if Failed Notification and " + subscriptionName + " exist in Database");
        final MongoCondition queryRequest = MongoCondition.subscriptionNameCondition(
                subscriptionName);
        String subscriptionNameCheck = objMapper.readValue(
                mongoDBHandler.find(database,
                        failedNotificationCollectionName, queryRequest)
                              .get(0),
                JsonNode.class).get("subscriptionName").asText();
        assertEquals(
                "Expected subscriptionName in failed notification in Database is not as expected.",
                subscriptionName, subscriptionNameCheck);

        LOGGER.debug("Trying to query /failed-notifications RestApi with subscriptionName: "
                + subscriptionName);

        HttpRequest getRequest = new HttpRequest(HttpMethod.GET);
        response = getRequest.setPort(applicationPort)
                             .setHost(hostName)
                             .addHeader("content-type", CONTENT_TYPE)
                             .addHeader("Accept", CONTENT_TYPE)
                             .setEndpoint(ENTRY_POINT_FAILED_NOTIFICATIONS)
                             .addParam(FAILED_NOTIFICATIONS_PARAM, subscriptionName)
                             .performRequest();

        String responseAsString = response.getBody().toString();
        int responseStatusCode = response.getStatusCodeValue();
        LOGGER.debug("Response of /failed-notifications RestApi, Status Code: " + responseStatusCode
                + "\nResponse: " + responseAsString);

        JsonNode jsonNodeResult = objMapper.readValue(response.getBody().toString(),
                JsonNode.class);
        String actualTestCaseStartedEventId = jsonNodeResult.get(0)
                                                            .get("aggregatedObject")
                                                            .get("testCaseExecutions")
                                                            .get(0)
                                                            .get("testCaseStartedEventId")
                                                            .asText();
        assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
        assertEquals(
                "Differences between actual Failed Notification response TestCaseStartedEventId:\n"
                        + actualTestCaseStartedEventId
                        + "\nand expected  Failed Notification response TestCaseStartedEventId:\n"
                        + expectedTestCaseStartedEventId,
                expectedTestCaseStartedEventId, actualTestCaseStartedEventId);

    }

    @And("^Check failed notification has been returned$")
    public void check_failed_notification_has_been_returned() throws Throwable {
        final String expectedResponse = "{\"queryResponseEntity\":{}}";
        final String subscriptionName = "Subscription_1";

        LOGGER.debug(
                "Trying to query /failed-notifications RestApi one more time with subscriptionName: "
                        + subscriptionName);

        HttpRequest getRequest = new HttpRequest(HttpMethod.GET);
        response = getRequest.setPort(applicationPort)
                             .setHost(hostName)
                             .addHeader("content-type", CONTENT_TYPE)
                             .addHeader("Accept", CONTENT_TYPE)
                             .setEndpoint(ENTRY_POINT_FAILED_NOTIFICATIONS)
                             .addParam("SubscriptionName", subscriptionName)
                             .performRequest();

        String responseAsString = response.getBody().toString();
        int responseStatusCode = response.getStatusCodeValue();
        LOGGER.debug("Response of /failed-notifications RestApi, Status Code: " + responseStatusCode
                + "\nResponse: " + responseAsString);

        assertEquals(HttpStatus.OK.value(), responseStatusCode);
        assertEquals(
                "Differences between actual Failed Notification response:\n" + responseAsString
                        + "\nand expected  Failed Notification response:\n" + expectedResponse,
                expectedResponse, responseAsString);
    }

    @And("^Perform a query on created Aggregated object with filter$")
    public void perform_valid_query_and_filter_on_aggregated_object() throws Throwable {
        final String expectedResponse = "[{\"6acc3c87-75e0-4b6d-88f5-b1a5d4e62b43\":\"\\\"33d05e6f-9bd9-4138-83b6-e20cc74680a3\\\"\"}]";

        String query1 = FileUtils.readFileToString(new File(QUERY_3_FILE_NAME), "UTF-8");

        List<String> queries = new ArrayList<>();
        queries.add(query1);

        for (String query : queries) {
            LOGGER.debug("Freestyle querying for the AggregatedObject with criteria: " + query);
            HttpRequest postRequest = new HttpRequest(HttpMethod.POST);
            response = postRequest.setPort(applicationPort)
                                  .setHost(hostName)
                                  .addHeader("content-type", CONTENT_TYPE)
                                  .addHeader("Accept", CONTENT_TYPE)
                                  .setEndpoint(ENTRY_POINT_QUERY)
                                  .setBody(query)
                                  .performRequest();

            LOGGER.debug("Response of /query RestApi, Status Code: " + response.getStatusCodeValue()
                    + "\nResponse: "
                    + response.getBody().toString());

            String responseAsString = response.getBody().toString();
            int responseStatusCode = response.getStatusCodeValue();

            assertEquals(HttpStatus.OK.value(), responseStatusCode);
            assertEquals("Failed to compare actual response:\n" + responseAsString
                    + "\nwith expected response:\n"
                    + expectedResponse, expectedResponse, responseAsString);
        }
    }

    @And("^Perform a query and filter with part of path$")
    public void perform__query_and_filter_with_part_of_path() throws Throwable {
        final String expectedResponse = "[{\"6acc3c87-75e0-4b6d-88f5-b1a5d4e62b43\":\"\\\"[5005, 1481875921843, 1481875988767, 2000, 1481875921763, 1481875944272, 1481875891763]\\\"\"}]";
        final String expectedResponse2 = "[{\"6acc3c87-75e0-4b6d-88f5-b1a5d4e62b43\":\"\\\"null\\\"\"}]";
        final String expectedResponse3 = "[{\"6acc3c87-75e0-4b6d-88f5-b1a5d4e62b43\":\"\\\"[33d05e6f-9bd9-4138-83b6-e20cc74680a3, 33d05e6f-9bd9-4138-83b6-e20cc74681b5]\\\"\"}]";

        List<String> expectedResponses = new ArrayList<String>();
        expectedResponses.add(expectedResponse);
        expectedResponses.add(expectedResponse2);
        expectedResponses.add(expectedResponse3);

        String query1 = FileUtils.readFileToString(new File(QUERY_4_FILE_NAME), "UTF-8");
        String query2 = FileUtils.readFileToString(new File(QUERY_5_FILE_NAME), "UTF-8");
        String query3 = FileUtils.readFileToString(new File(QUERY_6_FILE_NAME), "UTF-8");

        List<String> queries = new ArrayList<>();
        queries.add(query1);
        queries.add(query2);
        queries.add(query3);

        int pos = 0;
        for (String query : queries) {
            LOGGER.debug("Freestyle querying for the AggregatedObject with criteria: " + query);
            HttpRequest postRequest = new HttpRequest(HttpMethod.POST);
            response = postRequest.setPort(applicationPort)
                                  .setHost(hostName)
                                  .addHeader("content-type", CONTENT_TYPE)
                                  .addHeader("Accept", CONTENT_TYPE)
                                  .setEndpoint(ENTRY_POINT_QUERY)
                                  .setBody(query)
                                  .performRequest();

            LOGGER.debug("Response of /query RestApi, Status : " + response.getStatusCodeValue()
                    + "\nResponse: "
                    + response.getBody().toString());

            String responseAsString = response.getBody().toString();
            int responseStatusCode = response.getStatusCodeValue();

            assertEquals(HttpStatus.OK.value(), responseStatusCode);
            assertEquals(
                    "Failed to compare actual response:\n" + responseAsString
                            + "\nwith expected response:\n"
                            + expectedResponses.get(pos),
                    expectedResponses.get(pos), responseAsString);
            pos++;
        }
    }

    @Then("^perform query to retrieve and filter out confidence level information$")
    public void perform_query_to_retrieve_and_filter_out_confidence_level_information()
            throws Throwable {
        final String aggrId = "6acc3c87-75e0-4b6d-88f5-b1a5d4e62b43";

        String query = FileUtils.readFileToString(new File(QUERY_7_FILE_NAME), "UTF-8");

        LOGGER.debug("Freestyle querying for the AggregatedObject with criteria: " + query);

        JsonNode queryJson = objMapper.readValue(query, JsonNode.class);
        String formattedQuery = queryJson.toString();

        HttpRequest postRequest = new HttpRequest(HttpMethod.POST);
        response = postRequest.setPort(applicationPort)
                              .setHost(hostName)
                              .addHeader("content-type", CONTENT_TYPE)
                              .addHeader("Accept", CONTENT_TYPE)
                              .setEndpoint(ENTRY_POINT_QUERY)
                              .setBody(formattedQuery)
                              .performRequest();

        LOGGER.debug("Response of /aggregated-objects/query RestApi, Status Code: " + response.getStatusCodeValue()
                + "\nResponse: "
                + response.getBody().toString());

        JsonNode jsonNodeResult = objMapper.readValue(response.getBody().toString(),
                JsonNode.class);
        JsonNode aggrObjResponse = objMapper.readTree(jsonNodeResult.get(0).get(aggrId).asText());

        JsonNode confidenceLevels = aggrObjResponse.get("confidenceLevels").get(1);

        assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());
        assertEquals("Failed to retrieve the latest confidence level.", "readyForDelivery",
                confidenceLevels.get("name").asText());
        assertEquals("Failed to retrieve the latest confidence level.", "SUCCESS",
                confidenceLevels.get("value").asText());
    }

    /**
     * Method that creates a document in MongoDb database.
     *
     * @param databaseName    - Name of the database in MongoDb to use.
     * @param collectionName  - Name of the collection in MongoDb to use.
     * @param objToBeInserted - Object in string format to be inserted to database.
     *
     * @return boolean - Returns true or false depending if object/document was successfully created
     *         in database.
     *
     */
    private void createDocumentInMongoDb(String databaseName, String collectionName,
            String objToBeInserted) {
        LOGGER.debug("Inserting Object to MongoDb.\nDatabase: " + databaseName + "\nCollection: "
                + collectionName
                + "\nDocument to be inserted\n: " + objToBeInserted);
        mongoDBHandler.insertDocument(databaseName, collectionName, objToBeInserted);
    }
}
