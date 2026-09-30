package com.example.expensetracker.service;

import com.example.expensetracker.entity.Category;
import com.example.expensetracker.entity.Transaction;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.enums.PaymentMethod;
import com.example.expensetracker.enums.TransactionType;
import com.example.expensetracker.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvExportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    private CsvExportService csvExportService;

    @BeforeEach
    void setUp() {
        csvExportService = new CsvExportService(transactionRepository);
    }

    @Test
    @DisplayName("Should return unchanged plain alphanumeric string without special chars")
    void testNormalStringEscaping() {
        assertEquals("Coffee", csvExportService.escapeCsvField("Coffee"));
        assertEquals("123.45", csvExportService.escapeCsvField("123.45"));
        assertEquals("", csvExportService.escapeCsvField(null));
    }

    @Test
    @DisplayName("Should escape commas and quotes according to RFC 4180")
    void testSpecialCharsEscaping() {
        assertEquals("\"Dinner, Drinks\"", csvExportService.escapeCsvField("Dinner, Drinks"));
        assertEquals("\"He said \"\"Hello\"\"\"", csvExportService.escapeCsvField("He said \"Hello\""));
        assertEquals("\"Line 1\nLine 2\"", csvExportService.escapeCsvField("Line 1\nLine 2"));
    }

    @Test
    @DisplayName("Should neutralize CSV injection prefix '=' with single quote")
    void testCsvInjectionEquals() {
        String input = "=cmd|' /C calc'!A0";
        String escaped = csvExportService.escapeCsvField(input);
        assertTrue(escaped.startsWith("\"'="));
    }

    @Test
    @DisplayName("Should neutralize CSV injection prefixes '+', '-', '@' with single quote")
    void testCsvInjectionOtherSymbols() {
        String plus = "+12345";
        String minus = "-500";
        String at = "@SUM(A1:A10)";

        assertTrue(csvExportService.escapeCsvField(plus).startsWith("\"'+"));
        assertTrue(csvExportService.escapeCsvField(minus).startsWith("\"'-"));
        assertTrue(csvExportService.escapeCsvField(at).startsWith("\"'@"));
    }

    @Test
    @DisplayName("Should generate complete and well-formed CSV export")
    void testExportTransactionsCsv() {
        User user = new User(1L, "Test User", "test@test.com", "hash", "INR");
        Category category = new Category(10L, user, "Food", TransactionType.EXPENSE, "#FF0000", "food");

        Transaction tx = new Transaction(
            101L, user, category, TransactionType.EXPENSE,
            BigDecimal.valueOf(250.50), "Lunch, with team",
            LocalDate.of(2026, 9, 15), PaymentMethod.UPI, "=Note with injection"
        );

        when(transactionRepository.findForExport(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(tx));

        byte[] resultBytes = csvExportService.exportTransactionsCsv(1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        String csv = new String(resultBytes, StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertEquals(2, lines.length);
        assertEquals("ID,Date,Type,Category,Amount,Description,Payment Method,Notes", lines[0]);
        assertTrue(lines[1].contains("101"));
        assertTrue(lines[1].contains("\"Lunch, with team\""));
        assertTrue(lines[1].contains("\"'=Note with injection\""));
    }
}
