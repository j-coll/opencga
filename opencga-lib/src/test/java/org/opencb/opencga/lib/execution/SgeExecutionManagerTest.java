package org.opencb.opencga.lib.execution;

import org.junit.Test;
import org.opencb.commons.test.GenericTest;
import org.opencb.datastore.core.ObjectMap;

public class SgeExecutionManagerTest extends GenericTest {

    @Test
    public void jobTest() throws Exception {
        ExecutionManager em = new SgeExecutionManager();

        String jobExecutionId = em.queueJob("Sleep tool", "myJobName", null, "/tmp", "echo \"Hello World!\"", null, null);
        System.out.println("Job submitted with id: " + jobExecutionId);

        ObjectMap objectMap = new ObjectMap();
        for(;;) {
            String status = em.status(jobExecutionId, objectMap);
            System.out.println(status);
            if(status.equals(ExecutionManager.FINISHED) || status.equals(ExecutionManager.EXECUTION_ERROR)) {
                System.out.println(objectMap.toJson());
                break;
            }
            Thread.sleep(500);
        }
    }

}