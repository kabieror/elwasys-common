package org.kabieror.elwasys.common;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Dieser Buchungseintrag repräsentiert einen Eintrag in der Buchungstabelle des
 * Guthabens
 * 
 * @author Oliver Kabierschke
 *
 */
public class CreditAccountingEntry {
    /**
     * Die Id des Eintrags
     */
    private final int id;

    /**
     * Das Datum der Buchung
     */
    private final LocalDateTime date;

    /**
     * Die verbuchte Menge an Guthaben
     */
    private final BigDecimal amount;

    /**
     * Der von der Buchung betroffene Benutzer
     */
    private final User user;

    /**
     * Der Buchungstext
     */
    private final String description;

    /**
     * Konstruktor
     * 
     * @param dataManager
     *            Der Daten-Manager, der die Verbindung zur Datenbank bereit
     *            stellt
     * @throws SQLException
     */
    public CreditAccountingEntry(DataManager dataManager, ResultSet res, User user)
            throws SQLException {
        this.id = res.getInt("id");
        this.amount = res.getBigDecimal("amount");
        this.user = user;
        this.date = res.getTimestamp("date").toLocalDateTime();
        this.description = res.getString("description");
    }

    public int getId() {
        return this.id;
    }

    public LocalDateTime getDate() {
        return this.date;
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public User getUser() {
        return this.user;
    }

    public String getDescription() {
        return this.description;
    }
}
