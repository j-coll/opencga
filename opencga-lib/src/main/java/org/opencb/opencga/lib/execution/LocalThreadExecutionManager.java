package org.opencb.opencga.lib.execution;

import org.opencb.datastore.core.ObjectMap;

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
    public String status(String jobExecutionId, ObjectMap attributes) throws Exception {
        return null;
    }
}
