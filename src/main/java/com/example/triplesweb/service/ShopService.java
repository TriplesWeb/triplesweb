package com.example.triplesweb.service;

import com.example.triplesweb.dto.ShopCardDto;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.entity.Shop;
import com.example.triplesweb.entity.ShopImage;
import com.example.triplesweb.repository.ShopItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/**
 * 쇼핑(굿즈) 상품 등록 및 목록 조회를 담당한다.
 * 이미지 업로드와 {@link Shop} 저장, 카테고리별 카드 DTO 변환을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopItemRepository shopItemRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public Shop registerGoods(Shop shop, List<MultipartFile> files) throws IOException {
        if (shop == null) throw new IllegalArgumentException("상품 정보가 없습니다.");

        if (files != null) {
                for (MultipartFile f : files) {
                    if (f == null || f.isEmpty()) continue;

                    SavedMeta meta = fileStorageService.saveWithMeta(f);
                    ShopImage img = ShopImage.builder()
                            .shop(shop)
                            .file(meta)
                            .build();
                    shop.addImage(img);
                }
            }
        return shopItemRepository.save(shop);
    }
    @Transactional(readOnly = true)
    public List<ShopCardDto> getCardsByCategory (String category) {
        return shopItemRepository.findByCategory(category).stream()
                .map(this::toCardDto)
                .toList();
    }

    public ShopCardDto toCardDto(Shop shop) {
        String mainImageUrl = (shop.getImages() != null && !shop.getImages().isEmpty() &&
                shop.getImages().get(0).getFile() != null)
                ? safeUrl(shop.getImages().get(0).getFile().getUrl())  // "/uploads/..." 기대
                : "/images/no-image.png";
        return new ShopCardDto(
                shop.getId(),
                shop.getCategory(),
                shop.getName(),
                shop.getPrice(),
                shop.isSoldout(),
                mainImageUrl
        );
    }
    private String safeUrl(String url) {
        return (url == null || url.isBlank()) ? "/images/no-image.png" : url;
    }
}
