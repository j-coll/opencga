package org.opencb.opencga.storage.hadoop.variant.annotation.mr;


import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.mapreduce.Job;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.storage.core.variant.adaptors.VariantField;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam;
import org.opencb.opencga.storage.hadoop.variant.AbstractVariantsTableDriver;
import org.opencb.opencga.storage.hadoop.variant.adaptors.VariantHBaseQueryParser;
import org.opencb.opencga.storage.hadoop.variant.mr.VariantMapReduceUtil;

import java.io.IOException;

/**
 * Created on 12/02/19.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class DiscoverVariantsToAnnotateDriver extends AbstractVariantsTableDriver {

    @Override
    protected Class<DiscoverVariantsToAnnotateMapper> getMapperClass() {
        return DiscoverVariantsToAnnotateMapper.class;
    }

    @Override
    protected Job setupJob(Job job, String archiveTable, String variantTable) throws IOException {

        VariantHBaseQueryParser parser = new VariantHBaseQueryParser(getHelper(), getMetadataManager());

        Scan scan = parser.parseQuery(new Query(VariantQueryParam.ANNOTATION_EXISTS.key(), false),
                new QueryOptions(QueryOptions.INCLUDE, VariantField.TYPE.fieldName()));

        VariantMapReduceUtil.initTableMapperJob(job, variantTable, variantTable, scan, getMapperClass());
        VariantMapReduceUtil.setNoneReduce(job);

        return job;
    }

    @Override
    protected String getJobOperationName() {
        return "discover_variants_to_annotate";
    }


    public static class DiscoverVariantsToAnnotateMapper extends TableMapper<ImmutableBytesWritable, Put> {


        public static final byte[] FAMILY = Bytes.toBytes("a");
        public static final byte[] COLUMN = Bytes.toBytes("v");
        public static final byte[] VALUE = new byte[0];

        @Override
        protected void map(ImmutableBytesWritable key, Result value, Context context) throws IOException, InterruptedException {
            Put put = new Put(value.getRow());
            put.addColumn(FAMILY, COLUMN, VALUE);

            context.write(key, put);
        }
    }

}
