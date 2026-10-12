package com.example.triplesweb.util;

import lombok.Getter;

import java.util.List;


@Getter
public enum TrackInfo {

    ACCESS_TRACK (
            List.of("Access", "Generation", "Rolex", "찰나(Charla)","Dimention (AAA Ver.)"),
            List.of("0:46","2:45","3:13","3:04","3:12"),
            List.of(false, true, false, false, false),
            ("ACCESS")),
    AESTHETIC_TRACK (
            List.of("Cherry 100%", "Cherry Talk", "Touch", "Hide & Seek", "Deja-vu", "Dimension (KRE Ver.)"),
            List.of("0:48","2:52","2:57","2:55","2:53","3:04"),
            List.of(false, true, false, false, false, false),
            ("AESTHETIC")),

    LOVELALBUM_TRACK (
            List.of ("&#8576","Girls' Capitalism", "복합성 (Complexity)", "Black Soul Dress", "Seoul Sonyo Sound", "Cry Baby", "Speed Love", "Number 8"),
            List.of ("1:02","3:39","2:39","3:37","2:31","2:38","2:29","1:16"),
            List.of (false, true, false, false, false, false, false, false),
            ("LOVELALBUM")),
    EVOLALBUM_TRACK (
            List.of ("&#10209", "Invincible", "Rhodanthe", "Heavy Metal Wings", "미열 37.5", "Moto Princess", "Oui", "Enhanced Flower"),
            List.of ("0:52","3:18","3:48","3:16","3:42","3:15","2:40","1:54"),
            List.of (false, true, false, false, false, false, false, false ),
            ("EVOLALBUM")),
    ARIAALBUM_TRACK (
            List.of("Door","첫 이별(Farewell my first"),
            List.of("3:09","3:30"),
            List.of(true,false),
            ("SADNESS")),
    NXTALBUM_TRACK (
            List.of("Just Do It"),
            List.of("2:34"),
            List.of(true),
            ("NXTALBUM")),
    GLOWALBUM_TRACK (
            List.of("내적 댄스"),
            List.of("3:02"),
            List.of(true),
            ("GLOWALBUM")),
    VVALBUM_TRACK (
            List.of("Visual Virtue","Hit the Floor", "Choom", "Eclair", "연애 소설", "Atmosphere (VV ver.)", "12 Rings", "Vision", "Bionic Power", "Visionary Vision"),
            List.of("0:50","2:58","3:12","2:50","2:59","2:38","3:21","2:48","2:33","0:48"),
            List.of(false, true, false, false, false, false, false, false, false, false),
            ("VVALBUM")),
    BEYONDBEAUTY_TRACK (
            List.of("Magic Shine New Zone", "Fly Up", "Cameo Love", "Bubble Gum Girl", "Q&A", "Christmas Alone"),
            List.of("0:51","2:40","2:35","2:48","3:26","2:51"),
            List.of(false, false, false, false, false, true),
            ("BEYONDBEAUTY")
    ),
    ASSEMBLE_TRACK (
            List.of("Beam","Before the Rise", "Rising", "Colorful", "The Baddest", "New look", "초월 (Chowall)"),
            List.of("2:39", "0:54", "2:40", "3:23", "3:36", "3:29", "1:04"),
            List.of(false, false, true, false, false, false, false),
            ("ASSEMBLE")
    ),
    ASSEMBLE24_TRACK (
            List.of("S", "Girls Never Die", "가시권(Heart Raider", "Midnight Flower", "White Soul Sneakers", "치유(Chiyu)", "24", "이면의 이면", "Non scale", "Dimension"),
            List.of("1:14", "3:08", "3:00", "2:47", "3:04", "2:51", "2:34", "3:03", "3:27", "3:18"),
            List.of(false, true, false, false, false, false, false, false, false, false),
            ("ASSEMBLE24")
    ),
    ASSEMBLE25_TRACK (
            List.of("@% (Alpha Percent)", "깨어(Are you Alive", "추리 소설(Detective Soseol)", "어제 우리 불꽃놀이(Firework Diary", "Love Child", "Persona","Too Hot", "Diablo", "Friend Zone", "Love2Love"),
            List.of("1:03", "3:07", "3:21", "3:09", "3:02", "3:03", "3:22", "3:21", "3:18", "3:11"),
            List.of(false, true, false, false, false, false, false, false, false, false),
            ("ASSEMBLE25")
    ),
    LOVESIDE_TRACK (
            List.of("Sad Girls Schemin'", "Peer", "Baby Flower", "Type of Girl", "Sleek", "I Like That", "Me Myself Mode"),
            List.of("2:42", "1:01", "3:08", "3:09", "2:31", "2:16", "2:19"),
            List.of(false, false, true, false, false, false, false),
            ("LOVESIDE")
            );


    private final List<String> title;
    private final List<String> duration;
    private final List<Boolean> isTitle;
    private final String albumName;



    TrackInfo ( List<String> title, List<String> duration, List<Boolean> isTitle, String albumName) {
        this.title = title;
        this.duration = duration;
        this.isTitle = isTitle;
        this.albumName = albumName;
    }
}
