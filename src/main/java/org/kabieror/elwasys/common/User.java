package org.kabieror.elwasys.common;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Diese Klasse repräsentiert einen Benutzer des Systems.
 *
 * @author Oliver Kabierschke
 */
public class User {

    private static final String EMAIL_NOTIFICATION_KEY = "email_notification";
    private static final String PASSWORD_KEY = "password";
    private static final String DELETED_KEY = "deleted";
    private static final String IS_ADMIN_KEY = "is_admin";
    private static final String BLOCKED_KEY = "blocked";
    private static final String CARD_IDS_KEY = "card_ids";
    private static final String EMAIL_KEY = "email";
    private static final String USERNAME_KEY = "username";
    private static final String NAME_KEY = "name";
    private static final String PUSHOVER_USER_KEY_KEY = "pushover_user_key";
    private static final String AUTH_KEY_KEY = "auth_key";
    private static final String PUSH_ENABLED_KEY = "push_notification";
    private static final String PUSH_IONIC_ID_KEY = "app_id";
    private static final String PASSWORD_RESET_KEY_KEY = "password_reset_key";
    private static final String PASSWORD_RESET_TIMEOUT_KEY = "password_reset_timeout";
    private static final String CREDIT_KEY = "credit";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * Der Daten-Verwalter, der die Verbindung zur Datenbank bereit stellt
     */
    private final DataManager dataManager;
    /**
     * Die ID des Benutzers
     */
    private final int id;
    private LocalDateTime lastUpdateTime;
    /**
     * Der Name des Benutzers
     */
    private String name;

    /**
     * Die Email des Benutzers
     */
    private String email;

    /**
     * Der Benutzername
     */
    private String username;

    /**
     * Die Gruppe des Benutzers
     */
    private UserGroup group;

    /**
     * Das Guthaben des Benutzers in Cent
     */
    private BigDecimal credit;

    /**
     * Die ID der Karte vom Benutzer
     */
    private String[] cardIds;

    /**
     * Der Authentifizierungs-Code für die elwaApp
     */
    private String authKey;

    /**
     * Die Benutzer-ID des Ionic-Benutzers, der Push-Benachrichtigungen empfangen soll
     */
    private String pushIonicId;

    /**
     * Gibt an, ob der Benutzer Push-Benachrichtigungen empfangen möchte.
     */
    private boolean pushEnabled = true;

    /**
     * Gibt an, ob der Benutzer blockiert ist
     */
    private boolean blocked;

    /**
     * Gibt an, ob der Benutzer Administrator-Rechte besitzt.
     */
    private boolean isAdmin;

    /**
     * Gibt an ob der Benutzer als gelöscht markiert ist
     */
    private boolean deleted;

    /**
     * Gibt an, ob der Benutzer per Email über fertiggestellte Waschvorgänge
     * benachrichtigt werden möchte.
     */
    private boolean emailNotification;

    /**
     * Das Passwort des Benutzers als SHA1-Hash
     */
    private String password;

    /**
     * Der Benutzer-Key bei Pushover.
     */
    private String pushoverUserKey;

    /**
     * Der Schlüssel, welcher dem Benutzer per Email zugesandt worden ist, um
     * das Passwort zurück zu setzen.
     */
    private String passwordResetKey;

    /**
     * Das Datum, an dem der Schlüssel zum Zurücksetzen des Passworts abläuft.
     */
    private LocalDateTime passwordResetTimeout;

    /**
     * Erstellt einen neuen Benutzer
     *
     * @param res         ResultSet, aus dem der Benutzer befüllt werden soll. Es wird davon ausgegangen, dass next()
     *                    bereits aufgerufen wurde
     * @param dataManager Die Datenbankverbindung für spätere Aktualisierungen
     * @throws SQLException
     */
    public User(DataManager dataManager, ResultSet res, UserGroup group) throws SQLException {
        this.dataManager = dataManager;
        this.id = res.getInt("id");
        this.group = group;
        this.load(res);
    }

    /**
     * Erstellt einen neuen Benutzer in der Datenbank
     *
     * @param dataManager Der Datenmanager
     * @param name        Der Name des neuen Benutzers
     * @param email       Die Email-Adresse des neuen Benutzers
     * @param cardIds     Die Kartennummern des neuen Benutzers
     * @param blocked     Ob der neue Benutzer geblockt sein soll
     * @throws SQLException Falls beim Erstellen des Benutzers ein Fehler auftritt
     */
    public User(DataManager dataManager, String name, String username, String email, String[] cardIds, boolean blocked,
                boolean isAdmin, boolean emailNotification, UserGroup group) throws SQLException {
        this.dataManager = dataManager;
        this.name = name;
        this.username = username.toLowerCase();
        this.group = group;
        this.email = email;
        this.blocked = blocked;
        this.cardIds = cardIds;
        this.isAdmin = isAdmin;
        this.emailNotification = emailNotification;
        this.pushoverUserKey = "";
        this.authKey = "";
        this.pushIonicId = "";
        this.pushEnabled = true;

        final PreparedStatement s = dataManager.getConnection().prepareStatement(
                "INSERT INTO users (name, username, email, card_ids, blocked, is_admin, email_notification, group_id, push_notification, app_id)" +
                        " VALUES " +
                        "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
        int i = 1;
        s.setString(i++, name);
        s.setString(i++, username.toLowerCase());
        s.setString(i++, email);
        s.setString(i++, StringUtils.join(cardIds, "\n"));
        s.setBoolean(i++, blocked);
        s.setBoolean(i++, isAdmin);
        s.setBoolean(i++, emailNotification);
        s.setInt(i++, group.getId());
        // Halte Push-Einstellung nur lokal
        s.setBoolean(i++, true);
        s.setString(i++, this.pushIonicId);
        s.executeUpdate();

        final ResultSet res = s.getGeneratedKeys();
        if (res.next()) {
            this.id = res.getInt(1);
            this.authKey = res.getString(AUTH_KEY_KEY);
        } else {
            throw new SQLException("No ID received by database.");
        }

        this.loadCredit();
    }

    /**
     * Erstellt einen virtuellen Benutzer ohne Anbindung zur Datenbank
     *
     * @param name Der Name des virtuellen Benutzers
     */
    private User(String name) {
        this.dataManager = null;
        this.id = -1;
        this.name = name;
        this.credit = BigDecimal.ZERO;
        this.isAdmin = false;
        this.emailNotification = false;
        this.pushEnabled = false;
        this.group = new UserGroup("Offline Gruppe", DiscountType.None, 1f);
        this.authKey = "";
        this.pushEnabled = false;
    }

    /**
     * Erstellt einen anonymen Benutzer
     *
     * @return Den Benutzer
     */
    public static User getAnonymous() {
        return new User("-");
    }

    /**
     * Gibt einen Testbenutzer zurück
     *
     * @param name Der Name des Benutzers
     * @return Den Testbenutzer
     */
    public static User getTestUser(String name) {
        return new User(name);
    }

    /**
     * Aktualisiert die Daten des Benutzers mit denen aus der Datenbank
     *
     * @throws SQLException
     * @throws NoDataFoundException
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
     * Aktualisiert die Daten des Benutzers mit denen aus einem Abfrageergebnis
     *
     * @param res Das Abfrageergebnis, mit dem der Benutzer aktualisiert werden soll
     * @throws SQLException
     */
    public void update(ResultSet res) throws SQLException {
        this.load(res);
        try {
            this.group.update();
        } catch (NoDataFoundException e) {
            this.logger.warn(String
                    .format("The group '%1s' (%2d) of user '%3s' (%4d) does not exist any more. Working with old data.",
                            this.group.getName(), this.group.getId(), this.name, this.id));
        }
    }

    /**
     * Verändert die Eigenschaften eines Benutzers
     *
     * @param name    Der neue Name des Benutzers
     * @param email   Die neue Email des Benutzers
     * @param cardIds Die neuen Kartennummern des Benutzers
     * @param blocked Ob des Benutzer geblockt sein soll
     * @throws SQLException
     */
    public void modify(String name, String username, String email, String[] cardIds, boolean blocked, boolean isAdmin,
                       boolean emailNotification, UserGroup userGroup, boolean pushEnabled) throws SQLException {
        final PreparedStatement s = this.dataManager.getConnection().prepareStatement(
                "UPDATE users SET name=?, username=?, email=?, card_ids=?, blocked=?, is_admin=?, " +
                        "email_notification=?, group_id=?, push_notification=? WHERE id=?");
        int i = 1;
        s.setString(i++, name);
        s.setString(i++, username.toLowerCase());
        s.setString(i++, email);
        s.setString(i++, StringUtils.join(cardIds, "\n"));
        s.setBoolean(i++, blocked);
        s.setBoolean(i++, isAdmin);
        s.setBoolean(i++, emailNotification);
        s.setInt(i++, userGroup.getId());
        s.setBoolean(i++, pushEnabled);
        s.setInt(i++, this.id);
        s.execute();

        this.name = name;
        this.username = username.toLowerCase();
        this.email = email;
        this.cardIds = cardIds;
        this.blocked = blocked;
        this.isAdmin = isAdmin;
        this.emailNotification = emailNotification;
        this.pushEnabled = pushEnabled;
        this.group = userGroup;
    }

    private void setPasswordResetKey(String key) throws SQLException {
        final PreparedStatement s =
                this.dataManager.getConnection().prepareStatement("UPDATE users SET password_reset_key=? WHERE id=?");
        s.setString(1, key);
        s.setInt(2, this.id);
        s.execute();

        this.passwordResetKey = key;
    }

    private void setPasswordResetTimeout(LocalDateTime timeout) throws SQLException {
        final PreparedStatement s = this.dataManager.getConnection()
                .prepareStatement("UPDATE users SET password_reset_timeout=? WHERE id=?");
        s.setTimestamp(1, Timestamp.valueOf(timeout));
        s.setInt(2, this.id);
        s.execute();

        this.passwordResetTimeout = timeout;
    }

    /**
     * Aktualisiert das Datum des letzten Logins auf das gegenwärtige.
     *
     * @throws SQLException
     */
    public void updateLastLogin() throws SQLException {
        this.dataManager.getConnection().prepareCall("UPDATE users SET last_login=now() WHERE id=" + this.id).execute();
    }

    /**
     * Befüllt die Felder dieses Objekts mit Werten aus der Datenbank
     *
     * @throws NoDataFoundException Wenn der zugehörige Datensatz nicht in der Datenbank zu finden ist
     * @throws SQLException         Wenn beim Laden der Daten ein Fehler auftritt
     */
    private void load() throws NoDataFoundException, SQLException {
        final ResultSet res =
                this.dataManager.getConnection().prepareCall("SELECT * FROM users WHERE id=" + this.id).executeQuery();
        if (res.isBeforeFirst() && res.next()) {
            this.load(res);
        } else {
            throw new NoDataFoundException(
                    String.format("Der Benutzer '%1s' wurde aus der Datenbank gelöscht.", this.name));
        }
    }

    /**
     * Befüllt die Felder dieses Objekts mit Werten aus einem Abfrageergebnis
     *
     * @param res Das Abfrageergebnis aus dem die Werte des Benutzers zu entnehmen sind
     * @throws SQLException Wenn beim Laden der Daten ein Fehler auftritt
     */
    private void load(ResultSet res) throws SQLException {
        this.name = res.getString(NAME_KEY);
        this.username = res.getString(USERNAME_KEY);
        this.email = res.getString(EMAIL_KEY);
        this.cardIds = res.getString(CARD_IDS_KEY).split("\n");
        this.blocked = res.getBoolean(BLOCKED_KEY);
        this.isAdmin = res.getBoolean(IS_ADMIN_KEY);
        this.deleted = res.getBoolean(DELETED_KEY);
        this.password = res.getString(PASSWORD_KEY);
        this.emailNotification = res.getBoolean(EMAIL_NOTIFICATION_KEY);
        this.pushoverUserKey = res.getString(PUSHOVER_USER_KEY_KEY);
        this.authKey = res.getString(AUTH_KEY_KEY);
        // Halte Push-Einstellung nur lokal, da kein Schreibzugriff auf Datenbank im elwaClient
        // this.pushEnabled = res.getBoolean(PUSH_ENABLED_KEY);
        this.pushIonicId = res.getString(PUSH_IONIC_ID_KEY);

        this.passwordResetKey = res.getString(PASSWORD_RESET_KEY_KEY);
        this.passwordResetTimeout = null;
        final Timestamp ts = res.getTimestamp(PASSWORD_RESET_TIMEOUT_KEY);
        if (ts != null) {
            this.passwordResetTimeout = ts.toLocalDateTime();
        }
        this.loadCredit();

        this.group = this.dataManager.getUserGroupById(res.getInt("group_id"));
    }

    /**
     * Lädt das Guthaben des Benutzers aus der Datenbank
     *
     * @throws SQLException
     */
    private synchronized void loadCredit() throws SQLException {
        this.credit = null;

        ResultSet res = this.dataManager.getConnection()
                .prepareCall("SELECT SUM(amount) AS credit FROM credit_accounting WHERE user_id=" + this.id)
                .executeQuery();
        if (res.isBeforeFirst() && res.next()) {
            this.credit = res.getBigDecimal(CREDIT_KEY);
        }

        if (this.credit == null) {
            this.credit = new BigDecimal("0.00");
        }

        // Kosten laufender Programme vom Guthaben abziehen
        res = this.dataManager.getConnection()
                .prepareCall("SELECT program_id FROM executions WHERE finished=FALSE AND user_id=" + this.id)
                .executeQuery();
        while (res.next()) {
            final Program prog = this.dataManager.getProgramById(res.getInt("program_id"));
            if (prog == null) {
                this.logger.error("Invalid entry in the database: Execution #" + res.getInt("id")
                    + " has no program set.");
                continue;
            }
            this.credit = this.credit.subtract(prog.getPrice(prog.getMaxDuration(), this));
        }
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getUsername() {
        return this.username;
    }

    public UserGroup getGroup() {
        return group;
    }

    public String getEmail() {
        return this.email;
    }

    public BigDecimal getCredit() {
        return this.credit;
    }

    /**
     * Gibt den Authentifizierungs-Code des Benutzers zurück, mit dem dieser sich bei der elwaApp registriert kann.
     *
     * @return Der Authentifizierungs-Code
     */
    public String getAuthKey() {
        return this.authKey;
    }

    public String getPushIonicId() {
        return this.pushIonicId;
    }

    public String[] getCardIds() {
        return this.cardIds;
    }

    public boolean isBlocked() {
        return this.blocked;
    }

    public boolean isAdmin() {
        return this.isAdmin;
    }

    public boolean isDeleted() {
        return this.deleted;
    }

    /**
     * Setzt das Gelöscht-Flag im Benutzer
     *
     * @throws SQLException
     */
    public void setDeleted(boolean d) throws SQLException {
        final PreparedStatement s =
                this.dataManager.getConnection().prepareStatement("UPDATE users SET deleted=?, username=? WHERE id=?");
        s.setBoolean(1, d);
        String newUserName;
        if (d) {
            newUserName = "#del" + this.id + "#" + this.username;
        } else {
            newUserName = this.username.replaceFirst("^#del" + this.id + "#", "");
        }
        s.setString(2, newUserName);
        s.setInt(3, this.id);
        s.execute();

        this.deleted = d;
        this.username = newUserName;
    }

    public boolean getEmailNotification() {
        return this.emailNotification;
    }

    /**
     * Setzt die Email-Benachrichrichtigung für den kommenden Waschgang.
     */
    public void setEmailNotification(boolean emailNotification) {
        this.emailNotification = emailNotification;
    }

    public String getPushoverUserKey() {
        return this.pushoverUserKey;
    }

    public void setPushoverUserKey(String key) throws SQLException {
        final PreparedStatement s =
                this.dataManager.getConnection().prepareStatement("UPDATE users SET pushover_user_key=? WHERE id=?");
        s.setString(1, key);
        s.setInt(2, this.id);
        s.execute();

        this.pushoverUserKey = key;
    }

    /**
     * Gibt an, ob der Benutzer Push-Benachrichtigungen erhalten möchte
     */
    public boolean isPushEnabled() {
        return this.pushEnabled;
    }

    public void setPushEnabled(boolean pushEnabled) throws SQLException {
        // Erfordert Schreibrechte für Client in Datenbank.
        // Daher Speicherung nur im Arbeitsspeicher
//        final PreparedStatement s =
//                this.dataManager.getConnection().prepareStatement("UPDATE users SET push_notification=? WHERE id=?");
//        s.setBoolean(1, pushEnabled);
//        s.setInt(2, this.id);
//        s.execute();

        this.pushEnabled = pushEnabled;
    }

    /**
     * Setzt die Anbindung zur elwaApp zurück, sodass sich der Benutzer neu registrieren kann.
     */
    public void resetAppConnection() throws SQLException {
        final PreparedStatement s =
                this.dataManager.getConnection()
                        .prepareStatement("UPDATE users SET auth_key=generate_user_authkey() WHERE id=?",
                                Statement.RETURN_GENERATED_KEYS);
        s.setInt(1, this.id);
        s.execute();

        ResultSet res = s.getGeneratedKeys();
        this.authKey = res.getString(AUTH_KEY_KEY);
    }

    /**
     * Generiert einen Schlüssel zum Zurücksetzen des Passworts des Benutzers.
     *
     * @return Den Schlüssel, der dem Benutzer per Email zugesandt werden soll.
     * @throws NoSuchAlgorithmException
     * @throws SQLException
     */
    public String generatePasswordResetKey() throws NoSuchAlgorithmException, SQLException {
        final byte[] keyArray = new byte[30];
        for (int i = 1; i < 30; i++) {
            keyArray[i] = (byte) (Math.random() * 255);
        }
        final String key = Utilities.sha1(new String(keyArray));

        this.setPasswordResetKey(key);
        this.setPasswordResetTimeout(LocalDateTime.now().plus(2, ChronoUnit.HOURS));

        return key;
    }

    /**
     * Prüft, ob der Schlüssel zum Zurücksetzen des Passworts abgelaufen ist.
     *
     * @return True, wenn der Schlüssel noch gültig ist, sonst false.
     */
    public boolean passwordResetKeyIsValid() {
        return this.passwordResetTimeout != null &&
                !Duration.between(LocalDateTime.now(), this.passwordResetTimeout).isNegative();
    }

    /**
     * Prüft, ob der Benutzer sich einen Preis leisten kann
     *
     * @param price Der Preis
     * @return Ob der Benutzer sich den Preis leisten kann
     */
    public boolean canAfford(BigDecimal price) {
        return this.credit.compareTo(price) >= 0;
    }

    /**
     * Prüft ein Passwort auf Korrektheit.
     *
     * @param password Das zu prüfende Passwort.
     * @return True, wenn das Passwort mit dem gespeicherten übereinstimmt.
     */
    public boolean checkPassword(String password) {
        try {
            return this.password.equals(Utilities.sha1(password));
        } catch (final NoSuchAlgorithmException e) {
            this.logger.error("Could not create a hash of the given password.", e);
            return false;
        }
    }

    /**
     * Ändert das Passwort des Benutzers.
     *
     * @param value Das neue Passwort.
     * @throws SQLException
     * @throws NoSuchAlgorithmException
     */
    public void changePassword(String value) throws NoSuchAlgorithmException, SQLException {
        PreparedStatement s =
                this.dataManager.getConnection().prepareStatement("UPDATE users SET password=? WHERE id=?");
        final String newPwHash = Utilities.sha1(value);
        s.setString(1, newPwHash);
        s.setInt(2, this.id);
        s.execute();
        this.password = newPwHash;

        // Password-Reset-Key konsumieren
        if (this.passwordResetKey != null) {
            this.passwordResetKey = null;
            s = this.dataManager.getConnection().prepareStatement("UPDATE users SET password_reset_key=? WHERE ID=?");
            s.setString(1, null);
            s.setInt(2, this.id);
            s.execute();
        }
    }

    /**
     * Bezahlt eine Ausführung
     *
     * @param e Die zu bezahlende Ausführung
     * @throws SQLException
     */
    public void payExecution(Execution e) throws SQLException {
        if (this.id >= 0) {
            if (e.getPrice().equals(BigDecimal.ZERO)) {
                // A free execution has not to be payed.
                return;
            }
            final PreparedStatement s = this.dataManager.getConnection().prepareStatement(
                    "INSERT INTO credit_accounting (user_id, execution_id, amount, description) VALUES (?, ?, ?, ?)");
            s.setInt(1, this.id);
            s.setInt(2, e.getId());
            s.setBigDecimal(3, e.getPrice().negate());
            s.setString(4, e.getProgram().getName() + " auf " + e.getDevice().getName() + " (" +
                    e.getDevice().getLocation().getName() + ") bezahlt von " + this.name + ".");
            s.execute();

            this.loadCredit();
        }
    }

    /**
     * Einzahlung auf das Konto des Benutzers
     *
     * @param amount Der einzuzahlende Betrag
     * @throws SQLException
     */
    public void inpayment(BigDecimal amount) throws SQLException {
        this.inpayment(amount, "Inpayment from Washportal");
    }

    public void inpayment(BigDecimal amount, String text) throws SQLException {
        final PreparedStatement s = this.dataManager.getConnection()
                .prepareStatement("INSERT INTO credit_accounting (user_id, amount, description) VALUES (?, ?, ?)");
        s.setInt(1, this.id);
        s.setBigDecimal(2, amount);
        s.setString(3, text);
        s.execute();
        this.loadCredit();
    }

    /**
     * Auszahlung vom Konto des Benutzers
     *
     * @param amount Der auszuzahlende Betrag
     * @throws SQLException
     * @throws NotEnoughCreditException
     */
    public void payout(BigDecimal amount) throws SQLException, NotEnoughCreditException {
        this.payout(amount, "Payout from Washportal");
    }

    public void payout(BigDecimal amount, String text) throws SQLException, NotEnoughCreditException {
        if (this.credit.compareTo(amount) < 0) {
            // Guthaben reicht zum Auszahlen nicht aus.
            throw new NotEnoughCreditException();
        }
        final PreparedStatement s = this.dataManager.getConnection()
                .prepareStatement("INSERT INTO credit_accounting (user_id, amount, description) VALUES (?, ?, ?)");
        s.setInt(1, this.id);
        s.setBigDecimal(2, amount.negate());
        s.setString(3, text);
        s.execute();
        this.loadCredit();
    }

    /**
     * Gibt zurück, ob der Benutzer Ausführungsaufträge aufgegeben hat, die
     * abgelaufen sind, jedoch nicht verbucht wurden.
     *
     * @return Wahr, wenn es abgelaufene Ausführungsaufträge gibt
     * @throws SQLException Wenn ein Fehler bei der Datenbankabfrage auftritt
     */
    public boolean hasExpiredExecutions() throws SQLException {
        final List<Execution> executions = this.dataManager.getNotFinishedExecutions(this);
        for (final Execution e : executions) {
            if (e.isExpired()) {
                return true;
            }
        }
        return false;
    }

}
