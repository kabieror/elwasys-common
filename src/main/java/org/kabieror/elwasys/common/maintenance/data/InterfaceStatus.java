/**
 *
 */
package org.kabieror.elwasys.common.maintenance.data;

/**
 * Diese Aufzählung definiert mögliche Zustände, die ein Waschwächter haben
 * kann.
 *
 * @author Oliver Kabierschke
 *
 */
public enum InterfaceStatus {
    /**
     * Warte auf Benutzereingabe.
     */
    NORMAL("Betriebsbereit"),

    /**
     * Startvorgang
     */
    START("Startvorgang"),

    /**
     * Allgemeiner Fehler
     */
    ERROR("Allgemeiner Fehler");

    private String detailMessage;

    InterfaceStatus() {
        this.detailMessage = "";
    }

    InterfaceStatus(String msg) {
        this.detailMessage = msg;
    }

    /**
     * Gibt die beschreibende Nachricht zum aktuellen Zustand zurück.
     *
     * @return Die beschreibende Nachricht.
     */
    public String getDetailMessage() {
        return this.detailMessage;
    }

    /**
     * Setzt die beschreibende Nachricht zum aktuellen Zustand.
     *
     * @param message
     *            Die beschreibende Nachricht
     */
    public void setDetailMessage(String message) {
        this.detailMessage = message;
    }
}
