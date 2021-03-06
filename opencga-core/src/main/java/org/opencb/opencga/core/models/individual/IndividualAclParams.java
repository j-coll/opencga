/*
 * Copyright 2015-2020 OpenCB
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

package org.opencb.opencga.core.models.individual;

import org.opencb.opencga.core.models.AclParams;

// Acl params to communicate the WS and the sample manager
public class IndividualAclParams extends AclParams {

    private String sample;
    private boolean propagate;

    public IndividualAclParams() {

    }

    public IndividualAclParams(String permissions, Action action, String sample, boolean propagate) {
        super(permissions, action);
        this.sample = sample;
        this.propagate = propagate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IndividualAclParams{");
        sb.append("permissions='").append(permissions).append('\'');
        sb.append(", action=").append(action);
        sb.append(", sample='").append(sample).append('\'');
        sb.append(", propagate=").append(propagate);
        sb.append('}');
        return sb.toString();
    }

    public String getSample() {
        return sample;
    }

    public IndividualAclParams setSample(String sample) {
        this.sample = sample;
        return this;
    }

    public boolean isPropagate() {
        return propagate;
    }

    public IndividualAclParams setPropagate(boolean propagate) {
        this.propagate = propagate;
        return this;
    }

    public IndividualAclParams setPermissions(String permissions) {
        super.setPermissions(permissions);
        return this;
    }

    public IndividualAclParams setAction(Action action) {
        super.setAction(action);
        return this;
    }
}
