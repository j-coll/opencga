package org.opencb.opencga.lib.execution;

import org.junit.Test;
import org.opencb.commons.test.GenericTest;

public class SgeExecutionManagerTest extends GenericTest {

    @Test
    public void jobTest() throws Exception {
        ExecutionManager em = new SgeExecutionManager();

        String jobExecutionId = em.queueJob("Sleep tool", "myJobName", null, "/tmp", "echo \"Hello World!\"", null, null);
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