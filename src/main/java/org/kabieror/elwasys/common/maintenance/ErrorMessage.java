/**
 *
 */
package org.kabieror.elwasys.common.maintenance;

/**
 * @author Oliver
 *
 */
public class ErrorMessage extends MaintenanceMessage {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final String message;

    public ErrorMessage(MaintenanceMessage request, String message) {
        super();
        this.message = message;
        this.setConversationId(request.getConversationId());
    }

    public ErrorMessage(String message) {
        super();
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }

}
