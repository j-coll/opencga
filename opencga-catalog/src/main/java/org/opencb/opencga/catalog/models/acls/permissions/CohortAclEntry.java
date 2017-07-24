/*
 * Copyright 2015-2017 OpenCB
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

package org.opencb.opencga.catalog.models.acls.permissions;

import org.opencb.commons.datastore.core.ObjectMap;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by pfurio on 11/05/16.
 */
public class CohortAclEntry extends AbstractAclEntry<CohortAclEntry.CohortPermissions> {

    public enum CohortPermissions {
        VIEW,
        UPDATE,
        DELETE,
        SHARE,
        WRITE_ANNOTATIONS,
        VIEW_ANNOTATIONS,
        DELETE_ANNOTATIONS
    }

    public CohortAclEntry() {
        this("", Collections.emptyList());
    }

    public CohortAclEntry(String member, EnumSet<CohortPermissions> permissions) {
        super(member, permissions);
    }

    public CohortAclEntry(String member, ObjectMap permissions) {
        super(member, EnumSet.noneOf(CohortPermissions.class));

        EnumSet<CohortPermissions> aux = EnumSet.allOf(CohortPermissions.class);
        for (CohortPermissions permission : aux) {
            if (permissions.containsKey(permission.name()) && permissions.getBoolean(permission.name())) {
                this.permissions.add(permission);
            }
        }
    }

    public CohortAclEntry(String member, List<String> permissions) {
        super(member, EnumSet.noneOf(CohortPermissions.class));

        if (permissions.size() > 0) {
            this.permissions.addAll(permissions.stream().map(CohortPermissions::valueOf).collect(Collectors.toList()));
        }
    }
}
