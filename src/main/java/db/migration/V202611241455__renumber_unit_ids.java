package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class V202611241455__renumber_unit_ids extends BaseJavaMigration {
    private static final int TEMPORARY_OFFSET = 1000;
    private static final List<UnitNumber> NUMBERS = List.of(
            new UnitNumber(1, 1, "Acid Angel from Asia"),
            new UnitNumber(2, 2, "+(KR)ystal Eyes"),
            new UnitNumber(14, 3, "Acid Eyes"),
            new UnitNumber(3, 4, "LOVElution"),
            new UnitNumber(4, 5, "EVOLution"),
            new UnitNumber(6, 6, "NXT"),
            new UnitNumber(5, 7, "Aria"),
            new UnitNumber(7, 8, "Glow"),
            new UnitNumber(8, 9, "Visionary Vision"),
            new UnitNumber(15, 10, "∞!"),
            new UnitNumber(16, 11, "Alphie"),
            new UnitNumber(9, 12, "msnz"),
            new UnitNumber(10, 13, "msnz moon"),
            new UnitNumber(11, 14, "msnz sun"),
            new UnitNumber(12, 15, "msnz neptune"),
            new UnitNumber(13, 16, "msnz zenith"),
            new UnitNumber(17, 17, "Aura")
    );
    private static final UnitColumn UNIT_ID = new UnitColumn("unit", "unit_id");
    private static final List<UnitColumn> REFERENCES = List.of(
            new UnitColumn("unit", "parent_unit_id"),
            new UnitColumn("unit_member", "unit_id"),
            new UnitColumn("album", "unit_id"),
            new UnitColumn("archive", "unit_id")
    );

    @Override
    public boolean canExecuteInTransaction() {
        // ALTER TABLE implicitly commits in MariaDB. Manage the data transaction separately.
        return false;
    }

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        boolean originalAutoCommit = connection.getAutoCommit();
        try (var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                var units = readUnits(statement);
                // Flyway can run before the application seeds a fresh database.
                if (units.isEmpty()) {
                    connection.commit();
                    return;
                }
                boolean alreadyRenumbered = alreadyRenumbered(units);
                validateSchema(statement, connection.getCatalog());
                validateReferences(statement);
                if (!alreadyRenumbered) {
                    renumber(connection, statement);
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }

            resetAutoIncrement(statement);
        }
    }

    private static void resetAutoIncrement(Statement statement) throws SQLException {
        // Also repair the counter if a previous run committed the IDs but failed the ALTER.
        long nextId;
        try (var rows = statement.executeQuery("""
                SELECT (SELECT MAX(unit_id) + 1 FROM unit) AS next_id, AUTO_INCREMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'unit'
                """)) {
            if (!rows.next()) {
                throw new SQLException("Missing metadata for unit AUTO_INCREMENT");
            }
            nextId = rows.getLong("next_id");
            if (rows.getLong("AUTO_INCREMENT") == nextId) {
                return;
            }
        }
        statement.executeUpdate("ALTER TABLE unit AUTO_INCREMENT = " + nextId);
    }

    private static Map<Integer, UnitRow> readUnits(Statement statement) throws SQLException {
        Map<Integer, UnitRow> units = new HashMap<>();
        try (var rows = statement.executeQuery("""
                SELECT unit_id, unit_name, parent_unit_id, visible FROM unit FOR UPDATE
                """)) {
            while (rows.next()) {
                units.put(rows.getInt("unit_id"), new UnitRow(rows.getString("unit_name"),
                        rows.getObject("parent_unit_id", Integer.class), rows.getBoolean("visible")));
            }
        }
        return units;
    }

    static boolean alreadyRenumbered(Map<Integer, UnitRow> units) throws SQLException {
        if (units.size() != NUMBERS.size()) {
            throw new SQLException("Expected " + NUMBERS.size() + " units, found " + units.size());
        }
        if (matchesNumbering(units, true)) {
            return true;
        }
        if (!matchesNumbering(units, false)) {
            throw new SQLException("Unexpected unit IDs, names or MSNZ hierarchy: " + units);
        }
        return false;
    }

    private static boolean matchesNumbering(Map<Integer, UnitRow> units, boolean newIds) {
        for (UnitNumber number : NUMBERS) {
            UnitRow row = units.get(newIds ? number.newId() : number.oldId());
            if (row == null || !number.matchesName(row.name())) {
                return false;
            }
        }

        int parentId = newIds ? 12 : 9;
        UnitRow parent = units.get(parentId);
        if (parent.visible() || parent.parentId() != null) {
            return false;
        }
        for (int childId = parentId + 1; childId <= parentId + 4; childId++) {
            if (!Integer.valueOf(parentId).equals(units.get(childId).parentId())) {
                return false;
            }
        }
        return true;
    }

    private static void validateSchema(Statement statement, String schema) throws SQLException {
        // Include unconstrained columns such as archive.unit_id and inbound foreign keys of any name.
        try (var rows = statement.executeQuery("""
                SELECT c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME, t.ENGINE
                FROM information_schema.COLUMNS c
                JOIN information_schema.TABLES t
                  ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
                WHERE c.TABLE_SCHEMA = DATABASE() AND t.TABLE_TYPE = 'BASE TABLE'
                  AND (c.COLUMN_NAME = 'unit_id' OR c.COLUMN_NAME LIKE '%!_unit!_id' ESCAPE '!')
                UNION
                SELECT k.TABLE_SCHEMA, k.TABLE_NAME, k.COLUMN_NAME, t.ENGINE
                FROM information_schema.KEY_COLUMN_USAGE k
                JOIN information_schema.TABLES t
                  ON t.TABLE_SCHEMA = k.TABLE_SCHEMA AND t.TABLE_NAME = k.TABLE_NAME
                WHERE k.REFERENCED_TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME = 'unit'
                  AND k.REFERENCED_COLUMN_NAME = 'unit_id'
                """)) {
            while (rows.next()) {
                var column = new UnitColumn(rows.getString("TABLE_NAME"), rows.getString("COLUMN_NAME"));
                if (!schema.equals(rows.getString("TABLE_SCHEMA"))
                        || !(UNIT_ID.equals(column) || REFERENCES.contains(column))) {
                    throw new SQLException("Unexpected unit ID column: " + rows.getString("TABLE_SCHEMA") + "." + column);
                }
                if (!"InnoDB".equalsIgnoreCase(rows.getString("ENGINE"))) {
                    throw new SQLException("Unit ID migration requires InnoDB for " + column.table());
                }
            }
        }
    }

    private static void validateReferences(Statement statement) throws SQLException {
        for (UnitColumn reference : REFERENCES) {
            try (var rows = statement.executeQuery("""
                    SELECT ref.%2$s FROM %1$s ref LEFT JOIN unit u ON ref.%2$s = u.unit_id
                    WHERE ref.%2$s IS NOT NULL AND u.unit_id IS NULL LIMIT 1
                    """.formatted(reference.table(), reference.column()))) {
                if (rows.next()) {
                    throw new SQLException("Unknown unit ID " + rows.getLong(1) + " in " + reference);
                }
            }
        }
    }

    private static void renumber(Connection connection, Statement statement) throws SQLException {
        int originalForeignKeyChecks;
        try (var rows = statement.executeQuery("SELECT @@SESSION.foreign_key_checks")) {
            rows.next();
            originalForeignKeyChecks = rows.getInt(1);
        }
        try {
            statement.execute("SET SESSION foreign_key_checks = 0");
            updateNumbers(connection, UNIT_ID);
            for (UnitColumn reference : REFERENCES) {
                updateNumbers(connection, reference);
            }
            validateReferences(statement);
        } finally {
            statement.execute("SET SESSION foreign_key_checks = " + originalForeignKeyChecks);
        }
    }

    private static void updateNumbers(Connection connection, UnitColumn column) throws SQLException {
        try (var update = connection.prepareStatement("UPDATE %s SET %s = ? WHERE %s = ?"
                .formatted(column.table(), column.column(), column.column()))) {
            for (UnitNumber number : NUMBERS) {
                if (number.oldId() != number.newId()) {
                    update.setInt(1, number.oldId() + TEMPORARY_OFFSET);
                    update.setInt(2, number.oldId());
                    update.addBatch();
                }
            }
            update.executeBatch();
            for (UnitNumber number : NUMBERS) {
                if (number.oldId() != number.newId()) {
                    update.setInt(1, number.newId());
                    update.setInt(2, number.oldId() + TEMPORARY_OFFSET);
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    record UnitRow(String name, Integer parentId, boolean visible) {}

    private record UnitNumber(int oldId, int newId, String name) {
        boolean matchesName(String actualName) {
            // The original Java seed used this spelling for AAA.
            return name.equalsIgnoreCase(actualName) || (oldId == 1 && "ACID ANGEL OF ASIA".equalsIgnoreCase(actualName));
        }
    }

    private record UnitColumn(String table, String column) {
        @Override
        public String toString() {
            return table + "." + column;
        }
    }
}
