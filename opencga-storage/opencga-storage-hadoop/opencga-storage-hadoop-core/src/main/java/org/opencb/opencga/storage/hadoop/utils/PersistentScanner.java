package org.opencb.opencga.storage.hadoop.utils;

import org.apache.hadoop.hbase.UnknownScannerException;
import org.apache.hadoop.hbase.client.AbstractClientScanner;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;

import java.io.IOException;

/**
 * Created by jacobo on 05/01/19.
 */
public class PersistentScanner extends AbstractClientScanner {

    private final HBaseManager hBaseManager;
    private final Scan scan;
    private final String tableName;
    private ResultScanner scanner;
    private byte[] lastRow = null;

    public PersistentScanner(HBaseManager hBaseManager, Scan scan, String tableName) throws IOException {
        this.scanner = null;
        this.hBaseManager = hBaseManager;
        this.scan = scan;
        this.tableName = tableName;
        if (scan.getAllowPartialResults()) {
            throw new IllegalArgumentException("Can not use a PersistentScanner with partial results");
        }
        obtainNewScanner();
    }

    @Override
    public Result next() throws IOException {
        return next(true);
    }

    private Result next(boolean retry) throws IOException {
        try {
            Result result = scanner.next();
            lastRow = result.getRow();
            return result;
        } catch (UnknownScannerException e) {
            if (retry) {
                // Obtain new scanner
                obtainNewScanner();
                return next(false);
            } else {
                throw e;
            }
        }
    }

    private void obtainNewScanner() throws IOException {
        if (lastRow != null) {
            scan.setStartRow(new Scan().setRowPrefixFilter(lastRow).getStopRow());
            scan.setStartRow(lastRow);
        }
        scanner = hBaseManager.act(tableName, table -> {
            return table.getScanner(scan);
        });
        if (scanner instanceof AbstractClientScanner) {
            // TODO: Merge with previous scan metrics
            scanMetrics = ((AbstractClientScanner) scanner).getScanMetrics();
        }
    }

    @Override
    public void close() {
        scanner.close();
    }

    @Override
    public boolean renewLease() {
        if (scanner instanceof AbstractClientScanner) {
            return ((AbstractClientScanner) scanner).renewLease();
        } else {
            return false;
        }
    }
}
