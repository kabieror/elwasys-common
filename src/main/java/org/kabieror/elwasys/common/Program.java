package org.kabieror.elwasys.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Ein Gerät (Device) kann mehrere Programme haben.
 *
 * @author Oliver Kabierschke
 */
public class Program {

    /**
     * Der Daten-Verwalter, der die Verbindung zur Datenbank bereit stellt
     */
    private final DataManager dataManager;
    /**
     * Die ID des Programms
     */
    private final int id;
    private final List<UserGroup> validUserGroups;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * Der Name des Programms
     */
    private String name;
    /**
     * Der Typ des Programms
     */
    private ProgramType type;
    /**
     * Der Aktivierungszustand des Programms
     */
    private boolean enabled;
    /**
     * Gibt an, ob das Programm aufgrund von Leistungsmessung automatisch
     * beendet werden soll.
     */
    private boolean autoEnd;

    /**
     * Gibt die Zeit ab Programmstart zurück, in der das Programm nicht
     * automatisch beendet werden soll.
     */
    private Duration earliestAutoEnd;

    /**
     * Die längste Dauer des Programms
     */
    private Duration maxDuration;

    /**
     * Die Zeit, in der das Programm kostenlos abgebrochen werden kann.
     */
    private Duration freeDuration;

    /**
     * Die Grundgebühr des Programms
     */
    private BigDecimal flagfall;

    /**
     * Der Zeitpreis des Programms
     */
    private BigDecimal rate;

    /**
     * Die Zeiteinheit des Zeitpreises
     */
    private ChronoUnit timeUnit;

    private LocalDateTime lastUpdateTime;

    public Program(DataManager dataManager, int id) throws SQLException, NoDataFoundException {
        this.dataManager = dataManager;
        this.id = id;
        this.validUserGroups = new ArrayList<>();
        this.update();
    }

    public Program(DataManager dataManager, ResultSet res) throws SQLException {
        this.dataManager = dataManager;
        this.id = res.getInt("id");
        this.validUserGroups = new ArrayList<>();
        this.update(res);
    }

    /**
     * Erstellt ein neues Programm in der Datenbank
     */
    public Program(DataManager dataManager, String name, ProgramType type, BigDecimal flagfall, BigDecimal rate,
                   ChronoUnit timeUnit, Duration maxDuration, Duration freeDuration, Boolean autoEnd,
                   Duration earliestAutoEnd, Boolean enabled, List<UserGroup> validUserGroups) throws SQLException {
        this.dataManager = dataManager;

        this.name = name;
        this.type = type;
        this.flagfall = flagfall;
        this.rate = rate;
        this.timeUnit = timeUnit;
        this.maxDuration = maxDuration;
        this.freeDuration = freeDuration;
        this.autoEnd = autoEnd;
        this.earliestAutoEnd = earliestAutoEnd;
        this.enabled = enabled;
        this.validUserGroups = validUserGroups;

        final PreparedStatement s = dataManager.getConnection().prepareStatement(
                "INSERT INTO programs (name, type, flagfall, rate, time_unit, max_duration, free_duration, auto_end, " +
                        "earliest_auto_end, enabled) VALUES (?, ?::PROGRAM_TYPE, ?, ?, ?::TIME_UNIT_TYPE, ?, ?, ?, ?," +
                        " ?)", Statement.RETURN_GENERATED_KEYS);
        int i = 1;
        s.setString(i++, name);
        s.setString(i++, this.getTypeString(type));
        s.setBigDecimal(i++, flagfall);
        s.setBigDecimal(i++, rate);
        s.setString(i++, this.getTimeUnitString(timeUnit));
        s.setInt(i++, new Long(maxDuration.getSeconds()).intValue());
        s.setInt(i++, new Long(freeDuration.getSeconds()).intValue());
        s.setBoolean(i++, this.autoEnd);
        s.setInt(i++, new Long(this.earliestAutoEnd.getSeconds()).intValue());
        s.setBoolean(i++, enabled);
        s.executeUpdate();

        final ResultSet res = s.getGeneratedKeys();
        if (res.next()) {
            this.id = res.getInt(1);
        } else {
            throw new SQLException("No ID received by database.");
        }

        // Relationen herstellen
        try {
            for (final UserGroup g : validUserGroups) {
                this.dataManager.getConnection().prepareCall(
                        "INSERT INTO programs_valid_user_groups (program_id, group_id) VALUES (" + this.id + ", " +
                                g.getId() + ")").execute();
            }
        } catch (final SQLException e) {
            // Fehler. Alles zurücksetzen.
            try {
                this.dataManager.getConnection()
                        .prepareCall("DELETE FROM programs_valid_user_groups WHERE program_id=" + this.id).execute();
            } catch (final SQLException e1) {
                // ignorieren und weiter.
                this.logger.error("Could not delete newly created relations between this program and its valid user " +
                        "groups", e1);
            }

            try {
                this.dataManager.getConnection().prepareCall("DELETE FROM programs WHERE id=" + this.id).execute();
            } catch (final SQLException e1) {
                // ignorieren und weiter.
                this.logger.error("Could not delete the newly created program.");
            }
            throw e;
        }
    }

    private Program(ProgramType type, BigDecimal flagfall, Duration maxDuration, Duration freeDuration, boolean autoEnd,
                    Duration earliestAutoEnd) {
        this.dataManager = null;
        this.id = -1;
        this.type = type;
        this.flagfall = BigDecimal.ZERO;
        this.maxDuration = maxDuration;
        this.freeDuration = Duration.ZERO;
        this.autoEnd = autoEnd;
        this.earliestAutoEnd = Duration.ZERO;
        this.enabled = true;
        this.validUserGroups = new ArrayList<>();
    }

    /**
     * Gibt das Programm zum öffnen einer Tür eines Geräts zurück
     *
     * @return Gibt ein 30-Sekunden-Programm zum Öffnen einer Tür zurück
     */
    public static Program getDoorOpenProgram() {
        return new Program(ProgramType.OPEN_DOOR, BigDecimal.ZERO, Duration.ofSeconds(30), Duration.ZERO, false,
                Duration.ZERO);
    }

    /**
     * Aktualisiert die Daten des Geräts mit denen aus der Datenbank
     *
     * @throws SQLException
     * @throws NoDataFoundException
     */
    public void update() throws SQLException, NoDataFoundException {
        // Only update after some time again
        if (this.lastUpdateTime != null &&
                Duration.between(this.lastUpdateTime, LocalDateTime.now()).minus(DataManager.UPDATE_DELAY)
                        .isNegative()) {
            return;
        }
        this.lastUpdateTime = LocalDateTime.now();

        final ResultSet res = this.dataManager.getConnection().prepareCall("SELECT * FROM programs WHERE id=" + this.id)
                .executeQuery();
        if (!res.next()) {
            throw new NoDataFoundException(
                    String.format("Das Programm '%1s' wurde aus der Datenbank gelöscht.", this.name));
        }
        this.update(res);
    }

    /**
     * Aktualisiert die Daten des Geräts mit denen aus der Datenbank
     *
     * @param res Das Abfrageergebnis mit denen das Gerät aktualisiert werden soll
     * @throws SQLException
     */
    public void update(ResultSet res) throws SQLException {
        this.name = res.getString("name");
        this.maxDuration = Duration.ofSeconds(res.getInt("max_duration"));
        this.freeDuration = Duration.ofSeconds(res.getInt("free_duration"));
        this.enabled = res.getBoolean("enabled");
        this.autoEnd = res.getBoolean("auto_end");
        this.earliestAutoEnd = Duration.ofSeconds(res.getInt("earliest_auto_end"));
        this.flagfall = res.getBigDecimal("flagfall");
        final String typeStr = res.getString("type");
        if (typeStr != null) {
            if (typeStr.equals("FIXED")) {
                this.type = ProgramType.FIXED;
            } else if (typeStr.equals("DYNAMIC")) {
                this.type = ProgramType.DYNAMIC;
            } else {
                this.logger.error("The type of program " + this.id + " is unknown.");
            }
        } else {
            this.logger.error("The type of program " + this.id + " is not set.");
        }
        if (this.type.equals(ProgramType.DYNAMIC)) {
            this.rate = res.getBigDecimal("rate");
            final String timeUnitStr = res.getString("time_unit");
            if (timeUnitStr != null) {
                if (timeUnitStr.equals("HOURS")) {
                    this.timeUnit = ChronoUnit.HOURS;
                } else if (timeUnitStr.equals("MINUTES")) {
                    this.timeUnit = ChronoUnit.MINUTES;
                } else {
                    this.timeUnit = ChronoUnit.SECONDS;
                    if (!timeUnitStr.equals("SECONDS")) {
                        this.logger.warn("The program with the id " + this.id +
                                " has an unknown time unit. Using seconds instead.");
                    }
                }
            } else {
                this.logger.warn("The time unit of program " + this.id + " is not set.");
            }
        }

        this.updateValidGroups();
    }

    private void updateValidGroups() throws SQLException {
        ResultSet res = this.dataManager.getConnection()
                .prepareCall("SELECT group_id FROM programs_valid_user_groups WHERE program_id=" + this.id)
                .executeQuery();
        this.validUserGroups.clear();
        if (res.isBeforeFirst()) {
            while (res.next()) {
                this.validUserGroups.add(this.dataManager.getUserGroupById(res.getInt("group_id")));
            }
        }
    }

    /**
     * Verändert das Programm und speichert die Änderung in der Datenbank ab.
     *
     * @param name
     * @param type
     * @param flagfall
     * @param rate
     * @param timeUnit
     * @param maxDuration
     * @param enabled
     * @throws SQLException
     */
    public void modify(String name, ProgramType type, BigDecimal flagfall, BigDecimal rate, ChronoUnit timeUnit,
                       Duration maxDuration, Duration freeDuration, Boolean autoEnd, Duration earliestAutoEnd,
                       Boolean enabled, List<UserGroup> validUserGroups) throws SQLException {
        final PreparedStatement s = this.dataManager.getConnection().prepareStatement(
                "UPDATE programs SET name=?, type=?::PROGRAM_TYPE, flagfall=?, " +
                        "rate=?, time_unit=?::TIME_UNIT_TYPE, max_duration=?, free_duration=?, auto_end=?, " +
                        "earliest_auto_end=?, enabled=? WHERE id=?");
        int i = 1;
        s.setString(i++, name);
        s.setString(i++, this.getTypeString(type));
        s.setBigDecimal(i++, flagfall);
        s.setBigDecimal(i++, rate);
        s.setString(i++, this.getTimeUnitString(timeUnit));
        s.setInt(i++, new Long(maxDuration.getSeconds()).intValue());
        s.setInt(i++, new Long(freeDuration.getSeconds()).intValue());
        s.setBoolean(i++, autoEnd);
        s.setInt(i++, new Long(earliestAutoEnd.getSeconds()).intValue());
        s.setBoolean(i++, enabled);
        s.setInt(i++, this.id);
        s.execute();

        this.name = name;
        this.type = type;
        this.flagfall = flagfall;
        this.rate = rate;
        this.timeUnit = timeUnit;
        this.maxDuration = maxDuration;
        this.freeDuration = freeDuration;
        this.autoEnd = autoEnd;
        this.earliestAutoEnd = earliestAutoEnd;
        this.enabled = enabled;

        // Benutzergruppen aktualisieren
        final List<UserGroup> skippedGroups = new Vector<>();
        final int oldGroupsCount = this.validUserGroups.size();
        for (final UserGroup g : validUserGroups) {
            if (this.validUserGroups.contains(g)) {
                skippedGroups.add(g);
                continue;
            }
            this.dataManager.getConnection().prepareCall(
                    "INSERT INTO programs_valid_user_groups (program_id, group_id) VALUES (" + this.id + ", " +
                            g.getId() + ")").execute();
            this.validUserGroups.add(g);
        }

        if (oldGroupsCount > skippedGroups.size()) {
            // Look for deleted groups
            for (i = 0; i < this.validUserGroups.size(); i++) {
                if (!validUserGroups.contains(this.validUserGroups.get(i))) {
                    this.dataManager.getConnection().prepareCall(
                            "DELETE FROM programs_valid_user_groups WHERE program_id=" + this.id + " AND group_id=" +
                                    this.validUserGroups.get(i).getId()).execute();
                    this.validUserGroups.remove(i);
                    i--;
                }
            }
        }
    }

    /**
     * Gibt den Preis des Programms nach einer Dauer zurück
     *
     * @param duration Die Dauer, auf deren Basis der Preis berechnet werden soll
     * @param user     Der Benuzter, für den ein Preis berechnet werden soll.
     * @return Der Preis auf Basis der angegebenen Dauer
     */
    public BigDecimal getPrice(Duration duration, User user) {
        if (duration.compareTo(this.freeDuration) <= 0) {
            return BigDecimal.ZERO;
        }

        if (user == null) {
            user = User.getAnonymous();
        }

        BigDecimal price = null;
        switch (this.type) {
            case DYNAMIC:
                price = this.getDynamicPrice(duration);
                break;
            case FIXED:
                price = this.flagfall;
                break;
            case OPEN_DOOR:
                price = BigDecimal.ZERO;
                break;
        }
        if (price != null) {
            if (user.getGroup().getDiscountType() == DiscountType.Factor) {
                return price.subtract(price.multiply(new BigDecimal(user.getGroup().getDiscountValue())));
            } else if (user.getGroup().getDiscountType() == DiscountType.Fix) {
                return price.subtract(new BigDecimal(user.getGroup().getDiscountValue()));
            } else {
                return price;
            }
        }
        return null;
    }

    private BigDecimal getDynamicPrice(Duration duration) {
        final BigDecimal factor;
        switch (this.timeUnit) {
            case SECONDS:
                factor = new BigDecimal(duration.getSeconds());
                break;
            case MINUTES:
                factor = new BigDecimal(duration.getSeconds() / 60);
                break;
            case HOURS:
                factor = new BigDecimal(duration.getSeconds() / 3600);
                break;
            default:
                throw new UnsupportedTemporalTypeException(
                        "The temporal unit " + this.timeUnit.name() + " is not supported.");
        }
        return this.flagfall.add(this.rate.multiply(factor));
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public Duration getMaxDuration() {
        return this.maxDuration;
    }

    public Duration getFreeDuration() {
        return this.freeDuration;
    }

    public boolean isAutoEnd() {
        return this.autoEnd;
    }

    public Duration getEarliestAutoEnd() {
        return this.earliestAutoEnd;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public ProgramType getType() {
        return this.type;
    }

    public BigDecimal getFlagfall() {
        return this.flagfall;
    }

    public BigDecimal getRate() {
        return this.rate;
    }

    public ChronoUnit getTimeUnit() {
        return this.timeUnit;
    }

    public List<UserGroup> getValidUserGroups() {
        return this.validUserGroups;
    }

    /**
     * Generiert aus einer Zeiteinheit einen String für die Datenbank
     *
     * @param timeUnit Die umzuwandelnde Zeiteinheit
     * @return Der String, der die Zeiteinheit repräsentiert
     */
    private String getTimeUnitString(ChronoUnit timeUnit) {
        if (timeUnit == null) {
            return null;
        }
        switch (timeUnit) {
            case HOURS:
                return "HOURS";
            case MINUTES:
                return "MINUTES";
            case SECONDS:
            default:
                return "SECONDS";
        }
    }

    /**
     * Generiert aus einem Programmtyp einen String für die Datenbank
     *
     * @param type Der umzuwandelnde Programmtyp
     * @return Der String, der den Programmtyp repräsentiert
     */
    private String getTypeString(ProgramType type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case DYNAMIC:
                return "DYNAMIC";
            case FIXED:
            default:
                return "FIXED";
        }
    }

    /**
     * Löscht dieses Programm in der Datenbank
     *
     * @throws SQLException
     */
    public void delete() throws SQLException {
        this.dataManager.getConnection().prepareCall("DELETE FROM programs WHERE id=" + this.id).execute();
    }
}
