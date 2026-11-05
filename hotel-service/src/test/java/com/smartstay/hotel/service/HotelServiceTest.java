package com.smartstay.hotel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.smartstay.hotel.document.HotelDocument;
import com.smartstay.hotel.document.RoomDocument;
import com.smartstay.hotel.dto.CreateHotelRequest;
import com.smartstay.hotel.dto.CreateRoomRequest;
import com.smartstay.hotel.dto.HotelResponse;
import com.smartstay.hotel.dto.RoomResponse;
import com.smartstay.hotel.dto.UpdateHotelRequest;
import com.smartstay.hotel.enums.Type;
import com.smartstay.hotel.exceptions.ForbiddenException;
import com.smartstay.hotel.exceptions.ResourceExistsException;
import com.smartstay.hotel.exceptions.ResourceNotFoundException;
import com.smartstay.hotel.mapper.HotelMapper;
import com.smartstay.hotel.mapper.RoomMapper;
import com.smartstay.hotel.repository.HotelRepository;
import com.smartstay.hotel.repository.RoomRepository;

@ExtendWith(MockitoExtension.class)
class HotelServiceTest {

    @Mock private HotelRepository hotelRepository;
    @Mock private RoomRepository  roomRepository;
    @Mock private HotelMapper     hotelMapper;
    @Mock private RoomMapper      roomMapper;

    @InjectMocks
    private HotelServiceImpl hotelService;

    private static final String MANAGER_ID   = "manager-1";
    private static final String OTHER_MANAGER = "manager-2";
    private static final String HOTEL_ID     = "hotel-1";
    private static final String ROOM_ID      = "room-1";

    private HotelDocument hotelDocument;
    private HotelResponse hotelResponse;
    private RoomDocument  roomDocument;
    private RoomResponse  roomResponse;

    @BeforeEach
    void setup() {
        hotelDocument = new HotelDocument();
        hotelDocument.setManagerId(MANAGER_ID);

        hotelResponse = new HotelResponse(HOTEL_ID, "Grand Hotel", "Luxury hotel",
                "Baku", "Azerbaijan", "Main St", 5.0, MANAGER_ID,
                4.5, 100, "14:00", "12:00", List.of(), List.of(), null, null, null);

        roomDocument = new RoomDocument();
        roomDocument.setHotelId(HOTEL_ID);
        roomDocument.setRoomNumber(101);

        roomResponse = new RoomResponse(ROOM_ID, HOTEL_ID, 101, Type.DOUBLE,
                150.0, 2, 1, true, List.of(), List.of(), null, null);
    }

    // ── createHotel ───────────────────────────────────────────────────────────

    @Test
    void createHotel_shouldSaveAndReturnResponse() {
        CreateHotelRequest request = new CreateHotelRequest(
                "Grand Hotel", "Luxury hotel", "Baku", "Azerbaijan",
                "Main St", 5.0, "14:00", "12:00", List.of(), List.of(), null);

        when(hotelMapper.toDocument(request)).thenReturn(hotelDocument);
        when(hotelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hotelMapper.toResponse(any())).thenReturn(hotelResponse);

        HotelResponse result = hotelService.createHotel(request, MANAGER_ID);

        assertThat(result).isNotNull();
        assertThat(result.managerId()).isEqualTo(MANAGER_ID);
        verify(hotelRepository).save(any());
    }

    @Test
    void createHotel_shouldSetManagerIdOnDocument() {
        CreateHotelRequest request = new CreateHotelRequest(
                "Grand Hotel", "Luxury", "Baku", "Azerbaijan",
                "Main St", 5.0, "14:00", "12:00", null, null, null);

        when(hotelMapper.toDocument(request)).thenReturn(hotelDocument);
        when(hotelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hotelMapper.toResponse(any())).thenReturn(hotelResponse);

        hotelService.createHotel(request, MANAGER_ID);

        assertThat(hotelDocument.getManagerId()).isEqualTo(MANAGER_ID);
    }

    // ── getHotelById ──────────────────────────────────────────────────────────

    @Test
    void getHotelById_shouldReturnHotel() {
        when(hotelRepository.findById(HOTEL_ID)).thenReturn(Optional.of(hotelDocument));
        when(hotelMapper.toResponse(hotelDocument)).thenReturn(hotelResponse);

        HotelResponse result = hotelService.getHotelById(HOTEL_ID);

        assertThat(result).isEqualTo(hotelResponse);
    }

    @Test
    void getHotelById_shouldThrowWhenNotFound() {
        when(hotelRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.getHotelById("ghost-id"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost-id");
    }

    // ── searchHotels ──────────────────────────────────────────────────────────

    @Test
    void searchHotels_shouldReturnPageOfHotels() {
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(hotelDocument), pageable, 1);

        when(hotelRepository.searchHotels("Baku", null, null, pageable)).thenReturn(page);
        when(hotelMapper.toResponse(hotelDocument)).thenReturn(hotelResponse);

        var result = hotelService.searchHotels("Baku", null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── updateHotel ───────────────────────────────────────────────────────────

    @Test
    void updateHotel_shouldUpdateAndReturnResponse() {
        UpdateHotelRequest request = new UpdateHotelRequest(
                "Updated Hotel", null, null, null, null, null, null, null, null, null, null);

        when(hotelRepository.findById(HOTEL_ID)).thenReturn(Optional.of(hotelDocument));
        when(hotelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hotelMapper.toResponse(any())).thenReturn(hotelResponse);

        HotelResponse result = hotelService.updateHotel(HOTEL_ID, request, MANAGER_ID);

        assertThat(result).isNotNull();
        verify(hotelMapper).updateDocument(request, hotelDocument);
        verify(hotelRepository).save(hotelDocument);
    }

    @Test
    void updateHotel_shouldThrowWhenNotFound() {
        when(hotelRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.updateHotel("ghost-id",
                new UpdateHotelRequest(null, null, null, null, null, null, null, null, null, null, null),
                MANAGER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateHotel_shouldThrowWhenNotOwner() {
        when(hotelRepository.findById(HOTEL_ID)).thenReturn(Optional.of(hotelDocument));

        assertThatThrownBy(() -> hotelService.updateHotel(HOTEL_ID,
                new UpdateHotelRequest(null, null, null, null, null, null, null, null, null, null, null),
                OTHER_MANAGER))
                .isInstanceOf(ForbiddenException.class);

        verify(hotelRepository, never()).save(any());
    }

    // ── deleteHotel ───────────────────────────────────────────────────────────

    @Test
    void deleteHotel_shouldDeleteHotelAndRooms() {
        when(hotelRepository.findById(HOTEL_ID)).thenReturn(Optional.of(hotelDocument));

        hotelService.deleteHotel(HOTEL_ID, MANAGER_ID);

        verify(roomRepository).deleteAllByHotelId(HOTEL_ID);
        verify(hotelRepository).deleteById(HOTEL_ID);
    }

    @Test
    void deleteHotel_shouldThrowWhenNotFound() {
        when(hotelRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.deleteHotel("ghost-id", MANAGER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(hotelRepository, never()).deleteById(anyString());
        verify(roomRepository, never()).deleteAllByHotelId(anyString());
    }

    @Test
    void deleteHotel_shouldThrowWhenNotOwner() {
        when(hotelRepository.findById(HOTEL_ID)).thenReturn(Optional.of(hotelDocument));

        assertThatThrownBy(() -> hotelService.deleteHotel(HOTEL_ID, OTHER_MANAGER))
                .isInstanceOf(ForbiddenException.class);

        verify(hotelRepository, never()).deleteById(anyString());
        verify(roomRepository, never()).deleteAllByHotelId(anyString());
    }

    // ── addRoom ───────────────────────────────────────────────────────────────

    @Test
    void addRoom_shouldSaveAndReturnResponse() {
        CreateRoomRequest request = new CreateRoomRequest(101, Type.DOUBLE, 150.0, 2, 1, null, null);

        when(hotelRepository.findById(HOTEL_ID)).thenReturn(Optional.of(hotelDocument));
        when(roomRepository.existsByHotelIdAndRoomNumber(HOTEL_ID, 101)).thenReturn(false);
        when(roomMapper.toDocument(request)).thenReturn(roomDocument);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roomMapper.toResponse(any())).thenReturn(roomResponse);

        RoomResponse result = hotelService.addRoom(HOTEL_ID, request, MANAGER_ID);

        assertThat(result).isNotNull();
        verify(roomRepository).save(any());
    }

    @Test
    void addRoom_shouldThrowWhenHotelNotFound() {
        when(hotelRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.addRoom("ghost-id",
                new CreateRoomRequest(101, Type.DOUBLE, 150.0, 2, 1, null, null), MANAGER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(roomRepository, never()).save(any());
    }

    @Test
    void addRoom_shouldThrowWhenNotOwner() {
        when(hotelRepository.findById(HOTEL_ID)).thenReturn(Optional.of(hotelDocument));

        assertThatThrownBy(() -> hotelService.addRoom(HOTEL_ID,
                new CreateRoomRequest(101, Type.DOUBLE, 150.0, 2, 1, null, null), OTHER_MANAGER))
                .isInstanceOf(ForbiddenException.class);

        verify(roomRepository, never()).save(any());
    }

    @Test
    void addRoom_shouldThrowWhenRoomNumberAlreadyExists() {
        CreateRoomRequest request = new CreateRoomRequest(101, Type.DOUBLE, 150.0, 2, 1, null, null);

        when(hotelRepository.findById(HOTEL_ID)).thenReturn(Optional.of(hotelDocument));
        when(roomRepository.existsByHotelIdAndRoomNumber(HOTEL_ID, 101)).thenReturn(true);

        assertThatThrownBy(() -> hotelService.addRoom(HOTEL_ID, request, MANAGER_ID))
                .isInstanceOf(ResourceExistsException.class)
                .hasMessageContaining("101");

        verify(roomRepository, never()).save(any());
    }

    // ── getRoomsByHotel ───────────────────────────────────────────────────────

    @Test
    void getRoomsByHotel_shouldReturnRooms() {
        when(roomRepository.findByHotelId(HOTEL_ID)).thenReturn(List.of(roomDocument));
        when(roomMapper.toResponse(roomDocument)).thenReturn(roomResponse);

        List<RoomResponse> result = hotelService.getRoomsByHotel(HOTEL_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(roomResponse);
    }

    // ── getAvailableRooms ─────────────────────────────────────────────────────

    @Test
    void getAvailableRooms_shouldReturnOnlyAvailableRooms() {
        when(roomRepository.findByHotelIdAndAvailable(HOTEL_ID, true)).thenReturn(List.of(roomDocument));
        when(roomMapper.toResponse(roomDocument)).thenReturn(roomResponse);

        List<RoomResponse> result = hotelService.getAvailableRooms(HOTEL_ID);

        assertThat(result).hasSize(1);
        verify(roomRepository).findByHotelIdAndAvailable(HOTEL_ID, true);
        verify(roomRepository, never()).findByHotelId(anyString());
    }
}
