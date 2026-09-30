package com.example.urlshortener.controller;

import com.example.urlshortener.dto.ErrorResponse;
import com.example.urlshortener.exception.LinkGoneException;
import com.example.urlshortener.exception.ResourceNotFoundException;
import com.example.urlshortener.service.RedirectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Tag(name = "Redirect", description = "Public short link redirection endpoint")
public class RedirectController {

    private final RedirectService redirectService;

    public RedirectController(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    @GetMapping("/{code:[A-Za-z0-9_-]{1,32}}")
    @Operation(summary = "Redirect short link to original target URL")
    public ResponseEntity<?> redirect(
            @PathVariable String code,
            @RequestHeader(value = "Accept", required = false) String acceptHeader,
            HttpServletRequest request) {

        try {
            String targetUrl = redirectService.resolveAndTrackRedirect(code, request);

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(targetUrl));
            headers.setCacheControl("no-store, no-cache, must-revalidate, max-age=0");
            headers.set("Referrer-Policy", "no-referrer-when-downgrade");

            return new ResponseEntity<>(headers, HttpStatus.FOUND); // HTTP 302
        } catch (ResourceNotFoundException ex) {
            if (isHtmlRequest(acceptHeader)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<!DOCTYPE html><html><head><title>Link Not Found</title><style>body{font-family:sans-serif;text-align:center;padding:50px;background:#0f172a;color:#f8fafc;}h1{color:#ef4444;}.card{background:#1e293b;padding:30px;border-radius:12px;display:inline-block;max-width:500px;}</style></head><body><div class='card'><h1>404 - Link Not Found</h1><p>The short link you followed does not exist or has been removed.</p></div></body></html>");
            }
            ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI());
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        } catch (LinkGoneException ex) {
            if (isHtmlRequest(acceptHeader)) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<!DOCTYPE html><html><head><title>Link Expired or Inactive</title><style>body{font-family:sans-serif;text-align:center;padding:50px;background:#0f172a;color:#f8fafc;}h1{color:#f59e0b;}.card{background:#1e293b;padding:30px;border-radius:12px;display:inline-block;max-width:500px;}</style></head><body><div class='card'><h1>410 - Link Expired / Limit Reached</h1><p>" + ex.getMessage() + "</p></div></body></html>");
            }
            ErrorResponse error = new ErrorResponse(HttpStatus.GONE.value(), "Gone", ex.getMessage(), request.getRequestURI());
            return new ResponseEntity<>(error, HttpStatus.GONE);
        }
    }

    private boolean isHtmlRequest(String acceptHeader) {
        return acceptHeader != null && acceptHeader.contains("text/html");
    }
}
