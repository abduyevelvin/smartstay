package com.smartstay.booking.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;

import com.smartstay.booking.dto.BookingResponse;
import com.smartstay.booking.entity.BookingEntity;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    BookingResponse toResponse(BookingEntity entity);

    List<BookingResponse> toListResponse(List<BookingEntity> entity);
}
