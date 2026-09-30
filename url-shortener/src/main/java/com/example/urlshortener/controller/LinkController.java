package com.example.urlshortener.controller;

import com.example.urlshortener.dto.request.BulkCreateRequest;
import com.example.urlshortener.dto.request.CreateLinkRequest;
import com.example.urlshortener.dto.request.UpdateLinkRequest;
import com.example.urlshortener.dto.response.BulkCreateResponse;
import com.example.urlshortener.dto.response.LinkResponse;
import com.example.urlshortener.security.UserPrincipal;
import com.example.urlshortener.service.LinkService;
import com.example.urlshortener.service.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/links")
@Tag(name = "Short Links", description = "Endpoints for managing short URLs, QR codes, and bulk creation")
public class LinkController {

    private final LinkService linkService;
    private final QrCodeService qrCodeService;

    public LinkController(LinkService linkService, QrCodeService qrCodeService) {
        this.linkService = linkService;
        this.qrCodeService = qrCodeService;
    }

    @PostMapping
    @Operation(summary = "Create a new short link")
    public ResponseEntity<LinkResponse> createLink(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                  @Valid @RequestBody CreateLinkRequest request) {
        LinkResponse response = linkService.createLink(userPrincipal.getId(), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get paginated short links for authenticated user")
    public ResponseEntity<Page<LinkResponse>> getUserLinks(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean expired) {
        Page<LinkResponse> links = linkService.getUserLinks(userPrincipal.getId(), page, size, search, active, expired);
        return ResponseEntity.ok(links);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get link details by ID")
    public ResponseEntity<LinkResponse> getLinkById(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                     @PathVariable Long id) {
        LinkResponse response = linkService.getLinkById(userPrincipal.getId(), id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing short link")
    public ResponseEntity<LinkResponse> updateLink(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody UpdateLinkRequest request) {
        LinkResponse response = linkService.updateLink(userPrincipal.getId(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a short link")
    public ResponseEntity<Void> deleteLink(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                           @PathVariable Long id) {
        linkService.deleteLink(userPrincipal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/qr")
    @Operation(summary = "Generate QR code PNG image for a short link")
    public ResponseEntity<byte[]> getLinkQrCode(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                @PathVariable Long id,
                                                @RequestParam(defaultValue = "256") int size) {
        LinkResponse link = linkService.getLinkById(userPrincipal.getId(), id);
        byte[] qrBytes = qrCodeService.generateQrCodePng(link.shortUrl(), size);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(qrBytes.length);
        headers.setCacheControl("public, max-age=86400");
        return new ResponseEntity<>(qrBytes, headers, HttpStatus.OK);
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk create up to 50 short links")
    public ResponseEntity<BulkCreateResponse> createBulkLinks(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                              @Valid @RequestBody BulkCreateRequest request) {
        BulkCreateResponse response = linkService.createBulkLinks(userPrincipal.getId(), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
