package org.opencb.opencga.lib.execution;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opencb.commons.test.GenericTest;
import org.opencb.opencga.lib.common.Config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ExecutionManagerTest extends GenericTest {

    @BeforeClass
    public static void init() throws IOException {
        InputStream is = ExecutionManagerTest.class.getClassLoader().getResourceAsStream("test.properties");
        Properties properties = new Properties();
        properties.load(is);


        Config.setGcsaHome(properties.getProperty("INSTALLATION_DIR"));
    }

    @Test
    public void testSGE() throws Exception {
        runJob(ExecutionManagerFactory.SGE);
    }

    @Test
    public void testSLURM() throws Exception {
        runJob(ExecutionManagerFactory.SLURM);
    }

    @Test
    public void testLocalThread() throws Exception {
        runJob(ExecutionManagerFactory.LOCAL);
    }

    @Test
    public void testLSF() throws Exception {
        runJob(ExecutionManagerFactory.LSF);
    }


    protected void runJob(String executionManagerName) throws Exception {
        ExecutionManager em = ExecutionManagerFactory.getFactory().getExecutionManager(executionManagerName);
        if(em != null) {
            String jobExecutionId = em.queueJob("Sleep tool", "myJobName", null, "/tmp", "echo \"Hello World!\"", null, executionManagerName);
            System.out.println("Job submitted with id: " + jobExecutionId);

            for(;;) {
                ExecutionJobStatus executionJobStatus = em.status(jobExecutionId);
                System.out.println(executionJobStatus.getStatus());
                if(executionJobStatus.getStatus().equals(ExecutionJobStatus.FINISHED) || executionJobStatus.getStatus().equals(ExecutionJobStatus.EXECUTION_ERROR)) {
                    System.out.println(executionJobStatus.getAttributes().toJson());
                    break;
                }
                Thread.sleep(500);
            }
        }
    }
}