package org.opencb.opencga.catalog.session;

import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.models.Session;

/**
 * Created by hpccoll1 on 02/06/15.
 */
public interface SessionManager {

    /**
     * Creates and stores a new Session for the specific user.
     *
     * @param userId        UserId of the user
     * @param password      User's password
     * @param sessionIp     Ip where the conextion is from
     * @return              New Session
     * @throws CatalogException
     */
    Session login(String userId, String password, String sessionIp) throws CatalogException;

    /**
     * Invalidates the specified Session for this user.
     *
     * @param userId        UserId of the user
     * @param sessionId     Session to invalidate
     * @return              Invalidated session
     * @throws CatalogException
     */
    Session logout(String userId, String sessionId) throws CatalogException;

    /**
     * Resolves the userId given the sessionId
     *
     * @param sessionId     SessionId to resolve
     * @return              UserId attached to that sessionId
     */
    String getUserIdBySessionId(String sessionId);
}
