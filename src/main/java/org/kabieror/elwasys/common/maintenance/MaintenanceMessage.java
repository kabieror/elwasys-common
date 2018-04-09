package org.kabieror.elwasys.common.maintenance;

import java.io.Serializable;

/**
 * Diese Klasse repräsentiert eine allgemeine Wartungs-Anfrage an den
 * Waschwächter.
 *
 * @author Oliver Kabierschke
 *
 */
public abstract class MaintenanceMessage implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * Die eindeutige ID der Konversation, an der diese Nachricht teil hat
     */
    private long conversationId;

    MaintenanceMessage() {

    }

    public long getConversationId() {
        return this.conversationId;
    }

    void setConversationId(long id) {
        this.conversationId = id;
    }
}
