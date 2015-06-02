package org.opencb.opencga.catalog.session;

import org.opencb.opencga.catalog.authentication.AuthenticationManager;
import org.opencb.opencga.catalog.db.api.CatalogUserDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.models.Session;

import static org.opencb.opencga.catalog.utils.ParamUtils.checkParameter;

/**
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class CatalogSessionManager implements SessionManager {

    private final CatalogUserDBAdaptor userDBAdaptor;
    private final AuthenticationManager authenticationManager;

    public CatalogSessionManager(CatalogUserDBAdaptor userDBAdaptor, AuthenticationManager authenticationManager) {
        this.userDBAdaptor = userDBAdaptor;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public Session login(String userId, String password, String sessionIp) throws CatalogException {
        checkParameter(userId, "userId");
        checkParameter(password, "password");
        checkParameter(sessionIp, "sessionIp");
        Session session = new Session(sessionIp);

        authenticationManager.authenticate(userId, password, true);

        return userDBAdaptor.addSession(userId, session).first();
    }

    @Override
    public Session logout(String userId, String sessionId) throws CatalogException {
        userDBAdaptor.logout(userId, sessionId);
        return userDBAdaptor.getSession(userId, sessionId).first();
    }

    @Override
    public String getUserIdBySessionId(String sessionId) {
        return userDBAdaptor.getUserIdBySessionId(sessionId);
    }
}
