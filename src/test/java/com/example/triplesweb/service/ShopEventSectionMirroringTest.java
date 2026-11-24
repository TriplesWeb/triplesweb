package com.example.triplesweb.service;

import com.example.triplesweb.dto.ShopSectionDto;
import com.example.triplesweb.entity.EventSection;
import com.example.triplesweb.entity.Shop;
import com.example.triplesweb.entity.ShopSection;
import com.example.triplesweb.repository.EventRepository;
import com.example.triplesweb.repository.EventSectionRepository;
import com.example.triplesweb.repository.ShopRepository;
import com.example.triplesweb.repository.ShopSectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopEventSectionMirroringTest {
    private final List<EventSection> events = new ArrayList<>();
    private final List<ShopSection> shops = new ArrayList<>();
    private ShopAdminService shopAdmin;
    private EventAdminService eventAdmin;

    @BeforeEach
    void setUp() {
        var eventSections = mock(EventSectionRepository.class);
        var shopSections = mock(ShopSectionRepository.class);
        var eventRepository = mock(EventRepository.class);
        var shopService = mock(ShopService.class);
        var storage = mock(FileStorageService.class);
        shopAdmin = new ShopAdminService(mock(ShopRepository.class), shopSections, eventRepository, eventSections,
                storage, shopService);
        eventAdmin = new EventAdminService(eventRepository, eventSections, storage, new ObjectMapper(),
                mock(EventService.class), shopService, shopAdmin);

        when(eventSections.findAll()).thenReturn(events);
        when(eventSections.findAllByOrderBySortOrderAscSectionIdAsc()).thenReturn(events);
        when(eventSections.findById(anyLong())).thenAnswer(call -> events.stream()
                .filter(section -> section.getSectionId().equals(call.getArgument(0))).findFirst());
        when(eventSections.save(any())).thenAnswer(call -> call.getArgument(0));
        when(shopSections.findAll()).thenReturn(shops);
        when(shopSections.findAllByOrderBySortOrderAscSectionIdAsc()).thenReturn(shops);
        when(shopSections.findById(anyLong())).thenAnswer(call -> shops.stream()
                .filter(section -> section.getSectionId().equals(call.getArgument(0))).findFirst());
        when(shopSections.save(any())).thenAnswer(call -> {
            ShopSection section = call.getArgument(0);
            if (section.getSectionId() == null) {
                section.setSectionId(100L + shops.size());
                shops.add(section);
            }
            return section;
        });
    }

    @Test
    void onlyTheThreeExactNamesAreMirroredAndMarked() {
        addEvents(" 콘서트 ", "팬콘", "팬미팅", "공연", "팬미팅 특별");
        shops.add(new ShopSection(7L, "공연", 0));
        shops.add(new ShopSection(8L, " 팬콘 ", 1));
        shops.add(new ShopSection(9L, "단독 굿즈", 2));

        var sections = shopAdmin.listSections();
        shopAdmin.ensureEventSectionsMirrored();

        assertThat(sections).extracting(ShopSectionDto::getName)
                .containsExactly("공연", " 팬콘 ", "단독 굿즈", "콘서트", "팬미팅");
        assertThat(sections.stream().filter(ShopSectionDto::isFromEvent).map(ShopSectionDto::getName))
                .containsExactly(" 팬콘 ", "콘서트", "팬미팅");
        assertThat(shops).hasSize(5);
        assertThatThrownBy(() -> shopAdmin.deleteSection(8L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shopAdmin.updateSection(8L, "전용 굿즈", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventRenameBetweenMirroredNamesKeepsTheShopSectionId() {
        addEvents("콘서트");
        var original = new ShopSection(7L, "콘서트", 2);
        shops.add(original);

        eventAdmin.updateSection(1L, " 팬콘 ", null);

        assertThat(shops).containsExactly(original);
        assertThat(original.getName()).isEqualTo("팬콘");
        assertThat(original.getSectionId()).isEqualTo(7L);
        assertThat(shopAdmin.listSections().get(0).isFromEvent()).isTrue();
    }

    @Test
    void eventRenameLeavingAndReenteringTheAllowlistPreservesExistingShopGroups() {
        addEvents("콘서트");
        shops.add(new ShopSection(7L, "콘서트", 0));
        shops.add(new ShopSection(8L, "공연", 1));

        eventAdmin.updateSection(1L, "공연", null);
        assertThat(shopAdmin.listSections()).noneMatch(ShopSectionDto::isFromEvent);

        eventAdmin.updateSection(1L, "팬미팅", null);
        assertThat(shops).extracting(ShopSection::getName).containsExactly("콘서트", "팬미팅");
        assertThat(shopAdmin.listSections().stream().filter(ShopSectionDto::isFromEvent).map(ShopSectionDto::getName))
                .containsExactly("팬미팅");
    }

    @Test
    void eventRenameToAnExistingShopSectionDoesNotOverwriteEitherGroup() {
        addEvents("공연");
        shops.add(new ShopSection(7L, "공연", 0));
        shops.add(new ShopSection(8L, "팬콘", 1));

        eventAdmin.updateSection(1L, "팬콘", null);

        assertThat(shops).extracting(ShopSection::getName).containsExactly("공연", "팬콘");
        assertThat(shopAdmin.listSections().stream().filter(ShopSectionDto::isFromEvent).map(ShopSectionDto::getSectionId))
                .containsExactly(8L);
    }

    @Test
    void eventRenameFromUnmirroredNameKeepsShopGoodsInTheSameSection() {
        addEvents("공연");
        var original = new ShopSection(7L, " 공연 ", 2);
        shops.add(original);
        var goods = new Shop();
        goods.setSection(original);

        eventAdmin.updateSection(1L, "콘서트", null);

        assertThat(shops).containsExactly(original);
        assertThat(goods.getSection().getSectionId()).isEqualTo(7L);
        assertThat(goods.getSection().getName()).isEqualTo("콘서트");
        assertThat(shopAdmin.listSections().get(0).isFromEvent()).isTrue();
    }

    @Test
    void eventSectionCreateAndRenameRejectCaseAndWhitespaceDuplicates() {
        addEvents("  Festival  ", "별도 이벤트");

        assertThatThrownBy(() -> eventAdmin.createSection(" fESTIVAL "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 구분명입니다: fESTIVAL");
        assertThatThrownBy(() -> eventAdmin.updateSection(2L, " FESTIVAL ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 구분명입니다: FESTIVAL");
        assertThat(events.get(1).getName()).isEqualTo("별도 이벤트");
        assertThat(eventAdmin.updateSection(1L, "festival", null).getName()).isEqualTo("festival");
    }

    @Test
    void shopSectionCreateAndRenameRejectCaseAndWhitespaceDuplicates() {
        shops.add(new ShopSection(7L, "  Festival  ", 0));
        shops.add(new ShopSection(8L, "별도 굿즈", 1));

        assertThatThrownBy(() -> shopAdmin.createSection(" fESTIVAL "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 구분명입니다: fESTIVAL");
        assertThatThrownBy(() -> shopAdmin.updateSection(8L, " FESTIVAL ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 구분명입니다: FESTIVAL");
        assertThat(shops.get(1).getName()).isEqualTo("별도 굿즈");
        assertThat(shopAdmin.updateSection(7L, "festival", null).getName()).isEqualTo("festival");
    }

    private void addEvents(String... names) {
        for (String name : names) {
            events.add(new EventSection((long) events.size() + 1, name, events.size()));
        }
    }
}
