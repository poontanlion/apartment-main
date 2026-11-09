package com.victory.apartment.controller;

import com.victory.apartment.model.BillingSettings;
import com.victory.apartment.repository.BillingSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bank details and QR code that invoices print. */
@WebMvcTest(BillingSettingsController.class)
class BillingSettingsControllerTest {

    private static final String QR = "data:image/png;base64,iVBORw0KGgo=";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BillingSettingsRepository repository;

    private BillingSettings stored;

    @BeforeEach
    void setUp() {
        stored = new BillingSettings();
        stored.setMonthlyInterestRate(2.0);
        Mockito.when(repository.findById("default")).thenReturn(Optional.of(stored));
        Mockito.when(repository.save(any(BillingSettings.class))).thenAnswer(call -> call.getArgument(0));
    }

    private String body(String bank, String name, String number, String qr) {
        return String.format("{\"bankName\":%s,\"accountName\":%s,\"accountNumber\":%s,\"qrCodeImage\":%s}",
                json(bank), json(name), json(number), json(qr));
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    void savesBankDetailsAndQr_keepingTheInterestRate() throws Exception {
        mockMvc.perform(put("/api/billing-settings/bank-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("  Kasikorn Bank ", "Poontan Apartment", "123-4-56789-0", QR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("Kasikorn Bank"))
                .andExpect(jsonPath("$.accountName").value("Poontan Apartment"))
                .andExpect(jsonPath("$.accountNumber").value("123-4-56789-0"))
                .andExpect(jsonPath("$.qrCodeImage").value(QR))
                .andExpect(jsonPath("$.monthlyInterestRate").value(2.0));
    }

    @Test
    void blankValuesClearTheFields() throws Exception {
        stored.setBankName("Old Bank");
        stored.setQrCodeImage(QR);

        mockMvc.perform(put("/api/billing-settings/bank-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("", null, "  ", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").doesNotExist())
                .andExpect(jsonPath("$.qrCodeImage").doesNotExist());
    }

    @Test
    void rejectsAnAccountNumberWithLetters() throws Exception {
        mockMvc.perform(put("/api/billing-settings/bank-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Bank", "Name", "12AB34", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAQrThatIsNotAnImage() throws Exception {
        mockMvc.perform(put("/api/billing-settings/bank-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Bank", "Name", "123", "data:text/html;base64,PHNjcmlwdD4=")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnOversizedQr() throws Exception {
        String huge = "data:image/png;base64," + "A".repeat(1_500_000);
        mockMvc.perform(put("/api/billing-settings/bank-info")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Bank", "Name", "123", huge)))
                .andExpect(status().isBadRequest());
    }
}
