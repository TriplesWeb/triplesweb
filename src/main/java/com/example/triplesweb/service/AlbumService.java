package com.example.triplesweb.service;

import com.example.triplesweb.entity.Album;
import com.example.triplesweb.repository.AlbumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 앨범 목록·상세 조회.
 */
@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;

    @Transactional(readOnly = true)
    public List<Album> listAll() {
        return albumRepository.findAll(Sort.by("releaseDate").ascending()
                .and(Sort.by("albumId").ascending()));
    }

    @Transactional(readOnly = true)
    public Album getDetail(long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new IllegalArgumentException("해당 앨범이 존재하지 않습니다. id=" + albumId));
        if (album.getUnit() != null && album.getUnit().getSubUnits() != null) {
            album.getUnit().getSubUnits().size();
        }
        if (album.getTracks() != null) {
            album.getTracks().size();
        }
        return album;
    }
}
