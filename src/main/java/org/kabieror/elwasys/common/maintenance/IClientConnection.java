package org.kabieror.elwasys.common.maintenance;

import java.io.IOException;

/**
 * Die Schnittstelle zu einem Wartungs-Client.
 *
 * @author Oliver Kabierschke
 */
public interface IClientConnection {

    /**
     * Sendet eine Anfrage an den Client und wartet auf eine Antwort.
     *
     * @param request Die Anfrage.
     * @return Die Antwort des Clients.
     */
    MaintenanceResponse sendQuery(MaintenanceRequest request) throws IOException;

    /**
     * Sendet eine Anfrage an den Client.
     *
     * @param request Die Anfrage.
     */
    void sendCommand(MaintenanceRequest request) throws IOException;

    /**
     * Gibt die Adresse des Hosts zurück, mit der die Verbindung besteht.
     *
     * @return Die Adresse des Hosts, mit der die Verbindung besteht.
     */
    String getHostAddress();
}
