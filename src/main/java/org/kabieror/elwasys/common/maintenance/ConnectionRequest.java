package org.kabieror.elwasys.common.maintenance;

/**
 * Eine Verbindungsanfrage eines Clients an den Server.
 *
 * @author Oliver Kabierschke
 */
class ConnectionRequest extends MaintenanceRequest {
    private String location;

    public ConnectionRequest(String location) {
        super();
        this.location = location;
    }

    public String getLocation() {
        return location;
    }
}
