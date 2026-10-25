package com.example.triplesweb.service;

import com.example.triplesweb.dto.ChemistryGroupDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.ChemistryGroup;
import com.example.triplesweb.entity.ChemistryLabelTone;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ChemistryGroupRepository;
import com.example.triplesweb.util.ChemistryHtmlCleaner;
import com.example.triplesweb.util.ChemistryImages;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChemistryGroupService {

    private final ChemistryGroupRepository chemistryGroupRepository;
    private final ArtistRepository artistRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ChemistryGroupDto> findAll() {
        return chemistryGroupRepository.findAll().stream()
                .map(this::toDto)
                .sorted(Comparator
                        .comparing((ChemistryGroupDto d) -> d.getMembers().size())
                        .thenComparing(d -> String.join(",", d.getMembers())))
                .toList();
    }

    @Transactional
    public ChemistryGroupDto upsert(List<String> members, String label, String labelEn,
                                    String description, String descriptionEn,
                                    MultipartFile image, List<MultipartFile> images,
                                    String keepImageUrlsJson, boolean clearImage,
                                    String linksJson, String labelTone) {
        ResolvedMembers resolved = resolveMembers(members);
        if (!StringUtils.hasText(label)) {
            throw new IllegalArgumentException("케미명은 필수입니다.");
        }
        if (description == null) {
            description = "";
        }
        ChemistryLabelTone tone = ChemistryLabelTone.fromParam(labelTone);

        ChemistryGroup entity = chemistryGroupRepository.findByMemberKey(resolved.memberKey())
                .orElseGet(() -> ChemistryGroup.builder()
                        .memberKey(resolved.memberKey())
                        .membersJson("[]")
                        .label("")
                        .description("")
                        .labelTone(ChemistryLabelTone.NONE)
                        .build());

        entity.setMemberKey(resolved.memberKey());
        entity.setMembersJson(writeMembersJson(resolved.orderedNames()));
        entity.setLabel(label.trim());
        entity.setLabelEn(StringUtils.hasText(labelEn) ? labelEn.trim() : null);
        entity.setDescription(ChemistryHtmlCleaner.clean(description));
        entity.setDescriptionEn(ChemistryHtmlCleaner.clean(descriptionEn != null ? descriptionEn : ""));
        entity.setLinksJson(normalizeLinksJson(linksJson));
        entity.setLabelTone(tone);

        applyImages(entity, image, images, keepImageUrlsJson, clearImage);

        return toDto(chemistryGroupRepository.save(entity));
    }

    private void applyImages(ChemistryGroup entity, MultipartFile image, List<MultipartFile> images,
                             String keepImageUrlsJson, boolean clearImage) {
        List<String> previous = ChemistryImages.resolve(entity.getImagesJson(), entity.getImageUrl(), objectMapper);
        List<MultipartFile> uploads = ChemistryImages.collectUploads(image, images);
        try {
            ChemistryImages.Result merged = ChemistryImages.merge(
                    previous,
                    keepImageUrlsJson,
                    uploads,
                    clearImage,
                    objectMapper,
                    file -> {
                        SavedMeta meta = fileStorageService.saveWithMeta(file, "chemistry");
                        return meta.getUrl();
                    });
            entity.setImagesJson(ChemistryImages.write(merged.urls(), objectMapper));
            entity.setImageUrl(ChemistryImages.primary(merged.urls()));
            for (String removed : merged.removed()) {
                deleteManagedImageAfterCommit(removed);
            }
        } catch (IOException e) {
            throw new IllegalStateException("이미지 저장에 실패했습니다.", e);
        }
    }

    @Transactional
    public void delete(Long id) {
        chemistryGroupRepository.findById(id).ifPresent(this::hardDelete);
    }

    @Transactional
    public void deleteByMembers(List<String> members) {
        ResolvedMembers resolved = resolveMembers(members);
        chemistryGroupRepository.findByMemberKey(resolved.memberKey()).ifPresent(this::hardDelete);
    }

    private void hardDelete(ChemistryGroup entity) {
        List<String> previous = ChemistryImages.resolve(entity.getImagesJson(), entity.getImageUrl(), objectMapper);
        chemistryGroupRepository.delete(entity);
        for (String url : previous) {
            deleteManagedImageAfterCommit(url);
        }
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

    private ResolvedMembers resolveMembers(List<String> members) {
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("멤버를 선택해 주세요.");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : members) {
            if (!StringUtils.hasText(raw)) continue;
            String name = raw.trim();
            if (!TripleSMembers.NAMES_KO.contains(name)) {
                throw new IllegalArgumentException("알 수 없는 멤버: " + name);
            }
            unique.add(name);
        }
        if (unique.size() < 3) {
            throw new IllegalArgumentException("3인 이상 케미는 멤버를 3명 이상 선택해야 합니다.");
        }
        List<Artist> artists = unique.stream()
                .map(name -> artistRepository.findByName(name)
                        .orElseThrow(() -> new IllegalArgumentException("아티스트 없음: " + name)))
                .sorted(Comparator.comparing(Artist::getArtistId))
                .toList();
        String memberKey = artists.stream()
                .map(a -> String.valueOf(a.getArtistId()))
                .collect(Collectors.joining(","));
        List<String> orderedNames = artists.stream().map(Artist::getName).toList();
        List<Long> artistIds = artists.stream().map(Artist::getArtistId).toList();
        return new ResolvedMembers(memberKey, orderedNames, artistIds);
    }

    private String writeMembersJson(List<String> names) {
        try {
            return objectMapper.writeValueAsString(names);
        } catch (IOException e) {
            throw new IllegalStateException("멤버 목록 직렬화 실패", e);
        }
    }

    private List<String> readMembersJson(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw == null ? List.of() : raw.stream().filter(StringUtils::hasText).map(String::trim).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private String normalizeLinksJson(String linksJson) {
        Map<String, String> links = parseLinks(linksJson);
        if (links.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(links);
        } catch (IOException e) {
            throw new IllegalArgumentException("링크 형식이 올바르지 않습니다.");
        }
    }

    private Map<String, String> parseLinks(String linksJson) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!StringUtils.hasText(linksJson)) return out;
        try {
            Map<String, String> raw = objectMapper.readValue(linksJson, new TypeReference<>() {});
            if (raw == null) return out;
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

    private ChemistryGroupDto toDto(ChemistryGroup g) {
        List<String> members = new ArrayList<>(readMembersJson(g.getMembersJson()));
        List<Long> artistIds = new ArrayList<>();
        if (StringUtils.hasText(g.getMemberKey())) {
            for (String part : g.getMemberKey().split(",")) {
                try {
                    artistIds.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        List<String> imageUrls = ChemistryImages.resolve(g.getImagesJson(), g.getImageUrl(), objectMapper);
        return ChemistryGroupDto.builder()
                .id(g.getId())
                .memberKey(g.getMemberKey())
                .members(members)
                .artistIds(artistIds)
                .label(g.getLabel())
                .description(g.getDescription())
                .labelEn(g.getLabelEn())
                .descriptionEn(g.getDescriptionEn())
                .imageUrl(ChemistryImages.primary(imageUrls))
                .imageUrls(imageUrls)
                .links(parseLinks(g.getLinksJson()))
                .labelTone(g.getLabelTone() != null ? g.getLabelTone() : ChemistryLabelTone.NONE)
                .build();
    }

    private record ResolvedMembers(String memberKey, List<String> orderedNames, List<Long> artistIds) {}
}
