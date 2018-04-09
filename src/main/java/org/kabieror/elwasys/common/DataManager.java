package org.kabieror.elwasys.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Duration;
import java.util.*;

/**
 * Diese Klasse stellt Methoden zum holen von Informationen aus der Datenbank
 * bereit.
 *
 * @author Oliver Kabierschke
 */
public class DataManager {

    /**
     * Aktualisiere nicht öfter als einmal alle 5 Sekunden aus der Datenbank.
     */
    static final Duration UPDATE_DELAY = Duration.ofSeconds(5);

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ConfigurationManager config;
    private final Map<Integer, Location> locations = new HashMap<>();
    private final Map<Integer, UserGroup> userGroups = new HashMap<>();
    private final Map<Integer, User> users = new HashMap<>();
    private final Map<Integer, Program> programs = new HashMap<>();
    private final Map<Integer, Device> devices = new HashMap<>();
    private final Map<Integer, Execution> executions = new HashMap<>();
    private final Properties dbProperties;
    private Connection db;


    /**
     * Constructor
     *
     * @throws ClassNotFoundException Wenn der Datenbanktreiber nicht geladen werden kann
     */
    public DataManager(ConfigurationManager config) throws ClassNotFoundException {
        this.logger.info("Loading database driver");

        this.config = config;

        dbProperties = new Properties();
        dbProperties.setProperty("user", this.config.getDatabaseUser());
        dbProperties.setProperty("password", this.config.getDatabasePassword());
        if (this.config.getDatabaseUseSsl()) {
            dbProperties.setProperty("ssl", "true");
        }

        if (!this.config.getDatabaseUseSsl()) {
            this.logger.warn("Insecure database connection is to be established.");
            this.logger.warn("To secure the database connection set 'database.useSsl=true' in the configuration file.");
        }

        // Datenbanktreiber laden
        Class.forName("org.postgresql.Driver");
    }

    /**
     * Prüft die Datenbankverbindung und baut bei Bedarf eine neue auf
     *
     * @throws SQLException Wenn keine neue Datenbankverbindung aufgebaut werden kann
     */
    public Connection getConnection() throws SQLException {
        if (this.db != null) {
            // Prüfe Datenbankverbindung
            try {
                this.db.prepareCall("SELECT 1").execute();
            } catch (final SQLException e) {
                this.logger.warn("Database connection is corrupt.");
            }
        }
        if (this.db == null || this.db.isClosed()) {
            this.logger.info("Trying to open new database connection with server " + this.config.getDatabaseServer());
            final String url =
                    "jdbc:postgresql://" + this.config.getDatabaseServer() + "/" + this.config.getDatabaseName();
            this.db = DriverManager.getConnection(url, this.dbProperties);
        }
        return this.db;
    }

    /**
     * Holt alle verfügbaren Standorte aus der Datenbank
     *
     * @return Alle verfügbaren Standorte
     */
    public List<Location> getLocations() throws SQLException {
        final List<Location> locations = new ArrayList<>();

        final ResultSet res = this.getConnection().prepareCall("SELECT * FROM locations ORDER BY name").executeQuery();
        while (res.next()) {
            locations.add(this.getLocation(res));
        }

        return locations;
    }

    /**
     * Holt einen Standort anhand seiner ID
     */
    public Location getLocation(int id) throws SQLException {
        if (this.locations.containsKey(id)) {
            final Location location = this.locations.get(id);
            try {
                location.update();
            } catch (final NoDataFoundException e) {
                this.locations.remove(id);
                return null;
            }
            return location;
        } else {
            Location location;
            try {
                location = new Location(this, id);
            } catch (final NoDataFoundException e) {
                return null;
            }
            this.locations.put(id, location);
            return location;
        }
    }

    /**
     * Sucht einen Ort anhand seines Namens.
     *
     * @param name Der Name des Orts.
     * @return Der Ort mit dem gegebenen Namen.
     */
    public Location getLocation(String name) throws SQLException {
        final PreparedStatement s = this.getConnection().prepareStatement("SELECT * FROM locations WHERE name=?");
        s.setString(1, name);
        final ResultSet res = s.executeQuery();
        if (res.next()) {
            return this.getLocation(res);
        } else {
            return null;
        }
    }

    /**
     * Holt einen Standort aus einem Abfrageergebnis
     */
    Location getLocation(ResultSet res) throws SQLException {
        if (this.locations.containsKey(res.getInt("id"))) {
            final Location l = this.locations.get(res.getInt("id"));
            l.update(res);
            return l;
        } else {
            final Location l = new Location(this, res);
            this.locations.put(l.getId(), l);
            return l;
        }
    }

    /**
     * Entfernt nicht verwendete Standorte aus der Datenbank
     */
    public void removeUnusedLocations() throws SQLException {
        this.getConnection().prepareCall(
                "DELETE FROM locations WHERE locations.id NOT IN (SELECT location_id FROM devices) AND locations.id<>1")
                .execute();
    }

    /**
     * Holt die Liste aller Geräte
     *
     * @return Eine Liste aller Geräte
     */
    public List<Device> getDevices() throws SQLException {
        final ResultSet res = this.getConnection().prepareCall("SELECT * FROM devices").executeQuery();
        final List<Device> result = new ArrayList<>();

        if (res.isBeforeFirst()) {
            while (res.next()) {
                result.add(this.getDevice(res));
            }
        }

        return result;
    }

    /**
     * Gibt die Geräte zurück, die vom elwaClient mit XS-Display angezeigt werden sollen.
     */
    public Device[] getDevicesToDisplayXs(Location location) throws SQLException {
        final Device[] result = new Device[4];

        final ResultSet sqlRes = this.getConnection()
                .prepareCall("SELECT id FROM devices WHERE location_id=" + location.getId() + " ORDER BY position")
                .executeQuery();

        if (sqlRes.isBeforeFirst()) {
            while (sqlRes.next()) {
                final int id = sqlRes.getInt("id");
                final Device dev = this.getDevice(id);
                for (int i = 0; i < 4; i++) {
                    if (dev.getPosition() == i + 1 && result[i] == null) {
                        result[i] = dev;
                        break;
                    }
                }
            }
        }

        return result;
    }

    /**
     * Gibt alle Geräte zurück, die vom elwaClient angezeigt werden sollen.
     *
     * @param location Der Ort, an dem der elwaClient stationiert ist.
     */
    public List<Device> getDevicesToDisplay(Location location) throws SQLException {
        final ResultSet sqlRes = this.getConnection()
                .prepareCall("SELECT id FROM devices WHERE location_id=" + location.getId() + " ORDER BY name")
                .executeQuery();

        List<Device> res = new ArrayList<>();
        if (sqlRes.isBeforeFirst()) {
            while (sqlRes.next()) {
                final int id = sqlRes.getInt("id");
                final Device dev = this.getDevice(id);
                res.add(dev);
            }
        }
        return res;
    }

    /**
     * Holt eine Liste an Geräten, auf denen das Programm p verfügbar ist
     *
     * @param p Das Programm, das auf den zurück gelieferten Geräten verfügbar sein soll
     * @return Eine Liste an Geräten, auf denen das Programm p verfügbar ist
     */
    public List<Device> getDevices(Program p) throws SQLException {
        final ResultSet res = this.getConnection().prepareCall(
                "SELECT devices.* FROM device_program_rel LEFT JOIN devices ON device_program_rel.device_id=devices" +
                        ".id WHERE program_id=" + p.getId()).executeQuery();

        final List<Device> result = new ArrayList<>();
        if (res.isBeforeFirst()) {
            while (res.next()) {
                result.add(this.getDevice(res));
            }
        }

        return result;
    }

    /**
     * Holt ein Gerät anhand seiner ID
     *
     * @param id Die ID des Geräts
     * @return Das geholte Gerät oder null, wenn kein Eintrag zur ID gefunden werden kann
     */
    public Device getDevice(int id) throws SQLException {
        if (this.devices.containsKey(id)) {
            final Device d = this.devices.get(id);
            try {
                d.update();
            } catch (final NoDataFoundException e) {
                // Das Gerät wurde aus der Datenbank gelöscht
                this.devices.remove(id);
                return null;
            }
            return d;
        } else {
            Device d;
            try {
                d = new Device(this, id);
            } catch (final NoDataFoundException e) {
                return null;
            }
            this.devices.put(d.getId(), d);
            return d;
        }
    }

    /**
     * Holt ein Gerät aus einem Abfrageergebnis
     *
     * @param res Das Abfrageergebnis
     * @return Das geholte Gerät
     */
    Device getDevice(ResultSet res) throws SQLException {
        if (this.devices.containsKey(res.getInt("id"))) {
            final Device d = this.devices.get(res.getInt("id"));
            d.update(res);
            return d;
        } else {
            final Device d = new Device(this, res);
            this.devices.put(d.getId(), d);
            return d;
        }
    }

    /**
     * Holt alle verfügbaren Programme aus der Datenbank
     */
    public List<Program> getPrograms() throws SQLException {
        final List<Program> programs = new LinkedList<>();

        final ResultSet res = this.getConnection().prepareCall("SELECT * FROM programs").executeQuery();

        while (res.next()) {
            programs.add(this.getProgram(res));
        }

        return programs;
    }

    /**
     * Holt ein Programm anhand dessen ID
     *
     * @return Das Programm
     */
    public Program getProgramById(int id) throws SQLException {
        // Prüfe, ob das Gerät schon einmal geladen wurde
        if (this.programs.containsKey(id)) {
            final Program p = this.programs.get(id);
            try {
                p.update();
            } catch (final NoDataFoundException e) {
                // Das Programm wurde aus der Datenbank gelöscht
                this.programs.remove(id);
                return null;
            }
            return p;
        } else {
            Program p;
            try {
                p = new Program(this, id);
            } catch (final NoDataFoundException e) {
                return null;
            }
            this.programs.put(p.getId(), p);
            return p;
        }
    }

    /**
     * Holt ein Programm aus einem Abfrageergebnis
     *
     * @param res Das Abfrageergebnis, aus dem das Programm geholt werden soll
     * @return Das Programm
     */
    Program getProgram(ResultSet res) throws SQLException {
        if (this.programs.containsKey(res.getInt("id"))) {
            final Program p = this.programs.get(res.getInt("id"));
            p.update(res);
            return p;
        } else {
            final Program p = new Program(this, res);
            this.programs.put(res.getInt("id"), p);
            return p;
        }
    }

    /**
     * Holt eine Liste aller Benutzergruppen aus der Datenbank.
     *
     * @return Eine Liste aller verfügbarer Benutzergruppen.
     */
    public List<UserGroup> getUserGroups() throws SQLException {
        ResultSet res = this.getConnection().prepareCall("SELECT * FROM user_groups ORDER BY name").executeQuery();
        List<UserGroup> groups = new ArrayList<>();
        if (res.isBeforeFirst()) {
            while (res.next()) {
                groups.add(this.getUserGroupById(res));
            }
        }
        return groups;
    }

    /**
     * Holt eine Benutzergruppe aus der Datenbank
     *
     * @param id Die ID der Benutzergruppe
     * @return Die Benutzergruppe
     */
    public UserGroup getUserGroupById(int id) throws SQLException {
        if (this.userGroups.containsKey(id)) {
            UserGroup g = this.userGroups.get(id);
            try {
                g.update();
            } catch (NoDataFoundException e) {
                this.userGroups.remove(id);
                return null;
            }
            return g;
        } else {
            UserGroup g;
            try {
                g = new UserGroup(this, id);
            } catch (NoDataFoundException e) {
                return null;
            }
            this.userGroups.put(id, g);
            return g;
        }
    }

    /**
     * Holt eine Benutzergruppe aus der Datenbank
     *
     * @param res Das Abfrageergebnis, aus dem die Benutzergruppe geholt werden soll.
     * @return Die Benutzergruppe
     */
    private UserGroup getUserGroupById(ResultSet res) throws SQLException {
        if (this.userGroups.containsKey(res.getInt("id"))) {
            UserGroup g = this.userGroups.get(res.getInt("id"));
            g.update(res);
            return g;
        } else {
            UserGroup g;
            g = new UserGroup(this, res);
            this.userGroups.put(res.getInt("id"), g);
            return g;
        }
    }

    /**
     * Ermittelt die Standard-Benutzergruppe
     *
     * @return Die Standard-Benutzergruppe
     */
    public UserGroup getDefaultUserGroup() throws SQLException {
        ResultSet res =
                this.getConnection().prepareCall("SELECT * FROM user_groups ORDER BY id ASC LIMIT 1").executeQuery();
        if (res.isBeforeFirst() && res.next()) {
            return new UserGroup(this, res);
        } else {
            return null;
        }
    }

    /**
     * Gibt den letzten Benutzer eines Geräts zurück
     *
     * @param d Das Gerät, dessen letzter Benutzer ermittelt werden soll
     * @return Den letzten Benutzer des Geräts oder null, wenn es keinen solchen gibt
     * @throws SQLException Wenn der letzte Benutzer nicht geladen werden kann
     */
    public User getLastUser(Device d) throws SQLException {
        final ResultSet res = this.getConnection().prepareCall(
                "SELECT users.* " + "FROM executions LEFT JOIN users ON executions.user_id=users.id " +
                        "WHERE device_id=" + d.getId() + " AND user_id>=0 AND start IS NOT NULL " +
                        "ORDER BY executions.id DESC LIMIT 1").executeQuery();
        if (res.isBeforeFirst() && res.next()) {
            return this.getUser(res);
        } else {
            return null;
        }
    }

    /**
     * Sucht den zur Id passenden Benutzer
     *
     * @param id Die Id des Nutzers
     * @return Den gefundenen Benutzer oder null, wenn kein Eintrag zur Id gefunden werden kann
     * @throws SQLException Wenn die Abfrage nicht ausgeführt werden kann
     */
    public User getUserById(int id) throws SQLException {
        if (this.users.containsKey(id)) {
            final User u = this.users.get(id);
            try {
                u.update();
            } catch (final NoDataFoundException e) {
                // Benutzer wurde aus der Datenbank gelöscht
                this.users.remove(id);
                return null;
            }
            return u;
        } else {
            // Benutzer aus Datenbank laden
            final ResultSet res = this.getConnection().prepareCall("SELECT * FROM users WHERE id=" + id).executeQuery();
            if (res.isBeforeFirst() && res.next()) {
                final User u = new User(this, res, this.getUserGroupById(res.getInt("group_id")));
                this.users.put(u.getId(), u);
                return u;
            } else {
                return null;
            }
        }
    }

    /**
     * Holt alle verfügbaren Benutzer aus der Datenbank
     *
     * @return Eine Liste aller Benutzer in der Datenbank
     * @throws SQLException Wenn die Abfrage nicht ausgeführt werden kann
     */
    public List<User> getUsers() throws SQLException {
        final ResultSet res =
                this.getConnection().prepareCall("SELECT * FROM users WHERE deleted=FALSE").executeQuery();
        final Vector<User> users = new Vector<>();
        while (res.next()) {
            users.add(new User(this, res, this.getUserGroupById(res.getInt("group_id"))));
        }
        return users;
    }

    /**
     * Lädt einen Benutzer anhand eines Abfrageergebnisses
     *
     * @param res Das Abfrageergebnis aus dem der Benutzer zu erstellen ist
     * @return Den Benutzer
     * @throws SQLException Wenn beim Laden der Daten ein Fehler auftritt
     */
    private User getUser(ResultSet res) throws SQLException {
        if (this.users.containsKey(res.getInt("id"))) {
            final User u = this.users.get(res.getInt("id"));
            u.update(res);
            return u;
        } else {
            final User u = new User(this, res, this.getUserGroupById(res.getInt("group_id")));
            this.users.put(u.getId(), u);
            return u;
        }
    }

    /**
     * Sucht den zur Kartennummer passenden Benutzer
     *
     * @param cardId Die Kartennummer des Nutzers
     * @return Den gefundenen Benutzer oder null, wenn kein Eintrag zur Id gefunden werden kann
     */
    public User getUserByCardId(String cardId) throws SQLException {
        final ResultSet res = this.getConnection()
                .prepareCall("SELECT * FROM users WHERE deleted=false AND card_ids ~ '(?n)^" + cardId + "$' LIMIT 1")
                .executeQuery();
        if (res.isBeforeFirst() && res.next()) {
            return this.getUser(res);
        } else {
            return null;
        }
    }

    /**
     * Sucht den zur Email-Adresse passenden Benutzer
     *
     * @param email Die Email-Adresse des gesuchten Benutzers
     * @return Den Benutzer mit der gegebenen Email-Adresse oder null, wenn es keinen solchen gibt.
     */
    public User getUserByEmail(String email) throws SQLException {
        final PreparedStatement s =
                this.getConnection().prepareStatement("SELECT * FROM users WHERE deleted=FALSE AND email=? LIMIT 1");
        s.setString(1, email);
        final ResultSet res = s.executeQuery();
        if (res.isBeforeFirst() && res.next()) {
            return this.getUser(res);
        } else {
            return null;
        }
    }

    /**
     * Sucht den zum Schlüssel fürs Zurücksetzen eines Passworts passenden
     * Benutzer.
     *
     * @param key Der Schlüssel zum Zurücksetzen des Passworts eines Benutzers.
     * @return Den gefundenen Benutzer, oder null, wenn der Schlüssel unbekannt oder abgelaufen ist.
     */
    public User getUserByPasswordResetKey(String key) throws SQLException {
        final PreparedStatement s = this.getConnection()
                .prepareStatement("SELECT * FROM users WHERE deleted=FALSE AND " + "password_reset_key=?");
        s.setString(1, key);
        final ResultSet res = s.executeQuery();
        if (res.isBeforeFirst() && res.next()) {
            final User u = this.getUser(res);
            if (u.passwordResetKeyIsValid()) {
                return u;
            }
        }
        return null;
    }

    /**
     * Sucht alle nicht abgeschlossenen Ausführungen zu einem Benutzer
     *
     * @param u Der Benutzer
     * @return Alle Ausführungen, die nicht abgeschlossen sind
     * @throws SQLException Falls ein Fehler bei der Datenbankabfrage auftritt
     */
    public List<Execution> getNotFinishedExecutions(User u) throws SQLException {
        final List<Execution> executions = new LinkedList<>();
        final ResultSet res = this.getConnection().prepareCall(
                "SELECT * FROM executions WHERE user_id=" + u.getId() + " AND finished=false AND start IS NOT NULL")
                .executeQuery();
        while (res.next()) {
            executions.add(this.getExecution(res));
        }
        return executions;
    }

    /**
     * Holt eine Ausführung aus einem Abfrageergebnis
     *
     * @param res Das Abfrageergebnis, aus dem die Ausführung geholt werden soll
     * @return Die Ausführung
     */
    private Execution getExecution(ResultSet res) throws SQLException {
        if (this.executions.containsKey(res.getInt("id"))) {
            final Execution e = this.executions.get(res.getInt("id"));
            e.update(res);
            return e;
        } else {
            final Execution e = new Execution(this, res, this.getDevice(res.getInt("device_id")),
                    this.getProgramById(res.getInt("program_id")), this.getUserById(res.getInt("user_id")));
            this.executions.put(e.getId(), e);
            return e;
        }
    }

    public Execution getExecution(int id) throws SQLException {
        if (this.executions.containsKey(id)) {
            final Execution e = this.executions.get(id);
            e.update();
            return e;
        } else {
            final ResultSet res =
                    this.getConnection().prepareCall("SELECT * FROM executions WHERE id=" + id).executeQuery();
            res.next();
            return this.getExecution(res);
        }
    }

    /**
     * Holt die auf dem gegebenen Gerät laufende Ausführung aus der Datenbank
     *
     * @param device Das Gerät, dessen laufende Ausführung gesucht ist.
     * @return Die derzeit laufende Ausführung auf dem Gerät.
     */
    public Execution getRunningExecution(Device device) throws SQLException {
        final PreparedStatement s = this.getConnection()
                .prepareStatement("SELECT * FROM executions WHERE device_id=? AND finished=? AND start IS NOT NULL");
        s.setInt(1, device.getId());
        s.setBoolean(2, false);
        final ResultSet res = s.executeQuery();

        while (res.next()) {
            final Execution e = this.getExecution(res);
            if (!e.isExpired()) {
                return e;
            }
        }
        return null;
    }

    /**
     * Holt alle registrierten Ausführungen auf einem Gerät aus der Datenbank.
     *
     * @param device Das Gerät, wessen Ausführungen gesucht sind.
     * @return Die Ausführungen auf dem Gerät.
     */
    public List<Execution> getExecutions(Device device) throws SQLException {
        final PreparedStatement s = this.getConnection().prepareStatement(
                "SELECT * FROM executions WHERE device_id=? AND start IS NOT NULL ORDER BY start DESC");
        s.setInt(1, device.getId());
        final ResultSet res = s.executeQuery();

        final List<Execution> executions = new Vector<>();

        while (res.next()) {
            executions.add(this.getExecution(res));
        }

        return executions;
    }

    /**
     * Erstellt eine neue Programmausführung
     *
     * @param user    Der ausführende Benutzer
     * @param program Das ausgeführte Programm
     * @param device  Das Gerät, auf dem die Ausführung läuft
     * @return Die erstellte Ausführung
     * @throws SQLException Wenn die Ausführung nicht erstellt werden kann
     */
    public Execution newExecution(User user, Program program, Device device) throws SQLException {
        final Execution e = new Execution(this, device, program, user);
        this.executions.put(e.getId(), e);
        return e;
    }

    /**
     * Holt die Guthabensbuchungen eines Benutzers aus der Datenbank
     *
     * @param user Der Benutzer, zu dem die Buchungen geladen werden sollen
     * @return Die Buchungen eines Benutzers
     */
    public List<CreditAccountingEntry> getAccountingEntries(User user) throws SQLException {
        final ResultSet res = this.getConnection()
                .prepareCall("SELECT * FROM credit_accounting WHERE user_id=" + user.getId() + " ORDER BY date DESC")
                .executeQuery();
        final List<CreditAccountingEntry> entries;
        entries = new LinkedList<>();
        while (res.next()) {
            entries.add(new CreditAccountingEntry(this, res, user));
        }
        return entries;
    }

    /**
     * Holt die letzte Einzahlung des Benutzers aus der Datenbank
     *
     * @param user Der Benutzer, dessen letzte Einzahlung geladen werden soll
     * @return Die letzte Einzahlung des Benutzers
     */
    public CreditAccountingEntry getLastInpayment(User user) throws SQLException {
        final ResultSet res = this.getConnection().prepareCall(
                "SELECT * FROM credit_accounting WHERE user_id=" + user.getId() +
                        " AND amount>0 ORDER BY DATE DESC LIMIT 1").executeQuery();
        if (res.next()) {
            return new CreditAccountingEntry(this, res, user);
        } else {
            return null;
        }
    }
}
