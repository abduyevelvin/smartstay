package com.smartstay.hotel.mapper;

import org.mapstruct.Mapper;

import com.smartstay.hotel.document.RoomDocument;
import com.smartstay.hotel.dto.CreateRoomRequest;
import com.smartstay.hotel.dto.RoomResponse;

@Mapper(componentModel = "spring")
public interface RoomMapper {

    RoomDocument toDocument(CreateRoomRequest request);

    RoomResponse toResponse(RoomDocument roomDocument);
}
