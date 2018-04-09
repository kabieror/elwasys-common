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
 * Eine Benutzergruppe
 *
 * @author Oliver Kabierschke
 */
public class UserGroup {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Der Daten-Verwalter, der die Verbindung zur Datenbank bereit stellt
     */
    private final DataManager dataManager;

    /**
     * Die ID des Benutzers
     */
    private final int id;
    private final List<Location> validLocations;
    private final List<Device> validDevices;
    private final List<Program> validPrograms;
    /**
     * Der Name der Benutzergruppe
     */
    private String name;
    /**
     * Gibt an, ob die Benutzer eine pauschale Rabattierung erhält
     */
    private DiscountType discountType;
    /**
     * Gibt den Faktor an, mit dem jeder zu zahlende Preis multipliziert wird
     */
    private double discountValue;
    private LocalDateTime lastUpdateTime;
    private LocalDateTime lastLocationsUpdateTime;
    private LocalDateTime lastDevicesUpdateTime;
    private LocalDateTime lastProgramsUpdateTime;

    /**
     * Lädt eine Benutzergruppe mit Daten aus der Datenbank.
     *
     * @param dataManager Der Datenmanager
     * @param res         Das Abfrageergebnis, aus dem die Gruppe geladen werden soll
     */
    UserGroup(DataManager dataManager, ResultSet res) throws SQLException {
        this.dataManager = dataManager;
        this.id = res.getInt("id");
        this.validLocations = new ArrayList<>();
        this.validDevices = new ArrayList<>();
        this.validPrograms = new ArrayList<>();
        this.load(res);
    }

    /**
     * Lädt eine Benutzergruppe anhand ihrer ID aus der Datenbank.
     */
    UserGroup(DataManager dataManager, int id) throws NoDataFoundException, SQLException {
        this.dataManager = dataManager;
        this.id = id;
        this.validLocations = new ArrayList<>();
        this.validDevices = new ArrayList<>();
        this.validPrograms = new ArrayList<>();
        load();
    }

    /**
     * Erzeugt eine neue Benutzergruppe in der Datenbank.
     */
    public UserGroup(DataManager dataManager, String name, DiscountType discountType, double discountValue)
            throws SQLException {
        this.dataManager = dataManager;

        this.validLocations = new ArrayList<>();
        this.validDevices = new ArrayList<>();
        this.validPrograms = new ArrayList<>();

        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;

        final PreparedStatement s = dataManager.getConnection().prepareStatement(
                "INSERT INTO user_groups (name, discount_type, discount_value) VALUES " + "(?, ?::DISCOUNT_TYPE, ?)",
                Statement.RETURN_GENERATED_KEYS);
        int i = 1;
        s.setString(i++, name);
        switch (discountType) {
            case Factor:
                s.setString(i++, "FACTOR");
                break;
            case Fix:
                s.setString(i++, "FIX");
                break;
            default:
                s.setString(i++, "NONE");
                break;
        }
        s.setDouble(i++, discountValue);
        s.executeUpdate();

        final ResultSet res = s.getGeneratedKeys();
        if (res.next()) {
            this.id = res.getInt(1);
        } else {
            throw new SQLException("No ID received by database.");
        }
    }

    /**
     * Erzeugt eine Benutzergruppe ohne anbindung zur Datenbank.
     */
    UserGroup(String name, DiscountType discountType, double discountValue) {
        this.id = 0;
        this.dataManager = null;

        this.validLocations = new ArrayList<>();
        this.validDevices = new ArrayList<>();
        this.validPrograms = new ArrayList<>();

        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    /**
     * Verändert die Eigenschaften der Benutzergruppe.
     *
     * @param name          Der neue Name der Benutzergruppe.
     * @param discountType  Der neue Rabattierungstyp der Benutzergruppe.
     * @param discountValue Der neue Rabattierungswert der Benutzergruppe.
     */
    public void modify(String name, DiscountType discountType, double discountValue) throws SQLException {
        final PreparedStatement s = this.dataManager.getConnection().prepareStatement(
                "UPDATE user_groups SET name=?, discount_type=?::DISCOUNT_TYPE, discount_value=? WHERE id=?");
        int i = 1;
        s.setString(i++, name);
        switch (discountType) {
            case Factor:
                s.setString(i++, "FACTOR");
                break;
            case Fix:
                s.setString(i++, "FIX");
                break;
            default:
                s.setString(i++, "NONE");
                break;
        }
        s.setDouble(i++, discountValue);
        s.setInt(i++, this.id);
        s.execute();

        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    /**
     * Löscht die Benutzergruppe aus der Datenbank.
     */
    public void delete() throws SQLException {
        this.dataManager.getConnection().prepareCall(String.format(
                "UPDATE users SET group_id=(SELECT id FROM user_groups WHERE id<>%d LIMIT 1) WHERE group_id=%d",
                this.id, this.id)).execute();
        this.dataManager.getConnection().prepareCall("DELETE FROM user_groups WHERE id=" + this.id).execute();
    }

    /**
     * Aktualisiert die Daten der Benutzergruppe mit denen aus der Datenbank
     */
    public void update() throws NoDataFoundException, SQLException {
        // Only update after some time again
        if (this.lastUpdateTime != null &&
                Duration.between(this.lastUpdateTime, LocalDateTime.now()).minus(DataManager.UPDATE_DELAY)
                        .isNegative()) {
            return;
        }
        this.lastUpdateTime = LocalDateTime.now();

        this.load();
    }

    /**
     * Aktualisiert die Daten der Benutzergruppe mit denen aus einem Abfrageergebnis
     *
     * @param res Das Abfrageergebnis, mit dem die Gruppe aktualisiert werden soll
     */
    public void update(ResultSet res) throws SQLException {
        this.load(res);
    }

    /**
     * Befüllt die Felder dieses Objekts mit Werten aus der Datenbank
     *
     * @throws NoDataFoundException Wenn der zugehörige Datensatz nicht in der Datenbank zu finden ist
     * @throws SQLException         Wenn beim Laden der Daten ein Fehler auftritt
     */
    private void load() throws NoDataFoundException, SQLException {
        final ResultSet res =
                this.dataManager.getConnection().prepareCall("SELECT * FROM user_groups WHERE id=" + this.id)
                        .executeQuery();
        if (res.isBeforeFirst() && res.next()) {
            this.load(res);
        } else {
            throw new NoDataFoundException(
                    String.format("Die Benutzergruppe '%1s' wurde aus der Datenbank gelöscht.", this.name));
        }
    }

    /**
     * Befüllt die Felder dieses Objekts mit Werten aus einem Abfrageergebnis
     *
     * @param res Das Abfrageergebnis aus dem die Werte der Gruppe zu entnehmen sind
     * @throws SQLException Wenn beim Laden der Daten ein Fehler auftritt
     */
    private void load(ResultSet res) throws SQLException {
        this.name = res.getString("name");
        String dt = res.getString("discount_type");
        switch (dt) {
            case "FIX":
                this.discountType = DiscountType.Fix;
                break;
            case "FACTOR":
                this.discountType = DiscountType.Factor;
                break;
            default:
                this.discountType = DiscountType.None;
                break;
        }
        this.discountValue = res.getDouble("discount_value");
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public double getDiscountValue() {
        return discountValue;
    }

    public List<Location> getValidLocations() throws SQLException {
        // Only update after some time again
        if (this.lastLocationsUpdateTime != null &&
                Duration.between(this.lastLocationsUpdateTime, LocalDateTime.now()).minus(DataManager.UPDATE_DELAY)
                        .isNegative()) {
            return this.validLocations;
        }
        this.lastLocationsUpdateTime = LocalDateTime.now();

        ResultSet res = this.dataManager.getConnection().prepareCall(
                "SELECT * FROM locations_valid_user_groups val LEFT JOIN locations loc ON loc.id=val.location_id " +
                        "WHERE val.group_id=" + this.id).executeQuery();
        this.validLocations.clear();
        if (res.isBeforeFirst()) {
            while (res.next()) {
                this.validLocations.add(this.dataManager.getLocation(res));
            }
        }
        return this.validLocations;
    }

    public void setValidLocations(List<Location> valid) throws SQLException {
        final List<Location> skippedLocations = new Vector<>();
        final int oldLocationsCount = this.validLocations.size();
        for (final Location l : valid) {
            if (this.validLocations.contains(l)) {
                skippedLocations.add(l);
                continue;
            }
            this.dataManager.getConnection().prepareCall(
                    "INSERT INTO locations_valid_user_groups (location_id, group_id) VALUES (" + l.getId() + ", " +
                            this.id + ")").execute();
            this.validLocations.add(l);
        }

        if (oldLocationsCount > skippedLocations.size()) {
            // Look for deleted items
            for (int i = 0; i < this.validLocations.size(); i++) {
                if (!valid.contains(this.validLocations.get(i))) {
                    this.dataManager.getConnection().prepareCall(
                            "DELETE FROM locations_valid_user_groups WHERE location_id=" +
                                    this.validLocations.get(i).getId() + " AND group_id=" + this.id).execute();
                    this.validLocations.remove(i);
                    i--;
                }
            }
        }
    }

    public List<Device> getValidDevices() throws SQLException {
        // Only update after some time again
        if (this.lastDevicesUpdateTime != null &&
                Duration.between(this.lastDevicesUpdateTime, LocalDateTime.now()).minus(DataManager.UPDATE_DELAY)
                        .isNegative()) {
            return this.validDevices;
        }
        this.lastDevicesUpdateTime = LocalDateTime.now();

        ResultSet res = this.dataManager.getConnection().prepareCall(
                "SELECT * FROM devices_valid_user_groups val LEFT JOIN devices dev ON dev.id=val.device_id WHERE val" +
                        ".group_id=" + this.id).executeQuery();
        this.validDevices.clear();
        if (res.isBeforeFirst()) {
            while (res.next()) {
                this.validDevices.add(this.dataManager.getDevice(res));
            }
        }
        return this.validDevices;
    }

    public void setValidDevices(List<Device> valid) throws SQLException {
        final List<Device> skippedDevices = new Vector<>();
        final int oldDevicesCount = this.validDevices.size();
        for (final Device d : valid) {
            if (this.validDevices.contains(d)) {
                skippedDevices.add(d);
                continue;
            }
            this.dataManager.getConnection().prepareCall(
                    "INSERT INTO devices_valid_user_groups (device_id, group_id) VALUES (" + d.getId() + ", " +
                            this.id + ")").execute();
            this.validDevices.add(d);
        }

        if (oldDevicesCount > skippedDevices.size()) {
            // Look for deleted items
            for (int i = 0; i < this.validDevices.size(); i++) {
                if (!valid.contains(this.validDevices.get(i))) {
                    this.dataManager.getConnection().prepareCall(
                            "DELETE FROM devices_valid_user_groups WHERE device_id=" +
                                    this.validDevices.get(i).getId() + " AND group_id=" + this.id).execute();
                    this.validDevices.remove(i);
                    i--;
                }
            }
        }
    }

    public List<Program> getValidPrograms() throws SQLException {
        // Only update after some time again
        if (this.lastProgramsUpdateTime != null &&
                Duration.between(this.lastProgramsUpdateTime, LocalDateTime.now()).minus(DataManager.UPDATE_DELAY)
                        .isNegative()) {
            return this.validPrograms;
        }
        this.lastProgramsUpdateTime = LocalDateTime.now();

        ResultSet res = this.dataManager.getConnection().prepareCall(
                "SELECT * FROM programs_valid_user_groups val LEFT JOIN programs pro ON pro.id=val.program_id WHERE " +
                        "val.group_id=" + this.id).executeQuery();
        this.validPrograms.clear();
        if (res.isBeforeFirst()) {
            while (res.next()) {
                this.validPrograms.add(this.dataManager.getProgram(res));
            }
        }
        return this.validPrograms;
    }

    public void setValidPrograms(List<Program> valid) throws SQLException {
        final List<Program> skippedPrograms = new Vector<>();
        final int oldProgramsCount = this.validPrograms.size();
        for (final Program p : valid) {
            if (this.validPrograms.contains(p)) {
                skippedPrograms.add(p);
                continue;
            }
            this.dataManager.getConnection().prepareCall(
                    "INSERT INTO programs_valid_user_groups (program_id, group_id) VALUES (" + p.getId() + ", " +
                            this.id + ")").execute();
            this.validPrograms.add(p);
        }

        if (oldProgramsCount > skippedPrograms.size()) {
            // Look for deleted items
            for (int i = 0; i < this.validPrograms.size(); i++) {
                if (!valid.contains(this.validPrograms.get(i))) {
                    this.dataManager.getConnection().prepareCall(
                            "DELETE FROM programs_valid_user_groups WHERE program_id=" +
                                    this.validPrograms.get(i).getId() + " AND group_id=" + this.id).execute();
                    this.validPrograms.remove(i);
                    i--;
                }
            }
        }
    }
}
