package com.example.triplesweb.service;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Chemistry;
import com.example.triplesweb.entity.ChemistryLabelTone;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ChemistryRepository;
import com.example.triplesweb.util.ChemistryHtmlCleaner;
import com.example.triplesweb.util.TripleSMembers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

    private final ChemistryRepository chemistryRepository;
    private final ArtistRepository artistRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ChemistryDto> findAll() {
        return chemistryRepository.findAllWithArtists().stream()
                .sorted(Comparator
                        .comparing((Chemistry c) -> indexOf(c.getArtistA().getName()))
                        .thenComparing(c -> indexOf(c.getArtistB().getName())))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ChemistryDto upsert(String member1, String member2, String label, String labelEn,
                               String description, String descriptionEn, MultipartFile image,
                               boolean clearImage, String linksJson, String labelTone) {
        Artist[] pair = normalizePair(member1, member2);
        if (!StringUtils.hasText(label)) {
            throw new IllegalArgumentException("케미명은 필수입니다.");
        }
        if (description == null) {
            description = "";
        }
        ChemistryLabelTone tone = ChemistryLabelTone.fromParam(labelTone);

        Chemistry entity = chemistryRepository
                .findByArtistPair(pair[0].getArtistId(), pair[1].getArtistId())
                .orElseGet(() -> Chemistry.builder()
                        .artistA(pair[0])
                        .artistB(pair[1])
                        .crossFlag(false)
                        .labelTone(ChemistryLabelTone.NONE)
                        .label("")
                        .description("")
                        .build());

        // 한·영 케미명·설명 각각 저장. 영문 케미명이 비면 표시 시 한국어로 폴백.
        entity.setLabel(label.trim());
        entity.setLabelEn(StringUtils.hasText(labelEn) ? labelEn.trim() : null);
        entity.setDescription(ChemistryHtmlCleaner.clean(description));
        entity.setDescriptionEn(ChemistryHtmlCleaner.clean(descriptionEn != null ? descriptionEn : ""));
        entity.setLinksJson(normalizeLinksJson(linksJson));
        entity.setLabelTone(tone);

        if (image != null && !image.isEmpty()) {
            try {
                String previousUrl = entity.getImageUrl();
                SavedMeta meta = fileStorageService.saveWithMeta(image, "chemistry");
                entity.setImageUrl(meta.getUrl());
                deleteManagedImageAfterCommit(previousUrl);
            } catch (IOException e) {
                throw new IllegalStateException("이미지 저장에 실패했습니다.", e);
            }
        } else if (clearImage) {
            String previousUrl = entity.getImageUrl();
            entity.setImageUrl(null);
            deleteManagedImageAfterCommit(previousUrl);
        }

        return toDto(chemistryRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        chemistryRepository.findById(id).ifPresent(this::hardDelete);
    }

    @Transactional
    public void deleteByMembers(String member1, String member2) {
        Artist[] pair = normalizePair(member1, member2);
        chemistryRepository.findByArtistPair(pair[0].getArtistId(), pair[1].getArtistId())
                .ifPresent(this::hardDelete);
    }

    private void hardDelete(Chemistry entity) {
        String previous = entity.getImageUrl();
        chemistryRepository.delete(entity);
        deleteManagedImageAfterCommit(previous);
    }

    private void deleteManagedImageAfterCommit(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteManagedImage(imageUrl);
                }
            });
        } else {
            deleteManagedImage(imageUrl);
        }
    }

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
        }
    }

    public int indexOf(String member) {
        return TripleSMembers.indexOf(member);
    }

    public List<String> memberNames() {
        return List.copyOf(TripleSMembers.NAMES_KO);
    }

    /**
     * 표시 순서 기준으로 (앞 멤버, 뒤 멤버) Artist 쌍을 반환한다.
     * 시드·API upsert/delete가 동일 규칙을 쓴다.
     */
    public Artist[] resolveArtistPair(String member1, String member2) {
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
        String first = ia < ib ? a : b;
        String second = ia < ib ? b : a;
        Artist artistA = artistRepository.findByName(first)
                .orElseThrow(() -> new IllegalArgumentException("아티스트 없음: " + first));
        Artist artistB = artistRepository.findByName(second)
                .orElseThrow(() -> new IllegalArgumentException("아티스트 없음: " + second));
        return new Artist[]{artistA, artistB};
    }

    private Artist[] normalizePair(String member1, String member2) {
        return resolveArtistPair(member1, member2);
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
        if (c.getArtistA() == null || c.getArtistB() == null) {
            throw new IllegalStateException("Chemistry#" + c.getId() + "에 Artist FK가 없습니다. 시드/마이그레이션을 확인하세요.");
        }
        return ChemistryDto.builder()
                .id(c.getId())
                .memberA(c.getArtistA().getName())
                .memberB(c.getArtistB().getName())
                .artistAId(c.getArtistA().getArtistId())
                .artistBId(c.getArtistB().getArtistId())
                .label(c.getLabel())
                .description(c.getDescription())
                .labelEn(c.getLabelEn())
                .descriptionEn(c.getDescriptionEn())
                .imageUrl(c.getImageUrl())
                .links(parseLinks(c.getLinksJson()))
                .crossFlag(c.isCrossFlag())
                .labelTone(c.getLabelTone() != null ? c.getLabelTone() : ChemistryLabelTone.NONE)
                .build();
    }
}
