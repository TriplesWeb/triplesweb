package db.migration;

import com.example.triplesweb.util.ArchiveFolderClassifier;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Uses the same alias parser as archive writes, including historic member names and compact S-number chains. */
public class V202611241410__archive_cast_backfill extends BaseJavaMigration {
    private static final int BATCH_SIZE = 500;

    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        try (var select = connection.prepareStatement(
                     "SELECT id, artist_names FROM archive WHERE id > ? ORDER BY id LIMIT ?");
             var update = connection.prepareStatement("""
                     UPDATE archive SET cast_member_mask = ?, cast_member_count = ?, cast_member_rank = ? WHERE id = ?
                     """)) {
            long lastId = 0;
            while (true) {
                List<Cast> batch = new ArrayList<>();
                select.setLong(1, lastId);
                select.setInt(2, BATCH_SIZE);
                try (var rows = select.executeQuery()) {
                    while (rows.next()) {
                        long id = rows.getLong(1);
                        batch.add(new Cast(id, ArchiveFolderClassifier.memberMask(rows.getString(2))));
                        lastId = id;
                    }
                }
                if (batch.isEmpty()) return;
                for (Cast row : batch) {
                    update.setInt(1, row.mask());
                    update.setInt(2, Integer.bitCount(row.mask()));
                    update.setInt(3, ArchiveFolderClassifier.memberRank(row.mask()));
                    update.setLong(4, row.id());
                    update.addBatch();
                }
                update.executeBatch();
            }
        }
    }

    private record Cast(long id, int mask) {}
}
