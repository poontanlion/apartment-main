package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.Room;
import com.victory.apartment.model.UtilityBill;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.repository.UtilityBillRepository;
import com.victory.apartment.repository.BillPaymentRepository;
import com.victory.apartment.repository.BillingSettingsRepository;
import com.victory.apartment.service.ActivityLogService;
import com.victory.apartment.service.PriceRuleException;
import com.victory.apartment.service.RentQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test cases for UtilityBillController.
 * Covers success and failure scenarios for the API endpoints.
 */
@WebMvcTest(UtilityBillController.class)
public class UtilityBillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UtilityBillRepository utilityBillRepository;

    @MockBean
    private BillPaymentRepository billPaymentRepository;

    @MockBean
    private BillingSettingsRepository billingSettingsRepository;

    @MockBean
    private RoomRepository roomRepository;

    @MockBean
    private ActivityLogService logService;

    @MockBean
    private RentQuoteService rentQuoteService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final YearMonth DECEMBER = YearMonth.of(2026, 12);

    private UtilityBill mockBill;
    private Room mockRoom;

    @BeforeEach
    void setUp() {
        Mockito.when(rentQuoteService.quote("rm-123", DECEMBER))
                .thenReturn(new RentQuoteService.RentQuote(6000.0, "LEASE", "ls-1"));

        mockBill = new UtilityBill();
        mockBill.setId("bill-123");
        mockBill.setInvoiceNo("INV-001");
        mockBill.setRoomId("rm-123");
        mockBill.setRoomNumber("A101");
        mockBill.setStatus("Pending");
        mockBill.setTotalAmount(6000.0);
        mockBill.setCreatedAt(LocalDateTime.now());

        mockRoom = new Room();
        mockRoom.setId("rm-123");
        mockRoom.setBuildingId("bld-1");
        mockRoom.setRoomNumber("A101");
    }

    /**
     * SUCCESS Case: Retrieve all utility bills without buildingId filter.
     * Expect HTTP 200 OK and a list of bills.
     */
    @Test
    void getAllBills_Success() throws Exception {
        Mockito.when(utilityBillRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockBill));

        mockMvc.perform(get("/api/utility-bills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bill-123"))
                .andExpect(jsonPath("$[0].invoiceNo").value("INV-001"));
    }

    /**
     * SUCCESS Case: Retrieve utility bills with buildingId filter.
     * Expect HTTP 200 OK and a filtered list of bills.
     */
    @Test
    void getAllBillsWithFilter_Success() throws Exception {
        Mockito.when(utilityBillRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockBill));
        Mockito.when(roomRepository.findByBuildingIdOrderByRoomNumberAsc("bld-1")).thenReturn(Arrays.asList(mockRoom));

        mockMvc.perform(get("/api/utility-bills").param("buildingId", "bld-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bill-123"));
    }

    /**
     * SUCCESS Case: Create a new utility bill.
     * Expect HTTP 200 OK and the created bill data.
     */
    @Test
    void createBill_Success() throws Exception {
        Mockito.when(utilityBillRepository.save(any(UtilityBill.class))).thenReturn(mockBill);
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));

        UtilityBill newBill = new UtilityBill();
        newBill.setRoomId("rm-123");
        newBill.setBillingMonth("December 2026");
        newBill.setCurrWaterMeter(110.0);
        newBill.setPrevWaterMeter(100.0);
        newBill.setCurrElectricMeter(220.0);
        newBill.setPrevElectricMeter(200.0);

        mockMvc.perform(post("/api/utility-bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newBill)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bill-123"));
    }

    private UtilityBill billWithUsage() {
        UtilityBill bill = new UtilityBill();
        bill.setRoomId("rm-123");
        bill.setBillingMonth("December 2026");
        bill.setPrevWaterMeter(100.0);
        bill.setCurrWaterMeter(108.0);
        bill.setWaterRate(9.0);
        bill.setPrevElectricMeter(400.0);
        bill.setCurrElectricMeter(480.0);
        bill.setElectricRate(4.0);
        bill.setCommonFee(300.0);
        return bill;
    }

    private void saveReturnsArgument() {
        Mockito.when(utilityBillRepository.save(any(UtilityBill.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createBill_ignoresClientRent() throws Exception {
        saveReturnsArgument();
        UtilityBill bill = billWithUsage();
        bill.setRentAmount(1.0);

        mockMvc.perform(post("/api/utility-bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bill)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentAmount").value(6000.0))
                .andExpect(jsonPath("$.totalAmount").value(6692.0));
    }

    @Test
    void createBill_setsLeaseIdFromQuote() throws Exception {
        saveReturnsArgument();
        UtilityBill bill = billWithUsage();
        bill.setLeaseId("ls-from-client");

        mockMvc.perform(post("/api/utility-bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bill)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaseId").value("ls-1"));
    }

    @Test
    void createBill_noRoomIs400() throws Exception {
        Mockito.when(rentQuoteService.quote(null, DECEMBER))
                .thenThrow(new PriceRuleException(HttpStatus.BAD_REQUEST, "ROOM_NOT_FOUND", "Room not found."));
        UtilityBill bill = billWithUsage();
        bill.setRoomId(null);

        mockMvc.perform(post("/api/utility-bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bill)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));

        Mockito.verify(utilityBillRepository, Mockito.never()).save(any());
    }

    @Test
    void createBill_malformedMonthIs400() throws Exception {
        UtilityBill bill = billWithUsage();
        bill.setBillingMonth("2026-12");

        mockMvc.perform(post("/api/utility-bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bill)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MONTH"));
    }

    @Test
    void createBill_existingIdIs409() throws Exception {
        Mockito.when(utilityBillRepository.existsById("bill-123")).thenReturn(true);
        UtilityBill bill = billWithUsage();
        bill.setId("bill-123");

        mockMvc.perform(post("/api/utility-bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bill)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BILL_ALREADY_EXISTS"));

        Mockito.verify(utilityBillRepository, Mockito.never()).save(any());
    }

    @Test
    void updateBill_keepsRentAndMonth() throws Exception {
        mockBill.setRentAmount(6000.0);
        mockBill.setBillingMonth("August 2026");
        Mockito.when(utilityBillRepository.findById("bill-123")).thenReturn(Optional.of(mockBill));
        saveReturnsArgument();
        UtilityBill incoming = billWithUsage();
        incoming.setRentAmount(1.0);
        incoming.setBillingMonth("May 2026");

        mockMvc.perform(put("/api/utility-bills/bill-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incoming)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentAmount").value(6000.0))
                .andExpect(jsonPath("$.billingMonth").value("August 2026"));
    }

    @Test
    void updateBill_recalculatesTotal() throws Exception {
        mockBill.setRentAmount(6000.0);
        Mockito.when(utilityBillRepository.findById("bill-123")).thenReturn(Optional.of(mockBill));
        saveReturnsArgument();
        UtilityBill incoming = billWithUsage();
        incoming.setTotalAmount(1.0);

        mockMvc.perform(put("/api/utility-bills/bill-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incoming)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(6692.0));
    }

    @Test
    void updateBill_legacyNullRentKeepsStoredTotal() throws Exception {
        legacyBillWithRentInsideTotal();
        UtilityBill incoming = billWithUsage();
        incoming.setTotalAmount(1.0);

        mockMvc.perform(put("/api/utility-bills/bill-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incoming)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentAmount").value(5500.0))
                .andExpect(jsonPath("$.totalAmount").value(6192.0));
    }

    @Test
    void updateBill_legacyMeterCorrectionKeepsLinesAddingUp() throws Exception {
        legacyBillWithRentInsideTotal();
        UtilityBill incoming = billWithUsage();
        incoming.setCurrWaterMeter(104.0);

        mockMvc.perform(put("/api/utility-bills/bill-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incoming)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waterAmount").value(36.0))
                .andExpect(jsonPath("$.rentAmount").value(5500.0))
                .andExpect(jsonPath("$.totalAmount").value(6156.0));
    }

    /** Before this feature a bill without rent charged 5,500 in its total but stored no rent: 5500 + 72 + 320 + 300. */
    private void legacyBillWithRentInsideTotal() {
        mockBill.setRentAmount(null);
        mockBill.setWaterAmount(72.0);
        mockBill.setElectricAmount(320.0);
        mockBill.setCommonFee(300.0);
        mockBill.setTotalAmount(6192.0);
        Mockito.when(utilityBillRepository.findById("bill-123")).thenReturn(Optional.of(mockBill));
        saveReturnsArgument();
    }

    @Test
    void rentQuote_returnsQuote() throws Exception {
        mockMvc.perform(get("/api/utility-bills/rent-quote")
                .param("roomId", "rm-123")
                .param("month", "December 2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentAmount").value(6000.0))
                .andExpect(jsonPath("$.source").value("LEASE"))
                .andExpect(jsonPath("$.leaseId").value("ls-1"));
    }

    @Test
    void rentQuote_malformedMonthIs400() throws Exception {
        mockMvc.perform(get("/api/utility-bills/rent-quote")
                .param("roomId", "rm-123")
                .param("month", "2026-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MONTH"));
    }

    /**
     * SUCCESS Case: Update an existing utility bill.
     * Expect HTTP 200 OK.
     */
    @Test
    void updateBill_Success() throws Exception {
        Mockito.when(utilityBillRepository.findById("bill-123")).thenReturn(Optional.of(mockBill));
        Mockito.when(utilityBillRepository.save(any(UtilityBill.class))).thenReturn(mockBill);

        mockMvc.perform(put("/api/utility-bills/bill-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockBill)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bill-123"));
    }

    /**
     * FAILURE Case: Update a utility bill that does not exist.
     * Expect HTTP 404 Not Found.
     */
    @Test
    void updateBill_NotFound() throws Exception {
        Mockito.when(utilityBillRepository.findById("bill-999")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/utility-bills/bill-999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockBill)))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Update status of a utility bill.
     * Expect HTTP 200 OK.
     */
    @Test
    void updateStatus_Success() throws Exception {
        Mockito.when(utilityBillRepository.findById("bill-123")).thenReturn(Optional.of(mockBill));
        Mockito.when(utilityBillRepository.save(any(UtilityBill.class))).thenReturn(mockBill);

        Map<String, String> req = new HashMap<>();
        req.put("status", "Paid");

        mockMvc.perform(put("/api/utility-bills/bill-123/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Paid")); // mockBill originally has "Pending", but the controller modifies the entity passed to save
    }

    /**
     * FAILURE Case: Update status of a utility bill that does not exist.
     * Expect HTTP 404 Not Found.
     */
    @Test
    void updateStatus_NotFound() throws Exception {
        Mockito.when(utilityBillRepository.findById("bill-999")).thenReturn(Optional.empty());

        Map<String, String> req = new HashMap<>();
        req.put("status", "Paid");

        mockMvc.perform(put("/api/utility-bills/bill-999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }
}
