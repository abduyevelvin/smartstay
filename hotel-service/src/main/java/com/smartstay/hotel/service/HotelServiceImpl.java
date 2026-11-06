package com.smartstay.hotel.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.smartstay.hotel.dto.CreateHotelRequest;
import com.smartstay.hotel.dto.CreateRoomRequest;
import com.smartstay.hotel.dto.HotelResponse;
import com.smartstay.hotel.dto.RoomResponse;
import com.smartstay.hotel.dto.UpdateHotelRequest;
import com.smartstay.hotel.exceptions.ForbiddenException;
import com.smartstay.hotel.exceptions.ResourceExistsException;
import com.smartstay.hotel.exceptions.ResourceNotFoundException;
import com.smartstay.hotel.mapper.HotelMapper;
import com.smartstay.hotel.mapper.RoomMapper;
import com.smartstay.hotel.repository.HotelRepository;
import com.smartstay.hotel.repository.RoomRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final HotelMapper hotelMapper;
    private final RoomMapper roomMapper;

    @Override
    public HotelResponse createHotel(final CreateHotelRequest request,
                                     final String managerId) {
        final var hotelDocument = hotelMapper.toDocument(request);
        hotelDocument.setManagerId(managerId);

        final var saved = hotelRepository.save(hotelDocument);
        log.info("Hotel created — id={}, managerId={}", saved.getId(), managerId);

        return hotelMapper.toResponse(saved);
    }

    @Override
    public HotelResponse getHotelById(final String id) {
        log.debug("Fetching hotel — id={}", id);

        final var hotel = hotelRepository.findById(id)
                                         .orElseThrow(
                                                 () -> new ResourceNotFoundException("Hotel not found with id: " + id));

        return hotelMapper.toResponse(hotel);
    }

    @Override
    public Page<HotelResponse> searchHotels(final String city,
                                            final String country,
                                            final Double minStars,
                                            final Pageable pageable) {
        log.debug("Searching hotels — city={}, country={}, minStars={}, page={}, size={}", city, country, minStars,
                pageable.getPageNumber(), pageable.getPageSize());

        return hotelRepository.searchHotels(city, country, minStars, pageable)
                              .map(hotelMapper::toResponse);
    }

    @Override
    public HotelResponse updateHotel(final String id,
                                     final UpdateHotelRequest request,
                                     final String managerId) {
        final var hotelDocument = hotelRepository.findById(id)
                                                 .orElseThrow(() -> new ResourceNotFoundException(
                                                         "Hotel not found with id: " + id));

        if (!hotelDocument.getManagerId()
                          .equals(managerId)) {
            log.warn("Forbidden update attempt — hotelId={}, requesterId={}", id, managerId);
            throw new ForbiddenException("You are not authorized to update this hotel");
        }

        hotelMapper.updateDocument(request, hotelDocument);

        final var saved = hotelRepository.save(hotelDocument);
        log.info("Hotel updated — id={}, managerId={}", saved.getId(), managerId);

        return hotelMapper.toResponse(saved);
    }

    @Override
    public void deleteHotel(final String id,
                            final String managerId) {
        final var hotelDocument = hotelRepository.findById(id)
                                                 .orElseThrow(() -> new ResourceNotFoundException(
                                                         "Hotel not found with id: " + id));

        if (!hotelDocument.getManagerId()
                          .equals(managerId)) {
            log.warn("Forbidden delete attempt — hotelId={}, requesterId={}", id, managerId);
            throw new ForbiddenException("You are not authorized to delete this hotel");
        }

        roomRepository.deleteAllByHotelId(id);
        hotelRepository.deleteById(id);
        log.info("Hotel deleted — id={}, managerId={}", id, managerId);
    }

    @Override
    public RoomResponse addRoom(final String hotelId,
                                final CreateRoomRequest request,
                                final String managerId) {
        final var hotelDocument = hotelRepository.findById(hotelId)
                                                 .orElseThrow(() -> new ResourceNotFoundException(
                                                         "Hotel not found with id: " + hotelId));

        if (!hotelDocument.getManagerId()
                          .equals(managerId)) {
            log.warn("Forbidden add-room attempt — hotelId={}, requesterId={}", hotelId, managerId);
            throw new ForbiddenException("You are not authorized to add rooms to this hotel");
        }

        if (roomRepository.existsByHotelIdAndRoomNumber(hotelId, request.roomNumber())) {
            throw new ResourceExistsException(
                    "Room number " + request.roomNumber() + " already exists in hotel: " + hotelId);
        }

        final var roomDocument = roomMapper.toDocument(request);
        roomDocument.setHotelId(hotelId);
        roomDocument.setAvailable(true);

        final var saved = roomRepository.save(roomDocument);
        log.info("Room added — roomId={}, hotelId={}, managerId={}", saved.getId(), hotelId, managerId);

        return roomMapper.toResponse(saved);
    }

    @Override
    public RoomResponse getRoomByHotelAndById(final String hotelId,
                                              final String roomId) {
        log.debug("Fetching room - hotelId={}, roomId={}", hotelId, roomId);

        final var room = roomRepository.findByHotelIdAndId(hotelId, roomId)
                                       .orElseThrow(() -> new ResourceNotFoundException(
                                               "Room not found with id: " + roomId + " in hotel: " + hotelId));

        return roomMapper.toResponse(room);
    }

    @Override
    public List<RoomResponse> getRoomsByHotel(final String hotelId) {
        log.debug("Fetching rooms — hotelId={}", hotelId);

        return roomRepository.findByHotelId(hotelId)
                             .stream()
                             .map(roomMapper::toResponse)
                             .toList();
    }

    @Override
    public List<RoomResponse> getAvailableRooms(final String hotelId) {
        log.debug("Fetching available rooms — hotelId={}", hotelId);

        // TODO: wire Booking Service in Phase 4 to check real-time availability
        return roomRepository.findByHotelIdAndAvailable(hotelId, true)
                             .stream()
                             .map(roomMapper::toResponse)
                             .toList();
    }
}
