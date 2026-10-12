package com.example.triplesweb.dto;

import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.UnitMember;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;


@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UnitDto {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long unitId;
        private String unitName;
        private String smallName;
        private String unitImage;
        private String formationDate;
        private String DebutDate;
        private List<UnitMember> unitMembers = new ArrayList<>();
        private List<Album> albums = new ArrayList<>();
    }