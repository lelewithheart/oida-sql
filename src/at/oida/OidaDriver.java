package at.oida;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * JDBC-Treiber für OIDA-SQL.
 *
 * Verbindet sich über den normalen MySQL-Treiber (Connector/J) und übersetzt
 * jeden Befehl vorher von OIDA-SQL nach MySQL.
 *
 * URLs: jdbc:oida://host:3306/datenbank   oder ganz normal   jdbc:mysql://host:3306/datenbank
 */
public final class OidaDriver implements Driver {

    private static final String OIDA_URL = "jdbc:oida:";
    private static final String MYSQL_URL = "jdbc:mysql:";
    private static final String MYSQL_TREIBER = "com.mysql.cj.jdbc.Driver";

    static {
        try {
            DriverManager.registerDriver(new OidaDriver());
        } catch (SQLException e) {
            // Dann ist der Treiber nur direkt über die Klasse verwendbar - für DataGrip reicht das.
        }
    }

    private volatile Driver mysql;

    private Driver mysql() throws SQLException {
        Driver d = mysql;
        if (d == null) {
            try {
                d = (Driver) Class.forName(MYSQL_TREIBER, true, OidaDriver.class.getClassLoader())
                        .getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new SQLException("OIDA-SQL braucht den MySQL-Treiber (Connector/J). "
                        + "In DataGrip: den MySQL-Treiber duplizieren und oida-sql.jar dort dazugeben "
                        + "(Anleitung: https://lelewithheart.github.io/oida-sql/).", e);
            }
            mysql = d;
        }
        return d;
    }

    private static String mysqlUrl(String url) {
        return url.startsWith(OIDA_URL) ? MYSQL_URL + url.substring(OIDA_URL.length()) : url;
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && (url.startsWith(OIDA_URL) || url.startsWith(MYSQL_URL));
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        Properties eigenschaften = new Properties();
        if (info != null) {
            for (String name : info.stringPropertyNames()) {
                eigenschaften.setProperty(name, info.getProperty(name));
            }
        }
        Object log = eigenschaften.remove("oidaLog");
        Protokoll.einschalten(log != null ? log.toString() : System.getProperty("oida.log"));
        // Mehrere Befehle auf einmal (am Endwort getrennt) gehen als "a; b; c" zu MySQL.
        // Das erlaubt MySQL nur mit dieser Einstellung. Wer sie selbst setzt, behält seinen Wert.
        if (eigenschaften.getProperty("allowMultiQueries") == null && !url.contains("allowMultiQueries")) {
            eigenschaften.setProperty("allowMultiQueries", "true");
        }

        Uebersetzer uebersetzer;
        try {
            uebersetzer = Uebersetzer.ausJar();
        } catch (RuntimeException e) {
            throw new SQLException(e.getMessage(), e);
        }
        Connection echt = mysql().connect(mysqlUrl(url), eigenschaften);
        return echt == null ? null : Huelle.um(echt, uebersetzer, null);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return new DriverPropertyInfo[0];
        }
        return mysql().getPropertyInfo(mysqlUrl(url), info);
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
