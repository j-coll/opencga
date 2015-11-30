/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.analysis.execution.model;

import java.util.List;

public class Option {
    // Mandatory
    private String name;
    private String description;

    // Optional
    private boolean required;
    private boolean unnamed;
    private Object _default;
    private String range;
    private int arity;
    private List values;
    private ValidTypes type;

    public enum ValidTypes {

        FILE("file"),
        FOLDER("folder"),
        BOOLEAN("boolean"),
        TEXT("text"),
        NUMERIC("numeric");

        private String name;

        ValidTypes(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }


    }

    public Option() {

    }

    public Option(String name, String description, boolean required) {
        this.name = name;
        this.description = description;
        this.required = required;
    }

    @Override
    public String toString() {
        return "Option{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", required=" + required +
                '}';
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isUnnamed() {
        return unnamed;
    }

    public void setUnnamed(boolean unnamed) {
        this.unnamed = unnamed;
    }

    public Object getDefault() {
        return _default;
    }

    public void setDefault(Object def) {
        this._default = def;
    }


    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public int getArity() {
        return arity;
    }

    public void setArity(int arity) {
        this.arity = arity;
    }

    public List getValues() {
        return values;
    }

    public void setValues(List values) {
        this.values = values;
    }

    public ValidTypes getType() {
        return type;
    }

    public void setType(ValidTypes type) {
        this.type = type;
    }
}
