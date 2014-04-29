package com.flipkart.foxtrot.core.datastore.impl.hbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.foxtrot.common.Document;
import com.flipkart.foxtrot.core.MockHTable;
import com.flipkart.foxtrot.core.datastore.DataStoreException;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Created by rishabh.goyal on 15/04/14.
 */

public class HbaseDataStoreTest {
    private HbaseDataStore hbaseDataStore;
    private HTableInterface tableInterface;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(HbaseDataStoreTest.class.getSimpleName());
    private static final byte[] COLUMN_FAMILY = Bytes.toBytes("d");
    private static final byte[] DATA_FIELD_NAME = Bytes.toBytes("data");
    private static final String TEST_APP = "test-app";

    @Before
    public void setUp() throws Exception {
        this.tableInterface = MockHTable.create();
        this.tableInterface = spy(this.tableInterface);
        HbaseTableConnection tableConnection = Mockito.mock(HbaseTableConnection.class);
        when(tableConnection.getTable()).thenReturn(tableInterface);
        hbaseDataStore = new HbaseDataStore(tableConnection, mapper);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testSaveSingle() throws Exception {
        logger.info("Testing Single Save");
        Document expectedDocument = new Document();
        expectedDocument.setId(UUID.randomUUID().toString());
        expectedDocument.setTimestamp(System.currentTimeMillis());
        JsonNode data = mapper.valueToTree(Collections.singletonMap("TEST_NAME", "SINGLE_SAVE_TEST"));
        expectedDocument.setData(data);
        hbaseDataStore.save(TEST_APP, expectedDocument);
        validateSave(expectedDocument);
        logger.info("Tested Single Save");
    }

    @Test(expected = DataStoreException.class)
    public void testSaveSingleException() throws Throwable {
        logger.info("Testing Single Save - Exception");
        Document expectedDocument = new Document();
        expectedDocument.setId(UUID.randomUUID().toString());
        expectedDocument.setTimestamp(System.currentTimeMillis());
        JsonNode data = mapper.valueToTree(Collections.singletonMap("TEST_NAME", "SINGLE_SAVE_TEST"));
        expectedDocument.setData(data);
        doThrow(new IOException())
                .when(tableInterface)
                .put(Matchers.<Put>any());
        hbaseDataStore.save(TEST_APP, expectedDocument);
        validateSave(expectedDocument);
        logger.info("Tested Single Save");
    }

    @Test
    public void testSaveBulk() throws Exception {
        logger.info("Testing Bulk Save");
        List<Document> documents = new Vector<Document>();
        for (int i = 0; i < 10; i++) {
            documents.add(new Document(UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                    mapper.valueToTree(Collections.singletonMap("TEST_NAME", "SINGLE_SAVE_TEST"))));
        }
        hbaseDataStore.save(TEST_APP, documents);
        for (Document document : documents) {
            validateSave(document);
        }
        logger.info("Tested Bulk Save");
    }

    @Test(expected = DataStoreException.class)
    public void testSaveBulkException() throws Exception {
        logger.info("Testing Bulk Save - Exception");
        List<Document> documents = new Vector<Document>();
        for (int i = 0; i < 10; i++) {
            documents.add(new Document(UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                    mapper.valueToTree(Collections.singletonMap("TEST_NAME", "SINGLE_SAVE_TEST"))));
        }
        doThrow(new IOException())
                .when(tableInterface)
                .put(Matchers.<Put>any());
        hbaseDataStore.save(TEST_APP, documents);
        for (Document document : documents) {
            validateSave(document);
        }
        logger.info("Tested Bulk Save");
    }

    public void validateSave(Document savedDocument) throws Exception {
        String rowkey = String.format("%s:%s", savedDocument.getId(), TEST_APP);
        Get get = new Get(Bytes.toBytes(rowkey));
        Result result = tableInterface.get(get);
        assertNotNull("Get for Id should not be null", result);
        Document actualDocument = new Document(savedDocument.getId(),
                savedDocument.getTimestamp(),
                mapper.readTree(result.getValue(COLUMN_FAMILY, DATA_FIELD_NAME)));
        compare(savedDocument, actualDocument);
    }

    @Test
    public void testGetSingle() throws Throwable {
        logger.info("Testing Single Get");
        String id = UUID.randomUUID().toString();
        JsonNode data = mapper.valueToTree(Collections.singletonMap("TEST_NAME", "SINGLE_SAVE_TEST"));

        Document expectedDocument = new Document(id, System.currentTimeMillis(), data);
        tableInterface.put(hbaseDataStore.getPutForDocument(TEST_APP, expectedDocument));
        Document actualDocument = hbaseDataStore.get(TEST_APP, id);
        compare(expectedDocument, actualDocument);
        logger.info("Tested Single Get");
    }

    @Test(expected = DataStoreException.class)
    public void testGetSingleMissingDocument() throws Throwable {
        logger.info("Testing Single Get - Missing ID");
        hbaseDataStore.get(TEST_APP, UUID.randomUUID().toString());
        logger.info("Tested Single Get - Missing ID");
    }

    @Test(expected = DataStoreException.class)
    public void testGetSingleException() throws Throwable {
        logger.info("Testing Single Get - Exception");
        String id = UUID.randomUUID().toString();
        JsonNode data = mapper.valueToTree(Collections.singletonMap("TEST_NAME", "SINGLE_SAVE_TEST"));

        Document expectedDocument = new Document(id, System.currentTimeMillis(), data);
        tableInterface.put(hbaseDataStore.getPutForDocument(TEST_APP, expectedDocument));
        doThrow(new IOException())
                .when(tableInterface)
                .get(Matchers.<Get>any());
        Document actualDocument = hbaseDataStore.get(TEST_APP, id);
        compare(expectedDocument, actualDocument);
        logger.info("Tested Single Get - Exception");
    }

    @Test
    public void testGetBulk() throws Throwable {
        logger.info("Testing Bulk Get");
        Map<String, Document> idValues = new HashMap<String, Document>();
        List<String> ids = new Vector<String>();
        List<Put> putList = new Vector<Put>();
        for (int i = 0; i < 10; i++) {
            String id = UUID.randomUUID().toString();
            ids.add(id);
            idValues.put(id,
                    new Document(id, System.currentTimeMillis(), mapper.valueToTree(Collections.singletonMap("TEST_NAME", "SINGLE_SAVE_TEST"))));
            putList.add(hbaseDataStore.getPutForDocument(TEST_APP, idValues.get(id)));
        }
        tableInterface.put(putList);

        List<Document> actualDocuments = hbaseDataStore.get(TEST_APP, ids);
        HashMap<String, Document> actualIdValues = new HashMap<String, Document>();
        for (Document doc : actualDocuments) {
            actualIdValues.put(doc.getId(), doc);
        }
        assertNotNull("List of returned Documents should not be null", actualDocuments);
        for (String id : ids) {
            assertTrue("Requested Id should be present in response", actualIdValues.containsKey(id));
            compare(idValues.get(id), actualIdValues.get(id));
        }
        logger.info("Tested Bulk Get");
    }

    @Test(expected = DataStoreException.class)
    public void testGetBulkMissingDocument() throws Throwable {
        logger.info("Testing Bulk Get - Missing ID");
        hbaseDataStore.get(TEST_APP, Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        logger.info("Tested Bulk Get - Missing ID");
    }

    @Test(expected = DataStoreException.class)
    public void testGetBulkException() throws Throwable {
        logger.info("Testing Bulk Get - Exception");
        Map<String, Document> idValues = new HashMap<String, Document>();
        List<String> ids = new Vector<String>();
        List<Put> putList = new Vector<Put>();
        for (int i = 0; i < 10; i++) {
            String id = UUID.randomUUID().toString();
            ids.add(id);
            idValues.put(id,
                    new Document(id, System.currentTimeMillis(), mapper.valueToTree(Collections.singletonMap("TEST_NAME", "SINGLE_SAVE_TEST"))));
            putList.add(hbaseDataStore.getPutForDocument(TEST_APP, idValues.get(id)));
        }
        tableInterface.put(putList);
        doThrow(new IOException())
                .when(tableInterface)
                .get(Matchers.<Get>any());
        List<Document> actualDocuments = hbaseDataStore.get(TEST_APP, ids);
        HashMap<String, Document> actualIdValues = new HashMap<String, Document>();
        for (Document doc : actualDocuments) {
            actualIdValues.put(doc.getId(), doc);
        }
        assertNotNull("List of returned Documents should not be null", actualDocuments);
        for (String id : ids) {
            assertTrue("Requested Id should be present in response", actualIdValues.containsKey(id));
            compare(idValues.get(id), actualIdValues.get(id));
        }
        logger.info("Tested Bulk Get - Exception");
    }


    public void compare(Document expected, Document actual) throws Exception {
        assertNotNull(expected);
        assertNotNull(actual);
        assertNotNull("Actual document Id should not be null", actual.getId());
        assertNotNull("Actual document data should not be null", actual.getData());
        assertEquals("Actual Doc Id should match expected Doc Id", expected.getId(), actual.getId());
        assertEquals("Actual Doc Timestamp should match expected Doc Timestamp", expected.getTimestamp(), actual.getTimestamp());
        String expectedData = mapper.writeValueAsString(expected.getData());
        String actualData = mapper.writeValueAsString(actual.getData());
        assertEquals("Actual data should match expected data", expectedData, actualData);
    }
}