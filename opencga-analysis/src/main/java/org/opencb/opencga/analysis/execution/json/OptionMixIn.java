package org.opencb.opencga.analysis.execution.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.opencb.opencga.analysis.execution.model.Option;

import java.util.List;

/**
 * Created by pfurio on 30/10/15.
 */
public class OptionMixIn {

    @JsonProperty(required = false)
    private boolean required;

    @JsonProperty(required = false)
    private boolean unnamed;

    @JsonProperty(value = "default", required = false)
    private boolean _default;

    @JsonProperty(required = false)
    private String range;

    @JsonProperty(required = false)
    private int arity;

    @JsonProperty(required = false)
    private List values;

    @JsonProperty(required = false)
    private Option.ValidTypes type;

}
