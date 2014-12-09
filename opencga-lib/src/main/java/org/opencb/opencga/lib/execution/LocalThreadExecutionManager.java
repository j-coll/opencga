package org.opencb.opencga.lib.execution;

import java.net.URI;

/**
 * Created by jacobo on 5/12/14.
 */
public class LocalThreadExecutionManager extends ExecutionManager {

    @Override
    public String queueJob(String toolName, String jobName, URI outDir, String commandLine, String queue, String logFilePrefix) throws Exception {
        return null;
    }

    @Override
    public String queueJob(String toolName, String jobName, String hostName, String outDir, String commandLine, String queue, String logFilePrefix) throws Exception {
        return null;
    }

    @Override
    public ExecutionJobStatus status(String jobExecutionId) throws Exception {
        return null;
    }
}
