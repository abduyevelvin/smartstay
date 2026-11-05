package com.smartstay.hotel.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.smartstay.hotel.document.HotelDocument;
import com.smartstay.hotel.dto.CreateHotelRequest;
import com.smartstay.hotel.dto.HotelResponse;
import com.smartstay.hotel.dto.UpdateHotelRequest;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface HotelMapper {

    HotelDocument toDocument(CreateHotelRequest request);

    HotelResponse toResponse(HotelDocument hotelDocument);

    void updateDocument(UpdateHotelRequest request,
                        @MappingTarget HotelDocument hotelDocument);
}
