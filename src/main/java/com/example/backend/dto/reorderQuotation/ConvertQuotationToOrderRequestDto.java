package com.example.backend.dto.reorderQuotation;

import com.example.backend.dto.order.OrderItemRequestDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConvertQuotationToOrderRequestDto {

    private Long supplierId;

    private String priority;

    private String notes;

    private Long totalPrice;

    // Optional: if procurement wants to override items or prices during conversion
    private List<OrderItemRequestDto> items;
}

