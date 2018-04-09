package org.kabieror.elwasys.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Ein Gerät (Device) ist eine Waschmaschine bzw. ein Trockner und kann vom
 * Waschwächter geschaltet werden.
 *
 * @author Oliver Kabierschke
 */
public class Device {

    /**
     * Der Daten-Verwalter, der die Verbindung zur Datenbank bereit stellt
     */
    private final DataManager dataManager;
    /**
     * Die ID des Geräts
     */
    private final int id;
    /**
     * Liste mit Programmen des Geräts.
     */
    private final List<Program> programs;
    /**
     * Liste der auf diesem Gerät erlaubten Benutzergruppen
     */
    private final List<UserGroup> validUserGroups;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * Die Anzeige-Position des Geräts
     */
    private int position;
    /**
     * Der Name des Geräts.
     */
    private String name;
    /**
     * Der Name des Geräts im FHEM-Server.
     */
    private String fhemName;

    /**
     * Der Name des Schalters, welcher im FHEM-Server zum Schalten des Geräts
     * angesprochen werden soll.
     */
    private String fhemSwitchName;

    /**
     * Der Name des Leistungs-Messungs-Kanals dieses Geräts im Fhem-Server.
     */
    private String fhemPowerName;

    /**
     * Der Grenzwert für die Leistungsabnahme des Geräts, unter welchem ein
     * darauf laufendes Programm automatisch beendet wird.
     */
    private float autoEndPowerThreashold;

    /**
     * Die Zeit, die nach dem unterschreiden des Grenzwerts gewartet werden
     * soll, bevor ein auf dem Gerät laufendes Programm automatisch beendet
     * wird.
     */
    private Duration autoEndWaitTime;

    /**
     * Gibt an, ob das Gerät aktiviert ist
     */
    private boolean enabled;

    /**
     * Der Standort des Geräts
     */
    private Location location;

    /**
     * Die aktuell laufende Programmausführung
     */
    private Execution currentExecution;

    private LocalDateTime lastUpdateTime;

    /**
     * Erstellt ein lokales Abbild eines Gerätes in der Datenbank.
     */
    public Device(DataManager dataManager, ResultSet res) throws SQLException {
        this.dataManager = dataManager;
        this.id = res.getInt("id");
        this.programs = new ArrayList<>();
        this.validUserGroups = new ArrayList<>();
        this.update(res);
    }

    /**
     * Holt ein Gerät anhand seiner ID aus der Datenbank
     *
     * @param dataManager Die Datenbank
     * @param id          Die ID des Geräts
     * @throws SQLException Wenn die Daten nicht aus der Datenbank geladen werden können
     */
    public Device(DataManager dataManager, int id) throws SQLException, NoDataFoundException {
        this.dataManager = dataManager;
        this.id = id;
        this.programs = new ArrayList<>();
        this.validUserGroups = new ArrayList<>();
        this.update();
    }

    /**
     * Erstellt ein neues Gerät in der Datenbank
     *
     * @param dataManager Der Datenmanager
     * @param name        Der Name des neuen Geräts
     * @param location    Der Standort des neuen Geräts
     * @param enabled     Ob das Gerät aktiv ist
     * @param programs    Die Programme, die auf dem Gerät verwendet werden können
     * @throws SQLException Falls ein Fehler beim Eintragen in die Datenbank auftritt
     */
    public Device(DataManager dataManager, String name, int position, Location location, String fhem_name,
                  String fhem_switch_name, String fhem_power_name, float autoEndPowerThreashold,
                  Duration autoEndWaitTime, boolean enabled, List<Program> programs, List<UserGroup> validUserGroups)
            throws SQLException {
        this.dataManager = dataManager;

        this.name = name;
        this.position = position;
        this.location = location;
        this.fhemName = fhem_name;
        this.fhemSwitchName = fhem_switch_name;
        this.fhemPowerName = fhem_power_name;
        this.autoEndPowerThreashold = autoEndPowerThreashold;
        this.autoEndWaitTime = autoEndWaitTime;
        this.enabled = enabled;
        this.programs = programs;
        this.validUserGroups = validUserGroups;

        final PreparedStatement s = this.dataManager.getConnection().prepareStatement(
                "INSERT INTO devices (name, position, location_id, fhem_name, " +
                        "fhem_switch_name, fhem_power_name, auto_end_power_threashold, auto_end_wait_time, " +
                        "enabled) VALUES (?, ?, ?, ?, " + "?, ?, ?, ?, " + "?)", Statement.RETURN_GENERATED_KEYS);

        int i = 1;
        s.setString(i++, name);
        s.setInt(i++, position);
        s.setInt(i++, location.getId());
        s.setString(i++, fhem_name);
        s.setString(i++, fhem_switch_name);
        s.setString(i++, fhem_power_name);
        s.setFloat(i++, autoEndPowerThreashold);
        s.setInt(i++, new Long(autoEndWaitTime.getSeconds()).intValue());
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
            for (final Program p : programs) {
                this.dataManager.getConnection().prepareCall(
                        "INSERT INTO device_program_rel (device_id, program_id) VALUES (" + this.id + ", " + p.getId() +
                                ")").execute();
            }
            for (final UserGroup g : validUserGroups) {
                this.dataManager.getConnection().prepareCall(
                        "INSERT INTO devices_valid_user_groups (device_id, group_id) VALUES (" + this.id + ", " +
                                g.getId() + ")").execute();
            }
        } catch (final SQLException e) {
            // Fehler. Alles zurücksetzen.
            try {
                this.dataManager.getConnection()
                        .prepareCall("DELETE FROM device_program_rel WHERE device_id=" + this.id).execute();
            } catch (final SQLException e1) {
                // ignorieren und weiter.
                this.logger.error("Could not delete newly created relations between this device and its programs", e1);
            }
            try {
                this.dataManager.getConnection()
                        .prepareCall("DELETE FROM devices_valid_user_groups WHERE device_id=" + this.id).execute();
            } catch (final SQLException e1) {
                // ignorieren und weiter.
                this.logger
                        .error("Could not delete newly created relations between this device and its valid user groups",
                                e1);
            }

            try {
                this.dataManager.getConnection().prepareCall("DELETE FROM devices WHERE id=" + this.id).execute();
            } catch (final SQLException e1) {
                // ignorieren und weiter.
                this.logger.error("Could not delete the newly created device.");
            }
            throw e;
        }
    }

    /**
     * Verändert das Gerät
     *
     * @param name     Der neue Name des Geräts
     * @param location Der neue Standort des Geräts
     * @param enabled  Der neue Aktivierungs-Zustand des Geräts
     * @param programs Die neuen Programm des Geräts
     * @throws SQLException Falls beim Aktualisieren der Datenbank ein Fehler auftritt
     */
    public void modify(String name, int position, Location location, String fhemName, String fhemSwitchName,
                       String fhemPowerName, float autoEndPowerThreashold, Duration autoEndWaitTime, boolean enabled,
                       List<Program> programs, List<UserGroup> validUserGroups) throws SQLException {
        final PreparedStatement s = this.dataManager.getConnection().prepareStatement(
                "UPDATE devices SET name=?, position=?, location_id=?, fhem_name=?, fhem_switch_name=?, " +
                        "fhem_power_name=?, " +
                        "auto_end_power_threashold=?, auto_end_wait_time=?, enabled=? WHERE id=?");
        {
            int i = 1;
            s.setString(i++, name);
            s.setInt(i++, position);
            s.setInt(i++, location.getId());
            s.setString(i++, fhemName);
            s.setString(i++, fhemSwitchName);
            s.setString(i++, fhemPowerName);
            s.setFloat(i++, autoEndPowerThreashold);
            s.setInt(i++, new Long(autoEndWaitTime.getSeconds()).intValue());
            s.setBoolean(i++, enabled);
            s.setInt(i++, this.id);
        }

        s.execute();

        this.name = name;
        this.position = position;
        this.location = location;
        this.fhemName = fhemName;
        this.fhemSwitchName = fhemSwitchName;
        this.fhemPowerName = fhemPowerName;
        this.autoEndPowerThreashold = autoEndPowerThreashold;
        this.autoEndWaitTime = autoEndWaitTime;
        this.enabled = enabled;

        // Programme aktualisieren
        final List<Program> skippedPrograms = new Vector<>();
        final int oldProgramsCount = this.programs.size();
        for (final Program p : programs) {
            if (this.programs.contains(p)) {
                skippedPrograms.add(p);
                continue;
            }
            this.dataManager.getConnection().prepareCall(
                    "INSERT INTO device_program_rel (device_id, program_id) VALUES (" + this.id + ", " + p.getId() +
                            ")").execute();
            this.programs.add(p);
        }

        if (oldProgramsCount > skippedPrograms.size()) {
            // Look for deleted programs
            for (int i = 0; i < this.programs.size(); i++) {
                if (!programs.contains(this.programs.get(i))) {
                    this.dataManager.getConnection().prepareCall(
                            "DELETE FROM device_program_rel WHERE device_id=" + this.id + " AND program_id=" +
                                    this.programs.get(i).getId()).execute();
                    this.programs.remove(i);
                    i--;
                }
            }
        }

        // Benutzergruppen aktualisieren
        final List<UserGroup> skippedGroups = new Vector<>();
        final int oldGroupsCount = this.validUserGroups.size();
        for (final UserGroup g : validUserGroups) {
            if (this.validUserGroups.contains(g)) {
                skippedGroups.add(g);
                continue;
            }
            this.dataManager.getConnection().prepareCall(
                    "INSERT INTO devices_valid_user_groups (device_id, group_id) VALUES (" + this.id + ", " +
                            g.getId() + ")").execute();
            this.validUserGroups.add(g);
        }

        if (oldGroupsCount > skippedGroups.size()) {
            // Look for deleted groups
            for (int i = 0; i < this.validUserGroups.size(); i++) {
                if (!validUserGroups.contains(this.validUserGroups.get(i))) {
                    this.dataManager.getConnection().prepareCall(
                            "DELETE FROM devices_valid_user_groups WHERE device_id=" + this.id + " AND group_id=" +
                                    this.validUserGroups.get(i).getId()).execute();
                    this.validUserGroups.remove(i);
                    i--;
                }
            }
        }
    }

    /**
     * Löscht das Geät aus der Datenbank
     *
     * @throws SQLException
     */
    public void delete() throws SQLException {
        // Gerät löschen
        this.dataManager.getConnection().prepareCall("DELETE FROM devices WHERE id=" + this.id).execute();
    }

    /**
     * Aktualisiert die Daten des Geräts mit denen aus der Datenbank
     *
     * @throws SQLException
     */
    public void update() throws SQLException, NoDataFoundException {
        // Only update after some time again
        if (this.lastUpdateTime != null &&
                Duration.between(this.lastUpdateTime, LocalDateTime.now()).minus(DataManager.UPDATE_DELAY)
                        .isNegative()) {
            return;
        }
        this.lastUpdateTime = LocalDateTime.now();

        final ResultSet res = this.dataManager.getConnection().prepareCall("SELECT * FROM devices WHERE id=" + this.id)
                .executeQuery();
        if (!res.next()) {
            throw new NoDataFoundException(
                    String.format("Das Gerät '%1s' wurde aus der Datenbank gelöscht.", this.name));
        }
        this.update(res);
    }

    /**
     * Aktualisiert die Daten des Geräts mit denen aus einem Abfrageergebnis
     *
     * @param res Das Abfrageergebnis, mit denen die Daten des Gerätes aktualisiert werden sollen
     * @throws SQLException
     */
    public void update(ResultSet res) throws SQLException {
        this.name = res.getString("name");
        this.position = res.getInt("position");
        this.fhemName = res.getString("fhem_name");
        this.fhemSwitchName = res.getString("fhem_switch_name");
        this.fhemPowerName = res.getString("fhem_power_name");
        this.autoEndPowerThreashold = res.getFloat("auto_end_power_threashold");
        this.autoEndWaitTime = Duration.ofSeconds(res.getInt("auto_end_wait_time"));
        this.enabled = res.getBoolean("enabled");
        this.location = this.dataManager.getLocation(res.getInt("location_id"));

        this.updatePrograms();
        this.updateValidGroups();
    }

    private void updatePrograms() throws SQLException {
        ResultSet res = this.dataManager.getConnection()
                .prepareCall("SELECT program_id FROM device_program_rel WHERE device_id=" + this.id).executeQuery();
        this.programs.clear();
        if (res.isBeforeFirst()) {
            while (res.next()) {
                this.programs.add(this.dataManager.getProgramById(res.getInt("program_id")));
            }
        }
    }

    private void updateValidGroups() throws SQLException {
        ResultSet res = this.dataManager.getConnection()
                .prepareCall("SELECT group_id FROM devices_valid_user_groups WHERE device_id=" + this.id)
                .executeQuery();
        this.validUserGroups.clear();
        if (res.isBeforeFirst()) {
            while (res.next()) {
                this.validUserGroups.add(this.dataManager.getUserGroupById(res.getInt("group_id")));
            }
        }
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public int getPosition() {
        return this.position;
    }

    public String getFhemName() {
        return this.fhemName;
    }

    public String getFhemSwitchName() {
        return this.fhemSwitchName;
    }

    public String getFhemPowerName() {
        return this.fhemPowerName;
    }

    public float getAutoEndPowerThreashold() {
        return this.autoEndPowerThreashold;
    }

    public Duration getAutoEndWaitTime() {
        return this.autoEndWaitTime;
    }

    public List<Program> getPrograms() {
        return this.programs;
    }

    public List<UserGroup> getValidUserGroups() {
        return this.validUserGroups;
    }

    /**
     * Gibt alle Programme zurück, die der Benutzergruppe des gegebenene Benutzers zur Verfügung stehen.
     *
     * @param user Der Benutzer, dessen zur Verfügung stehende Programme geladen werden sollen.
     */
    public List<Program> getPrograms(User user) {
        List<Program> res = new Vector<>();
        for (Program p : this.programs) {
            if (p.getValidUserGroups().contains(user.getGroup())) {
                res.add(p);
            }
        }
        return res;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public Location getLocation() {
        return this.location;
    }

    public Execution getCurrentExecution() {
        return this.currentExecution;
    }

    public void onExecutionStarted(Execution e) {
        this.currentExecution = e;
    }

    public void onExecutionEnded() {
        this.currentExecution = null;
    }
}
