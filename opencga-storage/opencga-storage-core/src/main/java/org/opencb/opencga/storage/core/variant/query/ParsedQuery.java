package org.opencb.opencga.storage.core.variant.query;

import org.apache.commons.lang3.StringUtils;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils;

import java.util.List;

/**
 * Created by jacobo on 25/07/18.
 */
public class ParsedQuery {
    public enum Operation {
        EQ("="),
        NEQ("!="),
        GT(">"),
        GT_NULL(">>"),
        GTE(">="),
        GTE_NULL(">>="),
        LT("<"),
        LT_NULL("<<"),
        LTE("<="),
        LTE_NULL("<<="),
        REGEX("=~"),
        NOT_REFEX("!=~");

        private String symbol;

        Operation(String symbol) {
            this.symbol = symbol;
        }
    }

    public static class SimpleFilterList<VALUE> {
        private final List<VALUE> filters;
        private final VariantQueryUtils.QueryOperation operator;

        public SimpleFilterList(List<VALUE> filters, VariantQueryUtils.QueryOperation operator) {
            this.filters = filters;
            this.operator = operator;
        }
    }

    public static class FilterList<VALUE> {
        private final List<Filter<VALUE>> filters;
        private final VariantQueryUtils.QueryOperation operator;

        public FilterList(List<Filter<VALUE>> filters, VariantQueryUtils.QueryOperation operator) {
            this.filters = filters;
            this.operator = operator;
        }
    }

    public static class Filter<VALUE> {

        private final String key;
        private final Operation operator; // use ENUM
        private final VALUE value;

        public Filter(String key, Operation operator, VALUE value) {
            this.key = key;
            this.operator = operator;
            this.value = value;
        }

        @Override
        public String toString() {
            if (StringUtils.isEmpty(key)) {
                switch (operator) {
                    case EQ:
                        return value.toString();
                    case NEQ:
                        return "!" + value.toString();
                    default:
                        return operator.symbol + value.toString();

                }
            } else {
                return key + operator.symbol + value.toString();
            }
        }
    }
}
