package com.example.expensetracker.service;

import com.example.expensetracker.entity.Transaction;
import com.example.expensetracker.repository.TransactionRepository;
import com.example.expensetracker.util.DateRangeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Service generating CSV exports safe against CSV injection attacks.
 */
@Service
public class CsvExportService {

    private final TransactionRepository transactionRepository;

    public CsvExportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Exports transactions for a user within a date range to a sanitized CSV byte array.
     *
     * @param userId ID of the authenticated user
     * @param from start date
     * @param to end date
     * @return byte array containing CSV data
     */
    @Transactional(readOnly = true)
    public byte[] exportTransactionsCsv(Long userId, LocalDate from, LocalDate to) {
        DateRangeUtil.DateRange range = DateRangeUtil.resolveAndValidate(from, to);
        List<Transaction> transactions = transactionRepository.findForExport(userId, range.from(), range.to());

        StringBuilder sb = new StringBuilder();
        // CSV Header
        sb.append("ID,Date,Type,Category,Amount,Description,Payment Method,Notes\n");

        for (Transaction t : transactions) {
            sb.append(escapeCsvField(String.valueOf(t.getId()))).append(",");
            sb.append(escapeCsvField(t.getTransactionDate().toString())).append(",");
            sb.append(escapeCsvField(t.getType().name())).append(",");
            sb.append(escapeCsvField(t.getCategory().getName())).append(",");
            sb.append(escapeCsvField(t.getAmount().toPlainString())).append(",");
            sb.append(escapeCsvField(t.getDescription())).append(",");
            sb.append(escapeCsvField(t.getPaymentMethod().name())).append(",");
            sb.append(escapeCsvField(t.getNotes() != null ? t.getNotes() : "")).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Sanitizes and escapes a CSV field value.
     * Prevents formula/CSV injection by prepending a single quote if the field begins with =, +, -, @.
     * Escapes quotes, commas, and newlines properly according to RFC 4180.
     *
     * @param value field string
     * @return sanitized and quoted CSV string
     */
    public String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }

        String sanitized = value;
        // Prevent CSV / Formula Injection
        if (!sanitized.isEmpty()) {
            char firstChar = sanitized.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@') {
                sanitized = "'" + sanitized;
            }
        }

        // Check if quoting is needed
        boolean containsSpecialChar = sanitized.contains(",") ||
                                      sanitized.contains("\"") ||
                                      sanitized.contains("\n") ||
                                      sanitized.contains("\r") ||
                                      sanitized.startsWith("'");

        if (containsSpecialChar) {
            sanitized = sanitized.replace("\"", "\"\"");
            return "\"" + sanitized + "\"";
        }

        return sanitized;
    }
}
