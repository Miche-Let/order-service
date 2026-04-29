package com.michelet.order.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.michelet.order.application.OrderCommandService;
import com.michelet.order.application.dto.OrderResult;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OrderController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class})
@AutoConfigureRestDocs(uriScheme = "http", uriHost = "localhost", uriPort = 19700)
@ActiveProfiles("test")
@Import(RestDocsConfig.class)
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCommandService orderCommandService;

    @Test
    @DisplayName("상태 확인: 서비스가 정상 동작하면 200을 반환한다")
    void healthCheck() throws Exception {
        // given
        given(orderCommandService.checkHealth()).willReturn("Order Command Service is Healthy");

        mockMvc.perform(get("/api/v1/orders/health")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value("Order Command Service is Healthy"))
            .andDo(document("{class-name}/{method-name}"));
    }

    @Test
    @DisplayName("성공: 올바른 주문 요청 시 200 OK와 문서를 생성한다")
    void createOrderDocs() throws Exception {
        // given
        UUID mockOrderId = UUID.randomUUID();
        given(orderCommandService.createOrder(any()))
            .willReturn(new OrderResult(mockOrderId, "OCCUPIED"));

        String requestJson = """
            {
                "reservationId": "550e8400-e29b-41d4-a716-446655440002",
                "restaurantId": "550e8400-e29b-41d4-a716-446655440003",
                "orderName": "치킨 외 1건",
                "reservedDate": "2026-05-01",
                "receivingMethod": "PICKUP",
                "expiredAt": "2026-05-01T20:00:00",
                "items": [
                    {
                        "optionId": "550e8400-e29b-41d4-a716-446655440001",
                        "productName": "치킨",
                        "orderPrice": 20000,
                        "quantity": 2
                    }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/orders")
                .header("X-User-Id", "550e8400-e29b-41d4-a716-446655440000") // 헤더 추가
                .content(requestJson)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.orderId").value(mockOrderId.toString()))
            .andDo(document("{class-name}/{method-name}",
                requestHeaders(
                    headerWithName("X-User-Id").description("사용자 식별 ID")
                ),
                requestFields(
                    fieldWithPath("reservationId").type(JsonFieldType.STRING).description("예약 식별 ID"),
                    fieldWithPath("restaurantId").type(JsonFieldType.STRING).description("식당 ID"),
                    fieldWithPath("orderName").type(JsonFieldType.STRING).description("주문 요약 제목"),
                    fieldWithPath("reservedDate").type(JsonFieldType.STRING).description("예약 날짜 (YYYY-MM-DD)"),
                    fieldWithPath("receivingMethod").type(JsonFieldType.STRING).description("수령 방법 (PICKUP/SHIPPING)")
                        .optional(),
                    fieldWithPath("expiredAt").type(JsonFieldType.STRING).description("수령 기한 (YYYY-MM-DDTHH:mm:ss)"),
                    fieldWithPath("items").type(JsonFieldType.ARRAY).description("주문 항목 리스트"),
                    fieldWithPath("items[].optionId").type(JsonFieldType.STRING).description("상품 옵션 ID"),
                    fieldWithPath("items[].productName").type(JsonFieldType.STRING).description("주문 시점 상품명"),
                    fieldWithPath("items[].orderPrice").type(JsonFieldType.NUMBER).description("주문 시점 가격"),
                    fieldWithPath("items[].quantity").type(JsonFieldType.NUMBER).description("주문 수량")
                ),
                responseFields(
                    fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                    fieldWithPath("data.orderId").type(JsonFieldType.STRING).description("생성된 주문 ID"),
                    fieldWithPath("data.status").type(JsonFieldType.STRING).description("주문 상태"),
                    fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시간"),
                    fieldWithPath("traceId").type(JsonFieldType.STRING).description("추적 ID").optional(),
                    fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드").optional(),
                    fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지").optional()
                )
            ));
    }

    @Test
    @DisplayName("실패: 필수 파라미터 누락 시 400 에러를 반환한다")
    void createOrderFailInvalidInput() throws Exception {
        String invalidJson = "{\"reservationId\": null}";

        mockMvc.perform(post("/api/v1/orders")
                .header("X-User-Id", "550e8400-e29b-41d4-a716-446655440000") // 헤더 부재로 인한 400을 피하기 위해 정상 헤더 세팅
                .content(invalidJson)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andDo(document("{class-name}/{method-name}"));

        // Validation 실패 시 Service 레이어가 호출되지 않음을 검증
        verify(orderCommandService, never()).createOrder(any());
    }
}
