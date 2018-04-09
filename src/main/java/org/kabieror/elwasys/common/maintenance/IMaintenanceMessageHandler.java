/**
 *
 */
package org.kabieror.elwasys.common.maintenance;

/**
 * @author Oliver
 *
 */
public interface IMaintenanceMessageHandler {
    /**
     * Behandelt eine Anfrage für Log-Dateien
     *
     * @param request
     *            Die Anfrage
     * @return Die Antwort
     */
    GetLogResponse handleGetLog(GetLogRequest request);

    /**
     * Behandelt eine Anfrage für den aktuellen Status
     *
     * @param request
     *            Die Anfrage
     * @return Die Antwort
     */
    GetStatusResponse handleGetStatus(GetStatusRequest request);

    /**
     * Behandelt eine Anfrage zum Neustarten der Anwendung.
     *
     * @param request
     *            Die Anfrage
     * @return Die Antwort
     */
    void handleRestartApp(RestartAppRequest request);
}
