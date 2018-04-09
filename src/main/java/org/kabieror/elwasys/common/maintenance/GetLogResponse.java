/**
 *
 */
package org.kabieror.elwasys.common.maintenance;

import java.util.List;

/**
 * @author Oliver
 *
 */
public class GetLogResponse extends MaintenanceResponse {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final List<String> logContent;

    /**
     * @param id
     */
    public GetLogResponse(MaintenanceRequest request, List<String> logContent) {
        super(request);
        this.logContent = logContent;
    }

    public List<String> getLogContent() {
        return this.logContent;
    }

}
