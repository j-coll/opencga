package org.opencb.opencga.lib.execution;

import org.opencb.datastore.core.ObjectMap;

/**
 * Created by jacobo on 9/12/14.
 */
public class ExecutionJobStatus {
    public static final String UNKNOWN = "unknown";
    public static final String RUNNING = "running";
    public static final String TRANSFERRED = "transferred";
    public static final String QUEUED = "queued";
    public static final String ERROR = "error";
    public static final String FINISHED = "finished";
    public static final String EXECUTION_ERROR = "execution error";
    public static final String QUEUE_ERROR = "queueError";

    private String status;
    private ObjectMap attributes;

    public ExecutionJobStatus() {
        this(UNKNOWN, new ObjectMap());
    }

    public ExecutionJobStatus(String status, ObjectMap attributes) {
        this.status = status;
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        return "JobStatus{" +
                "status='" + status + '\'' +
                ", attributes=" + attributes.toJson() +
                '}';
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ObjectMap getAttributes() {
        return attributes;
    }

    public void setAttributes(ObjectMap attributes) {
        this.attributes = attributes;
    }
}
