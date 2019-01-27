package org.kabieror.elwasys.common;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

/**
 * Diese Klasse stellt häufig gebrauchte Funktionalitäten zur Verfügung.
 *
 * @author Oliver Kabierschke
 *
 */
public class Utilities {

    public static final String APP_VERSION = "0.3.7-SNAPSHOT";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ConfigurationManager config;

    public Utilities(ConfigurationManager config) {
        this.config = config;
    }

    /**
     * Erzeugt einen SHA-1-Hash einer gegebenen Zeichenkette.
     *
     * @param s
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String sha1(String s) throws NoSuchAlgorithmException {
        MessageDigest md = null;
        md = MessageDigest.getInstance("SHA-1");
        final byte[] b = md.digest(s.getBytes());
        String result = "";
        for (int i = 0; i < b.length; i++) {
            result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
        }
        return result;
    }

    /**
     * Generiert ein zufälliges Passwort.
     *
     * @return Ein zufälliges Passwort.
     */
    public static String generatePassword() {
        final char[] chars = ("abcdefghijklmnopqrstuvwxyz" + "ABCDEFGEHIJKLMNOPQRSTUVWXYZ"
                + "0123456789" + "-_!?=()#").toCharArray();
        return RandomStringUtils.random(12, chars);
    }

    /**
     * Generiert eine zufällige UID.
     *
     * @return Eine zufällige UID.
     */
    public static String generateUid() {
        return RandomStringUtils.randomAlphanumeric(20);
    }

    public static String getCurrentLogFile() {
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (final ch.qos.logback.classic.Logger logger : context.getLoggerList()) {
            for (final Iterator<Appender<ILoggingEvent>> index =
                    logger.iteratorForAppenders(); index.hasNext();) {
                final Appender<ILoggingEvent> appender = index.next();
                if (appender instanceof FileAppender<?>) {
                    final FileAppender<ILoggingEvent> fileAppender =
                            (FileAppender<ILoggingEvent>) appender;
                    return fileAppender.getFile();
                }
            }
        }
        return null;
    }

    /**
     * Sendet eine einfache Email an einen Benutzer.
     *
     * @param subject Betreff
     * @param content Inhalt
     * @param to      Empfänger
     * @throws EmailException
     */
    public void sendEmail(String subject, String content, User to) throws EmailException {
        this.logger.debug("Sending mail to " + to.getEmail() + ", using " + this.config.getSmtpServer() + ":" +
                this.config.getSmtpPort() + ", with user " + this.config.getSmtpUser());
        final SimpleEmail mail = new SimpleEmail();
        mail.addTo(to.getEmail());
        mail.setSubject(subject);
        mail.setMsg(content);

        mail.setHostName(this.config.getSmtpServer());
        mail.setSmtpPort(this.config.getSmtpPort());
        mail.setSSLOnConnect(this.config.getSmtpUseSsl());
        mail.setAuthentication(this.config.getSmtpUser(), this.config.getSmtpPassword());
        mail.setFrom(this.config.getSmtpSenderAddress());

        mail.send();
    }
}
