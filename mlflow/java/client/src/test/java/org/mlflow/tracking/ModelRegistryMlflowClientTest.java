package org.mlflow.tracking;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.mlflow.api.proto.ModelRegistry.ModelVersionDetailed;
import org.mlflow.api.proto.Service.RunInfo;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.mlflow.tracking.TestUtils.createExperimentName;

public class ModelRegistryMlflowClientTest {
    private static final Logger logger = LoggerFactory.getLogger(ModelRegistryMlflowClientTest.class);

    private static final MlflowProtobufMapper mapper = new MlflowProtobufMapper();

    private final TestClientProvider testClientProvider = new TestClientProvider();

    private MlflowClient client;

    private String modelName;
    private File tempFile;

    private static final String content = "Hello, Worldz!";

    @BeforeSuite
    public void beforeAll() throws IOException {
        client = Mockito.spy(testClientProvider.initializeClientAndSqlLiteBasedServer());
        modelName = "Model-" + UUID.randomUUID().toString();


        String expName = createExperimentName();
        String expId = client.createExperiment(expName);

        RunInfo runCreated = client.createRun(expId);
        String runId = runCreated.getRunUuid();

        File tempDir = Files.createTempDirectory("tempDir").toFile();
        tempFile = Files.createTempFile(tempDir.toPath(), "file", ".txt").toFile();

        FileUtils.writeStringToFile(tempFile, content, StandardCharsets.UTF_8);

        client.sendPost("registered-models/create",
                mapper.makeCreateModel(modelName));

        client.sendPost("model-versions/create",
                mapper.makeCreateModelVersion(modelName, runId, tempFile.getAbsolutePath()));
    }

    @AfterSuite
    public void afterAll() throws InterruptedException {
        testClientProvider.cleanupClientAndServer();
    }

    @AfterTest
    public void after() {
        Mockito.reset(client);
    }

    @Test
    public void testGetLatestModelVersions() throws IOException {

        // a list of stages
        List<ModelVersionDetailed> versions = client.getLatestVersions(modelName,
                Lists.newArrayList("None"));
        validateDetailedModelVersion(versions.get(0), modelName, "None", 1);

        // default stages
        List<ModelVersionDetailed> modelVersionDetails = client.getLatestVersions(modelName);
        Assert.assertEquals(modelVersionDetails.size(), 1);
        validateDetailedModelVersion(modelVersionDetails.get(0),
                modelName, "None", 1);
    }

    @Test
    public void testGetModelVersionDownloadUri() {
        String downloadUri = client.getModelVersionDownloadUri(modelName, 1);
        Assert.assertEquals(tempFile.getAbsolutePath(), downloadUri);
    }

    @Test
    public void testDownloadModelVersion() throws IOException {
        File tempDownloadFile = client.downloadModelVersion(modelName, 1);
        String downloadedContent = FileUtils.readFileToString(tempDownloadFile,
                StandardCharsets.UTF_8);
        Assert.assertEquals(content, downloadedContent);
    }

    @Test
    public void testDownloadLatestModelVersion() throws IOException {
        File tempDownloadFile = client.downloadLatestModelVersion(modelName, "None");
        String downloadedContent = FileUtils.readFileToString(tempDownloadFile,
                StandardCharsets.UTF_8);
        Assert.assertEquals(content, downloadedContent);
    }

    private void validateDetailedModelVersion(ModelVersionDetailed details, String modelName,
                                              String stage, long version) {
        Assert.assertEquals(details.getCurrentStage(), stage);
        Assert.assertEquals(details.getModelVersion().getRegisteredModel().getName(), modelName);
        Assert.assertEquals(details.getModelVersion().getVersion(), version);
    }

}
