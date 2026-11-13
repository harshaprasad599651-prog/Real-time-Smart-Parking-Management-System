package com.smartpark.server.controller;

import com.smartpark.server.dto.CreateVehicleCategoryRequest;
import com.smartpark.server.dto.UpdateVehicleCategoryRequest;
import com.smartpark.server.dto.VehicleCategoryDto;
import com.smartpark.server.security.AuthenticatedOperator;
import com.smartpark.server.service.VehicleCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Vehicle categories", description = "Vehicle categories and the slot types they may use")
public class VehicleCategoryController {

    private final VehicleCategoryService categoryService;
    private final OperatorGuard operatorGuard;

    public VehicleCategoryController(VehicleCategoryService categoryService, OperatorGuard operatorGuard) {
        this.categoryService = categoryService;
        this.operatorGuard = operatorGuard;
    }

    @GetMapping
    @Operation(summary = "List all vehicle categories", description = "Sorted by name.")
    public List<VehicleCategoryDto> list() {
        return categoryService.listCategories();
    }

    @GetMapping("/{categoryName}")
    @Operation(summary = "Get one vehicle category")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.NotFound
    public VehicleCategoryDto get(@PathVariable String categoryName) {
        return categoryService.getCategory(categoryName);
    }

    @PostMapping
    @Operation(summary = "Add a vehicle category (ADMIN)",
            description = "The name is normalised: \"Electric car\" becomes ELECTRIC_CAR.")
    @ApiResponse(responseCode = "201", description = "Created; Location header points to the new category")
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.Conflict
    public ResponseEntity<VehicleCategoryDto> create(
            @Parameter(hidden = true) AuthenticatedOperator operator,
            @Valid @RequestBody CreateVehicleCategoryRequest request) {
        String admin = operatorGuard.requireAdmin(operator);
        VehicleCategoryDto created = categoryService.createCategory(request, admin);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{name}")
                .buildAndExpand(created.categoryName()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{categoryName}")
    @Operation(summary = "Update a vehicle category (ADMIN)",
            description = "Replaces description and compatible slot types. Slots of newly compatible types start "
                    + "accepting the category; slots of removed types stop accepting it. The name cannot change.")
    @ApiErrorResponses.Ok
    @ApiErrorResponses.BadRequest
    @ApiErrorResponses.Forbidden
    @ApiErrorResponses.NotFound
    public VehicleCategoryDto update(
            @Parameter(hidden = true) AuthenticatedOperator operator,
            @PathVariable String categoryName,
            @Valid @RequestBody UpdateVehicleCategoryRequest request) {
        String admin = operatorGuard.requireAdmin(operator);
        return categoryService.updateCategory(categoryName, request, admin);
    }
}
