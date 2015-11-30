package org.opencb.opencga.analysis.execution.plugins.test;

import org.opencb.opencga.analysis.execution.model.Execution;
import org.opencb.opencga.analysis.execution.model.Manifest;
import org.opencb.opencga.analysis.execution.model.Option;
import org.opencb.opencga.analysis.execution.plugins.OpenCGAPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created on 26/11/15
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class TestPlugin extends OpenCGAPlugin {

    public static final String OUTDIR = "outdir";
    public static final String PARAM_1 = "param1";
    public static final String ERROR = "error";
    public static final String PLUGIN_ID = "test_plugin";
    private final Manifest manifest;

    public TestPlugin() {
        List<Option> validParams = Arrays.asList(
                new Option(OUTDIR, "", true),
                new Option(PARAM_1, "", false),
                new Option(ERROR, "", false)
        );
        List<Execution> executions = Collections.singletonList(
                new Execution("default", "default", "", Collections.emptyList(), Collections.emptyList(), OUTDIR, validParams, Collections.emptyList(), null, null)
        );
        manifest = new Manifest(null, "0.1.0", PLUGIN_ID, "Test plugin", "", "", "", null, Collections.emptyList(), executions, null, null);
    }

    @Override
    public Manifest getManifest() {
        return manifest;
    }

    @Override
    public String getIdentifier() {
        return PLUGIN_ID;
    }

    @Override
    public int run() throws Exception {
        if (getConfiguration().containsKey(PARAM_1)) {
            getLogger().info(getConfiguration().getString(PARAM_1));
        }
        return getConfiguration().getBoolean(ERROR) ? 1 : 0;
    }

}
