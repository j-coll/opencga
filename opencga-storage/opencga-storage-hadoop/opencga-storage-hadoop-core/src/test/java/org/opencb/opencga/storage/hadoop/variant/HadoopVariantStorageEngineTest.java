package org.opencb.opencga.storage.hadoop.variant;

import org.junit.ClassRule;
import org.opencb.opencga.storage.core.variant.VariantStorageEngineTest;

/**
 * Created on 03/05/19.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class HadoopVariantStorageEngineTest extends VariantStorageEngineTest implements HadoopVariantStorageTest {

    @ClassRule
    public static HadoopExternalResource externalResource = new HadoopExternalResource();

}
