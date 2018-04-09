package org.kabieror.elwasys.common.maintenance;

/**
 * Bestätigt den erfolgreichen Aufbau einer Verbindung.
 *
 * @author Oliver Kabierschke
 */
class ConnectionResponse extends MaintenanceResponse {
    ConnectionResponse(ConnectionRequest connectionRequest) {
        super(connectionRequest);
    }
}
