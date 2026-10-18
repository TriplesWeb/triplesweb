package com.example.triplesweb.service;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.entity.Chemistry;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ChemistryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChemistryService {

    private static final List<String> MEMBERS = List.of(
            "윤서연", "정혜린", "이지우", "김채연", "김유연", "김수민", "김나경", "공유빈",
            "카에데", "서다현", "코토네", "곽연지", "니엔", "박소현", "신위", "마유",
            "린", "주빈", "정하연", "박시온", "김채원", "설린", "서아", "지연"
    );

    private final ChemistryRepository chemistryRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ChemistryDto> findAll() {
        return chemistryRepository.findAll().stream()
                .filter(c -> !c.isDeleted())
                .sorted(Comparator.comparing(Chemistry::getMemberA).thenComparing(Chemistry::getMemberB))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ChemistryDto upsert(String member1, String member2, String label, String description,
                               MultipartFile image, boolean clearImage, String linksJson) {
        String[] pair = normalizePair(member1, member2);
        if (!StringUtils.hasText(label)) {
            throw new IllegalArgumentException("케미명은 필수입니다.");
        }
        if (description == null) {
            description = "";
        }

        Chemistry entity = chemistryRepository.findByMemberAAndMemberB(pair[0], pair[1])
                .orElseGet(() -> Chemistry.builder()
                        .memberA(pair[0])
                        .memberB(pair[1])
                        .crossFlag(false)
                        .deleted(false)
                        .build());

        entity.setLabel(label.trim());
        entity.setDescription(description);
        entity.setLinksJson(normalizeLinksJson(linksJson));
        entity.setDeleted(false);

        if (image != null && !image.isEmpty()) {
            try {
                String previousUrl = entity.getImageUrl();
                SavedMeta meta = fileStorageService.saveWithMeta(image, "chemistry");
                entity.setImageUrl(meta.getUrl());
                deleteManagedImage(previousUrl);
            } catch (IOException e) {
                throw new IllegalStateException("이미지 저장에 실패했습니다.", e);
            }
        } else if (clearImage) {
            deleteManagedImage(entity.getImageUrl());
            entity.setImageUrl(null);
        }

        return toDto(chemistryRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        chemistryRepository.findById(id).ifPresent(this::softDelete);
    }

    @Transactional
    public void deleteByMembers(String member1, String member2) {
        String[] pair = normalizePair(member1, member2);
        Chemistry entity = chemistryRepository.findByMemberAAndMemberB(pair[0], pair[1])
                .orElseGet(() -> Chemistry.builder()
                        .memberA(pair[0])
                        .memberB(pair[1])
                        .label("")
                        .description("")
                        .crossFlag(false)
                        .build());
        softDelete(entity);
    }

    private void softDelete(Chemistry entity) {
        deleteManagedImage(entity.getImageUrl());
        entity.setDeleted(true);
        entity.setLabel("");
        entity.setDescription("");
        entity.setImageUrl(null);
        entity.setLinksJson(null);
        chemistryRepository.save(entity);
    }

    /**
     * 관리자 업로드분({@code /uploads/images/chemistry/...})만 디스크에서 삭제.
     * 정적 시드 경로({@code /uploads/chemistry/chem/...})는 건드리지 않는다.
     */
    private void deleteManagedImage(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return;
        }
        String relative = fileStorageService.toRelativePath(imageUrl);
        if (relative == null || !relative.startsWith("images/chemistry/")) {
            return;
        }
        try {
            fileStorageService.deleteFile(imageUrl);
        } catch (RuntimeException ignored) {
            // DB 갱신은 계속 진행 — 없는 파일이거나 이미 지워진 경우
        }
    }

    public int indexOf(String member) {
        int idx = MEMBERS.indexOf(member);
        if (idx < 0) {
            throw new IllegalArgumentException("알 수 없는 멤버: " + member);
        }
        return idx;
    }

    private String[] normalizePair(String member1, String member2) {
        if (!StringUtils.hasText(member1) || !StringUtils.hasText(member2)) {
            throw new IllegalArgumentException("멤버 두 명이 필요합니다.");
        }
        String a = member1.trim();
        String b = member2.trim();
        if (a.equals(b)) {
            throw new IllegalArgumentException("같은 멤버끼리의 케미는 만들 수 없습니다.");
        }
        int ia = indexOf(a);
        int ib = indexOf(b);
        return ia < ib ? new String[]{a, b} : new String[]{b, a};
    }

    private String normalizeLinksJson(String linksJson) {
        Map<String, String> links = parseLinks(linksJson);
        if (links.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(links);
        } catch (IOException e) {
            throw new IllegalArgumentException("링크 형식이 올바르지 않습니다.");
        }
    }

    private Map<String, String> parseLinks(String linksJson) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!StringUtils.hasText(linksJson)) {
            return out;
        }
        try {
            Map<String, String> raw = objectMapper.readValue(linksJson, new TypeReference<>() {});
            if (raw == null) {
                return out;
            }
            raw.forEach((k, v) -> {
                if (StringUtils.hasText(k) && StringUtils.hasText(v)) {
                    out.put(k.trim(), v.trim());
                }
            });
            return out;
        } catch (IOException e) {
            throw new IllegalArgumentException("링크 형식이 올바르지 않습니다.");
        }
    }

    private ChemistryDto toDto(Chemistry c) {
        return ChemistryDto.builder()
                .id(c.getId())
                .memberA(c.getMemberA())
                .memberB(c.getMemberB())
                .label(c.getLabel())
                .description(c.getDescription())
                .imageUrl(c.getImageUrl())
                .links(parseLinks(c.getLinksJson()))
                .deleted(c.isDeleted())
                .crossFlag(c.isCrossFlag())
                .build();
    }
}
