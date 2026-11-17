package at.oida;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Hülle um eine echte MySQL-Verbindung (oder ein Statement davon).
 * Reicht alles unverändert weiter, nur SQL-Texte werden vorher übersetzt.
 */
final class Huelle implements InvocationHandler {

    /** Methoden, deren erster Parameter ein SQL-Text ist. */
    private static final Set<String> SQL_METHODEN = new HashSet<>(Arrays.asList(
            // Connection
            "prepareStatement", "prepareCall", "nativeSQL", "clientPrepareStatement", "serverPrepareStatement",
            // Statement
            "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "addBatch"));

    private final Object echt;
    private final Uebersetzer uebersetzer;
    private final Connection verbindung;   // bei Statements: die Hülle der Verbindung

    private Huelle(Object echt, Uebersetzer uebersetzer, Connection verbindung) {
        this.echt = echt;
        this.uebersetzer = uebersetzer;
        this.verbindung = verbindung;
    }

    @SuppressWarnings("unchecked")
    static <T> T um(T echt, Uebersetzer uebersetzer, Connection verbindung) {
        Huelle h = new Huelle(echt, uebersetzer, verbindung);
        ClassLoader lader = echt.getClass().getClassLoader();
        try {
            return (T) Proxy.newProxyInstance(lader, alleSchnittstellen(echt.getClass()), h);
        } catch (IllegalArgumentException e) {
            // Falls eine Treiber-eigene Schnittstelle nicht sichtbar ist: nur die Standard-Schnittstelle.
            Class<?> standard = echt instanceof Connection ? Connection.class
                    : echt instanceof CallableStatement ? CallableStatement.class
                    : echt instanceof PreparedStatement ? PreparedStatement.class
                    : Statement.class;
            return (T) Proxy.newProxyInstance(Huelle.class.getClassLoader(), new Class<?>[] {standard}, h);
        }
    }

    private static Class<?>[] alleSchnittstellen(Class<?> klasse) {
        Set<Class<?>> s = new LinkedHashSet<>();
        for (Class<?> k = klasse; k != null; k = k.getSuperclass()) {
            for (Class<?> i : k.getInterfaces()) {
                if (Modifier.isPublic(i.getModifiers())) {
                    s.add(i);
                }
            }
        }
        return s.toArray(new Class<?>[0]);
    }

    @Override
    public Object invoke(Object proxy, Method methode, Object[] args) throws Throwable {
        String name = methode.getName();
        Class<?>[] typen = methode.getParameterTypes();

        if (typen.length == 0) {
            if (name.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (name.equals("toString")) {
                return "OIDA-SQL[" + echt + "]";
            }
            if (name.equals("getConnection") && verbindung != null) {
                return verbindung;
            }
        } else if (typen.length == 1 && name.equals("equals") && typen[0] == Object.class) {
            return proxy == args[0];
        }

        if (typen.length > 0 && typen[0] == String.class && SQL_METHODEN.contains(name)) {
            args = args.clone();
            args[0] = uebersetzer.uebersetzen((String) args[0]);
        }

        Object ergebnis;
        try {
            ergebnis = methode.invoke(echt, args);
        } catch (InvocationTargetException e) {
            Throwable ursache = e.getCause();
            if (ursache instanceof SQLException) {
                throw uebersetzer.verziert((SQLException) ursache);
            }
            throw ursache;
        }

        if (ergebnis instanceof Statement && proxy instanceof Connection) {
            return um(ergebnis, uebersetzer, (Connection) proxy);
        }
        return ergebnis;
    }
}
