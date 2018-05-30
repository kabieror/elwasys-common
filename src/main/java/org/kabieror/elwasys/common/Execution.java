package org.kabieror.elwasys.common;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;

public class Execution {

    /**
     * Der Daten-Verwalter, der die Verbindung zur Datenbank bereit stellt
     */
    private final DataManager dataManager;

    /**
     * Die ID der Ausführung
     */
    private final int id;

    /**
     * Das ausführende Gerät
     */
    private final Device device;

    /**
     * Das ausgeführte Programm
     */
    private final Program program;

    /**
     * Der ausführende Benutzer
     */
    private final User user;

    /**
     * Das Startdatum
     */
    private LocalDateTime startDate;

    /**
     * Das Enddatum
     */
    private LocalDateTime endDate;

    /**
     * Gibt an, ob die Ausführung läuft
     */
    private boolean finished;

    /**
     * Gibt an, ob die Ausführung in der Datenbank gelöscht wurde
     */
    private boolean deleted = false;

    private LocalDateTime lastUpdateTime;

    /**
     * Erstellt eine neue Ausführung
     *
     * @throws SQLException
     */
    public Execution(DataManager dataManager, Device d, Program p, User u) throws SQLException {
        this.dataManager = dataManager;

        this.device = d;
        this.program = p;
        this.user = u;

        this.finished = false;

        final PreparedStatement s = dataManager.getConnection()
                .prepareStatement("INSERT INTO executions (device_id, program_id, user_id) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
        s.setInt(1, d.getId());
        s.setInt(2, p.getId());
        s.setInt(3, u.getId());

        s.executeUpdate();

        final ResultSet res = s.getGeneratedKeys();
        if (res.next()) {
            this.id = res.getInt(1);
        } else {
            throw new SQLException("No ID received by database.");
        }
    }

    /**
     * Erstellt eine Ausführung auf Grundlage eines Abfrageergebnis'.
     *
     * @param res Das Abfrageergebnis, aus dem die Ausführung geholt werden soll
     * @throws SQLException
     */
    public Execution(DataManager dataManager, ResultSet res, Device d, Program p, User u) throws SQLException {
        this.dataManager = dataManager;

        this.id = res.getInt("id");
        this.device = d;
        this.program = p;
        this.user = u;

        this.update(res);
    }

    /**
     * Erstellt eine Ausführung ohne Auswirkung auf die Datenbank
     */
    private Execution(Device d, Program p, User u) {
        this.dataManager = null;
        this.id = -1;
        this.device = d;
        this.program = p;
        this.user = u;
    }

    /**
     * Gibt eine Programmausführung zurück, die sich nicht in der Datenbank
     * spiegelt
     *
     * @param d Das Gerät der Ausführung
     * @param p Das Programm der Ausführung
     * @param u Der ausführende Benutzer
     * @return Die erstelle Programmausführung
     */
    public static Execution getOfflineExecution(Device d, Program p, User u) {
        return new Execution(d, p, u);
    }

    /**
     * Aktualisiert die Daten der Ausführung mit denen eines Abfrageergebnis'
     *
     * @param res Das Abfrageergebnis, mit dem die Ausführung aktualisiert werden soll
     * @throws SQLException
     */
    public void update(ResultSet res) throws SQLException {
        this.assertNotDeleted();
        final Timestamp startTS = res.getTimestamp("start");
        final Timestamp stopTS = res.getTimestamp("stop");

        if (startTS != null) {
            this.startDate = startTS.toLocalDateTime();
        }
        if (stopTS != null) {
            this.endDate = stopTS.toLocalDateTime();
        }

        this.finished = res.getBoolean("finished");
    }

    /**
     * @throws SQLException
     */
    public void update() throws SQLException {
        // Only update after some time again
        if (this.lastUpdateTime != null &&
                Duration.between(this.lastUpdateTime, LocalDateTime.now()).minus(DataManager.UPDATE_DELAY)
                        .isNegative()) {
            return;
        }
        this.lastUpdateTime = LocalDateTime.now();

        final ResultSet res =
                this.dataManager.getConnection().prepareCall("SELECT * FROM executions WHERE id=" + this.id)
                        .executeQuery();
        res.next();
        this.update(res);
    }

    /**
     * Gibt an, ob die Ausführung läuft
     *
     * @return Wahr, wenn die Ausführung läuft.
     */
    public boolean isRunning() {
        return !this.finished && this.startDate != null;
    }

    /**
     * Beendet die Ausführung
     *
     * @throws SQLException Wenn die Änderung nicht in die Datenbank geschrieben werden kann
     */
    public void stop() throws SQLException {
        this.assertNotDeleted();
        this.finished = true;
        this.endDate = LocalDateTime.now();
        if (this.id >= 0) {
            try {
                final PreparedStatement s = this.dataManager.getConnection()
                        .prepareStatement("UPDATE executions SET stop=?, finished=TRUE WHERE id=" + this.id);
                s.setTimestamp(1, Timestamp.valueOf(this.endDate));
                s.execute();
            } catch (final SQLException e) {
                this.finished = false;
                this.endDate = null;
                throw e;
            }
        }
    }

    /**
     * Startet die Ausführung
     *
     * @throws SQLException Wenn die Änderung nicht in die Datenbank geschrieben werden kann
     */
    public void start() throws SQLException {
        this.assertNotDeleted();
        // Setze Startzeit nur einmal
        if (this.startDate != null) {
            return;
        }
        this.startDate = LocalDateTime.now();
        if (this.id >= 0) {
            try {
                final PreparedStatement s =
                        this.dataManager.getConnection().prepareStatement("UPDATE executions SET start=? WHERE id=?");
                s.setTimestamp(1, Timestamp.valueOf(this.startDate));
                s.setInt(2, this.id);
                s.execute();
            } catch (final SQLException e) {
                this.startDate = null;
                throw e;
            }
        }
    }

    /**
     * Setzt die Ausführung auf den Ursprungs-Zustand zurück
     *
     * @throws SQLException
     */
    public void reset() throws SQLException {
        this.startDate = null;
        this.endDate = null;
        this.finished = false;
        if (this.id >= 0) {
            final PreparedStatement s = this.dataManager.getConnection()
                    .prepareStatement("UPDATE executions SET start=?, stop=?, finished=? WHERE id=?");
            s.setTimestamp(1, null);
            s.setTimestamp(2, null);
            s.setBoolean(3, true);
            s.setInt(4, this.id);
            s.execute();
        }
    }

    /**
     * Gibt die ID der Ausführung zurück
     *
     * @return
     */
    public int getId() {
        return this.id;
    }

    /**
     * Gibt den Zeitpunkt des Starts zurück
     *
     * @return Den Zeitpunkt des Starts
     */
    public LocalDateTime getStartDate() {
        return this.startDate;
    }

    /**
     * Gibt die Endzeit der Ausführung zurück
     *
     * @return Die Endzeit des Ausführung
     */
    public LocalDateTime getEndDate() {
        if (!this.finished) {
            return this.startDate.plus(this.program.getMaxDuration());
        } else {
            return this.endDate;
        }
    }

    /**
     * Gibt die verbleibenden Sekunden bis zum Ende der Ausführung zurück
     *
     * @return Die verbleibenden Sekunden bis zum Ende der Ausführung
     */
    public Duration getRemainingTime() {
        if (this.finished) {
            return Duration.ZERO;
        } else {
            return Duration.between(LocalDateTime.now(), this.getEndDate());
        }
    }

    /**
     * Gibt die seit dem Programmstart vergangene Zeit zurück.
     *
     * @return Die seit dem Programmstart vergangene Zeit.
     */
    public Duration getElapsedTime() {
        if (this.startDate == null) {
            return Duration.ZERO;
        }
        if (this.finished) {
            if (this.getEndDate() == null) {
                return Duration.ZERO;
            }
            return Duration.between(this.getStartDate(), this.getEndDate());
        } else {
            return Duration.between(this.getStartDate(), LocalDateTime.now());
        }
    }

    /**
     * Returns the elapsed time since start as display string
     * @return
     */
    public String getElapsedTimeString() {
        long seconds = this.getElapsedTime().getSeconds();
        long absSeconds = Math.abs(seconds);
        String positive = String.format(
            "%d:%02d:%02d",
            absSeconds / 3600,
            (absSeconds % 3600) / 60,
            absSeconds % 60);
        return seconds < 0 ? "-" + positive : positive;
    }

    /**
     * Der früheste Zeitpunkt ab jetzt, zu dem das Programm aufgrund von
     * Leistungsmessung automatisch beendet werden darf.
     *
     * @return
     */
    public Duration getEarliestAutoEnd() {
        if (this.finished) {
            return Duration.ZERO;
        } else {
            final Duration earliest = this.program.getEarliestAutoEnd().minus(this.getElapsedTime());
            if (earliest.isNegative()) {
                return this.device.getAutoEndWaitTime();
            }
            return earliest.plus(this.device.getAutoEndWaitTime());
        }
    }

    /**
     * Gibt den Preis für die Programmausführung zurück
     *
     * @return Den Preis für die Programmausführung
     */
    public BigDecimal getPrice() {
        if (this.startDate == null) {
            return BigDecimal.ZERO;
        }
        if (this.finished) {
            if (this.endDate == null) {
                return this.program.getPrice(this.program.getMaxDuration(), this.user);
            }
            return this.program.getPrice(Duration.between(this.startDate, this.endDate), this.user);
        } else {
            final Duration timeSinceStart = Duration.between(this.startDate, LocalDateTime.now());
            if (timeSinceStart.compareTo(this.program.getMaxDuration()) > 0) {
                // Maximaldauer überschritten
                return this.program.getPrice(this.program.getMaxDuration(), this.user);
            } else {
                return this.program.getPrice(Duration.between(this.startDate, LocalDateTime.now()), this.user);
            }
        }
    }

    /**
     * Gibt das Gerät zurück, auf dem die Programmausführung läuft
     *
     * @return Das Geräte
     */
    public Device getDevice() {
        return this.device;
    }

    /**
     * Gibt das Programm dieser Ausführung zurück
     *
     * @return Das Programm
     */
    public Program getProgram() {
        return this.program;
    }

    /**
     * Gibt den Benutzer zurück, der diese Ausführung gestartet hat
     *
     * @return Den Benutzer
     */
    public User getUser() {
        return this.user;
    }

    /**
     * Prüft, ob die Höchstdauer des Programms abgelaufen, die Ausführung aber
     * noch nicht verrechnet ist.
     *
     * @return Wahr, wenn die Ausführung noch abgerechnet werden muss
     */
    public boolean isExpired() {
        if (this.startDate == null || this.finished) {
            return false;
        }
        return Duration.between(this.startDate, LocalDateTime.now()).compareTo(this.program.getMaxDuration()) > 0;
    }

    /**
     * Markiert die Ausführung als abgeschlossen.
     *
     * @throws SQLException Falls ein Fehler bei der Datenbankabfrage auftritt
     */
    public void finish() throws SQLException {
        this.stop();
    }

    /**
     * Entfernt den zu dieser Ausführung gehörigen Eintrag in der Datenbank
     *
     * @throws SQLException Falls ein Fehler bei der Datenbankabfrage auftritt
     */
    public void delete() throws SQLException {
        if (!this.deleted && this.id >= 0) {
            this.dataManager.getConnection().prepareCall("DELETE FROM executions WHERE id=" + this.id).execute();
            this.deleted = true;
        }
    }

    /**
     * Stellt sicher, dass die Ausführung nicht bereits gelöscht wurde.
     *
     * @throws SQLException Falls die Ausführung gelöscht wurde.
     */
    private void assertNotDeleted() throws SQLException {
        if (this.deleted) {
            throw new SQLException("This execution has been deleted.");
        }
    }

}
