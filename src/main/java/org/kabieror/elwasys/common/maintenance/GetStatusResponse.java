/**
 *
 */
package org.kabieror.elwasys.common.maintenance;

import org.kabieror.elwasys.common.Execution;
import org.kabieror.elwasys.common.maintenance.data.BacklightStatus;
import org.kabieror.elwasys.common.maintenance.data.InterfaceStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Oliver
 *
 */
public class GetStatusResponse extends MaintenanceResponse {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private InterfaceStatus interfaceStatus;

    private BacklightStatus backlightStatus;

    private LocalDateTime startupTime;

    private List<Execution> runningExecutions;

    public GetStatusResponse(MaintenanceRequest request) {
        super(request);
    }

    public InterfaceStatus getInterfaceStatus() {
        return this.interfaceStatus;
    }

    /**
     * The current status of the terminal.
     *
     * @param request
     */
    public void setInterfaceStatus(InterfaceStatus status) {
        this.interfaceStatus = status;
    }

    public BacklightStatus getBacklightStatus() {
        return this.backlightStatus;
    }

    /**
     * The current status of the backlight of the terminal.
     *
     * @param request
     */
    public void setBacklightStatus(BacklightStatus status) {
        this.backlightStatus = status;
    }

    public LocalDateTime getStartupTime() {
        return this.startupTime;
    }

    /**
     * The time of the start up of the elwaclient.
     *
     * @param startupTime
     */
    public void setStartupTime(LocalDateTime startupTime) {
        this.startupTime = startupTime;
    }

    public List<Execution> getRunningExecutions() {
        return this.runningExecutions;
    }

    /**
     * The currently cunning executions.
     *
     * @param executions
     */
    public void setRunningExecutions(List<Execution> executions) {
        this.runningExecutions = executions;
    }

}
