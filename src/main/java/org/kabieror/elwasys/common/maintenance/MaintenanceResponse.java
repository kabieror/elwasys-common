/**
 *
 */
package org.kabieror.elwasys.common.maintenance;

/**
 * @author Oliver Kabierschke
 *
 */
public abstract class MaintenanceResponse extends MaintenanceMessage {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     *
     */
    MaintenanceResponse(MaintenanceRequest request) {
        super();
        this.setConversationId(request.getConversationId());
    }

    /**
     *
     */
    MaintenanceResponse() {
        super();
        this.setConversationId(-1);
    }

}
