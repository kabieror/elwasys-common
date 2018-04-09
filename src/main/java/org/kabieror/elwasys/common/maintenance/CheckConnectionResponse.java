/**
 *
 */
package org.kabieror.elwasys.common.maintenance;

/**
 * @author Oliver Kabierschke
 *
 */
public class CheckConnectionResponse extends MaintenanceResponse {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * @param conversationId
     */
    public CheckConnectionResponse(MaintenanceRequest request) {
        super(request);
    }

}
