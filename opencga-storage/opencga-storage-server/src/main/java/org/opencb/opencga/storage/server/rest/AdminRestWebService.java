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

package org.opencb.opencga.storage.server.rest;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;

/**
 * Created on 03/09/15.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
@Path("/admin")
public class AdminRestWebService extends GenericRestWebService {

    private static RestStorageServer server;

    public AdminRestWebService(@PathParam("version") String version, @Context UriInfo uriInfo, @
            Context HttpServletRequest httpServletRequest, @Context ServletContext context) throws IOException {
        super(version, uriInfo, httpServletRequest, context);
        System.out.println("Build AdminWSServer");
    }


    @GET
    @Path("/stop")
    @Produces("text/plain")
    public Response stop() {
        try {
            server.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
//        OpenCGAStorageService.getInstance().stop();
//        try {
//            RestStorageServer.stop();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return createOkResponse("bye!");
    }

    public static RestStorageServer getServer() {
        return server;
    }

    public static void setServer(RestStorageServer server) {
        AdminRestWebService.server = server;
    }

}
