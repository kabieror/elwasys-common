package org.kabieror.elwasys.common;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Diese Klasse stellt einen Standort dar, an dem Geräte stehen können.
 *
 * @author Oliver Kabierschke
 */
public class Location {

    private final DataManager dataManager;

    private final int id;
    private final List<UserGroup> validUserGroups;
    private String name;
    private String clientUid;
    private LocalDateTime clientLastSeen;

    private LocalDateTime lastUpdateTime;

    /**
     * Erstellt einen Standort anhand eines Datenbankeintrags
     *
     * @param dataManager Der Datenverwalter
     * @param res         Das Abfrageergebnis, aus dem der Standort gelesen werden soll
     */
    public Location(DataManager dataManager, ResultSet res) throws SQLException {
        this.dataManager = dataManager;

        this.id = res.getInt("id");
        this.validUserGroups = new ArrayList<>();

        this.update(res);
    }

    /**
     * Erstellt einen neuen Standort anhand eines Eintrags in der Datenbank
     *
     * @param dataManager Der Datenverwalter
     * @param id          Die ID des zu holenden Standorts
     */
    public Location(DataManager dataManager, int id) throws SQLException, NoDataFoundException {
        this.dataManager = dataManager;

        this.id = id;
        this.validUserGroups = new ArrayList<>();
        this.update();
    }

    /**
     * Erstellt einen neuen Standort in der Datenbank
     *
     * @param dataManager Der Datenverwalter
     * @param name        Der Name des neuen Standorts
     */
    public Location(DataManager dataManager, String name) throws SQLException {
        this.dataManager = dataManager;

        this.name = name;
        this.validUserGroups = new ArrayList<>();

        final PreparedStatement s = this.dataManager.getConnection()
                .prepareStatement("INSERT INTO locations (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
        s.setString(1, name);
        s.executeUpdate();

        final ResultSet res = s.getGeneratedKeys();
        if (res.next()) {
            this.id = res.getInt(1);
        } else {
            throw new SQLException("No ID received by database.");
        }
    }

    /**
     * Löscht diesen Standort aus der Datenbank
     *
     * @throws SQLException
     */
    public void delete() throws SQLException {
        this.dataManager.getConnection().prepareCall("DELETE FROM locations WHERE id=" + this.id).execute();
    }

    /**
     * Aktualisiert diesen Standort anhand seiner Repräsentation in der
     * Datenbank
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

        final ResultSet res =
                this.dataManager.getConnection().prepareCall("SELECT * FROM locations WHERE id=" + this.id)
                        .executeQuery();
        if (!res.next()) {
            throw new NoDataFoundException(
                    String.format("Der Standort '%1s' wurde aus der Datenbank gelöscht.", this.name));
        }
        this.update(res);
    }

    /**
     * Aktualisiert diesen Standort anhand seiner Repräsentation in der
     * Datenbank
     *
     * @param res Das Abfrageergebnis, aus dem die Daten zum Aktualisieren bezogen werden sollen
     * @throws SQLException
     */
    public void update(ResultSet res) throws SQLException {
        this.name = res.getString("name");
        final Timestamp ts = res.getTimestamp("client_last_seen");
        if (ts != null) {
            this.clientLastSeen = ts.toLocalDateTime();
        }
        this.clientUid = res.getString("client_uid");

        this.updateValidGroups();
    }

    private void updateValidGroups() throws SQLException {
        ResultSet res = this.dataManager.getConnection()
                .prepareCall("SELECT group_id FROM locations_valid_user_groups WHERE location_id=" + this.id)
                .executeQuery();
        this.validUserGroups.clear();
        if (res.isBeforeFirst()) {
            while (res.next()) {
                this.validUserGroups.add(this.dataManager.getUserGroupById(res.getInt("group_id")));
            }
        }
    }

    /**
     * Verändert diesen Standort
     *
     * @param name Der neue Name des Standorts
     */
    public void modify(String name, List<UserGroup> validUserGroups) throws SQLException {
        final PreparedStatement s =
                this.dataManager.getConnection().prepareStatement("UPDATE locations SET name=? WHERE id=?");
        s.setString(1, name);
        s.setInt(2, this.id);
        s.execute();

        this.name = name;

        // Benutzergruppen aktualisieren
        final List<UserGroup> skippedGroups = new Vector<>();
        final int oldGroupsCount = this.validUserGroups.size();
        for (final UserGroup g : validUserGroups) {
            if (this.validUserGroups.contains(g)) {
                skippedGroups.add(g);
                continue;
            }
            this.dataManager.getConnection().prepareCall(
                    "INSERT INTO locations_valid_user_groups (location_id, group_id) VALUES (" + this.id + ", " +
                            g.getId() + ")").execute();
            this.validUserGroups.add(g);
        }

        if (oldGroupsCount > skippedGroups.size()) {
            // Look for deleted groups
            for (int i = 0; i < this.validUserGroups.size(); i++) {
                if (!validUserGroups.contains(this.validUserGroups.get(i))) {
                    this.dataManager.getConnection().prepareCall(
                            "DELETE FROM locations_valid_user_groups WHERE location_id=" + this.id + " AND group_id=" +
                                    this.validUserGroups.get(i).getId()).execute();
                    this.validUserGroups.remove(i);
                    i--;
                }
            }
        }
    }

    /**
     * Registriert einen Client auf einen Ort. Die Registrierung muss spätestens
     * nach 5 Minuten aktualisiert werden, um gültig zu bleiben.
     *
     * @param uid   Die Identifikationsnummer des Clients.
     * @throws LocationOccupiedException Falls der Ort bereits auf einen anderen Client registriert ist.
     * @throws SQLException
     */
    public void registerClient(String uid) throws LocationOccupiedException, SQLException {
        if (!this.clientCanRegister(uid)) {
            throw new LocationOccupiedException(this.clientUid);
        }

        final LocalDateTime lastSeen = LocalDateTime.now();

        final PreparedStatement s = this.dataManager.getConnection().prepareStatement(
                "UPDATE locations SET client_uid=?, client_last_seen=? WHERE id=?");
        int i = 1;
        s.setString(i++, uid);
        s.setTimestamp(i++, Timestamp.valueOf(lastSeen));

        s.setInt(i++, this.id);
        s.execute();

        this.clientUid = uid;
        this.clientLastSeen = lastSeen;
    }

    /**
     * Löst die Registrierung eines Clients auf diesen Standort, sodass dieser
     * von einem anderen verwendet werden kann.
     *
     * @throws SQLException
     */
    public void releaseLocation() throws SQLException {
        final PreparedStatement s =
                this.dataManager.getConnection().prepareStatement("UPDATE locations SET client_uid=? WHERE id=?");
        s.setString(1, null);
        s.setInt(2, this.id);
        s.execute();

        this.clientUid = null;
    }

    private boolean clientCanRegister(String uid) {
        if (this.clientUid == null || this.clientUid.equals(uid)) {
            return true;
        } else {
            // Wenn der Client vor weniger als 5 Minuten die Datenbank
            // aktualisiert hat, ist der Eintrag noch gültig und der Ort gilt
            // als besetzt.
            return this.clientLastSeen == null ||
                    !Duration.between(this.clientLastSeen, LocalDateTime.now()).minus(Duration.ofMinutes(5))
                            .isNegative();
        }
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public LocalDateTime getClientLastSeen() {
        return this.clientLastSeen;
    }

    public List<UserGroup> getValidUserGroups() {
        return validUserGroups;
    }
}
