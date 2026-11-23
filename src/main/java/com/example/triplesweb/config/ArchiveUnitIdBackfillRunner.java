package com.example.triplesweb.config;

import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.UnitMember;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.UnitService;
import com.example.triplesweb.util.ArchiveFolderClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * unit_id 컬럼 도입 전 출연진만으로 유닛 폴더에 들어가던 글을 저장 unitId 로 한 번 옮긴다.
 * 수동 해제(null) 이후에는 다시 채우지 않도록 {@code app_one_shot} 마커로 1회만 실행한다.
 */
@Slf4j
@Component
@Order(3)
@Profile("!test")
@RequiredArgsConstructor
public class ArchiveUnitIdBackfillRunner implements ApplicationRunner {

    private static final String MARKER = "archive_unit_id_exact_cast_v1";

    private final JdbcTemplate jdbc;
    private final ArchiveService archiveService;
    private final UnitService unitService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!markerTableReady() || markerApplied()) return;
            int updated = archiveService.backfillUnitIdsFromExactCast(unitSnapshots(unitService.listVisibleForArchive()));
            jdbc.update("INSERT INTO app_one_shot(name) VALUES (?)", MARKER);
            log.info("Archive unit_id exact-cast backfill applied: {} rows", updated);
        } catch (RuntimeException e) {
            log.warn("Archive unit_id backfill skipped: {}", e.getMessage());
        }
    }

    private boolean markerTableReady() {
        try {
            Long n = jdbc.queryForObject(
                    """
                            SELECT COUNT(*) FROM information_schema.tables
                            WHERE table_schema = DATABASE() AND table_name = 'app_one_shot'
                            """,
                    Long.class);
            return n != null && n > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean markerApplied() {
        try {
            Long n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM app_one_shot WHERE name = ?", Long.class, MARKER);
            return n != null && n > 0;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static List<ArchiveFolderClassifier.UnitMembers> unitSnapshots(List<Unit> units) {
        List<ArchiveFolderClassifier.UnitMembers> out = new ArrayList<>();
        if (units == null) return out;
        for (Unit unit : units) {
            if (unit == null || unit.getUnitId() == null) continue;
            Set<String> members = new LinkedHashSet<>();
            if (unit.getUnitMembers() != null) {
                for (UnitMember membership : unit.getUnitMembers()) {
                    if (membership == null || membership.getArtist() == null) continue;
                    members.addAll(ArchiveFolderClassifier.membersOf(membership.getArtist().getName()));
                }
            }
            String formation = unit.getFormationDate();
            out.add(new ArchiveFolderClassifier.UnitMembers(
                    unit.getUnitId(),
                    unit.getUnitName(),
                    members,
                    formation == null || formation.isBlank() ? null : formation.trim()));
        }
        return out;
    }
}
