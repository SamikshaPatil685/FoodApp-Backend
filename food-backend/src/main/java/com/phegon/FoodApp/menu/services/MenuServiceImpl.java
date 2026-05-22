package com.phegon.FoodApp.menu.services;
import java.io.File;
import java.io.IOException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import com.phegon.FoodApp.category.entity.Category;
import com.phegon.FoodApp.category.repository.CategoryRepository;
import com.phegon.FoodApp.exceptions.BadRequestException;
import com.phegon.FoodApp.exceptions.NotFoundException;
import com.phegon.FoodApp.menu.dtos.MenuDTO;
import com.phegon.FoodApp.menu.entity.Menu;
import com.phegon.FoodApp.menu.repository.MenuRepository;
import com.phegon.FoodApp.response.Response;
import com.phegon.FoodApp.review.dtos.ReviewDTO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuServiceImpl implements MenuService {

    private final MenuRepository menuRepository;
    private final CategoryRepository categoryRepository;
    private final ModelMapper modelMapper;

    @Override
    public Response<MenuDTO> createMenu(MenuDTO menuDTO)  {

        log.info("Inside createMenu()");

        Category category = categoryRepository.findById(menuDTO.getCategoryId())
                .orElseThrow(() ->
                        new NotFoundException("Category not found with ID: " + menuDTO.getCategoryId()));


        MultipartFile imageFile = menuDTO.getImageFile();

        if (imageFile == null || imageFile.isEmpty()) {

            throw new BadRequestException(
                    "Menu image is required"
            );
        }

        // Upload folder
        String uploadDir = "uploads/";

        // Create folder if not exists
        File directory = new File(uploadDir);

        if (!directory.exists()) {

            directory.mkdirs();
        }

        // Unique filename
        String fileName =
                UUID.randomUUID() + "_" +
                        imageFile.getOriginalFilename();

        // File path
        Path filePath =
                Paths.get(uploadDir, fileName);

        // Save image
        try {

            Files.copy(
                    imageFile.getInputStream(),
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to save image",
                    e
            );
        }

        // Save URL in DB
        String imageUrl =
                "http://localhost:8090/uploads/" + fileName;

        log.info("Menu image uploaded successfully");

        Menu menu = Menu.builder()
                .name(menuDTO.getName())
                .description(menuDTO.getDescription())
                .price(menuDTO.getPrice())
                .imageUrl(imageUrl)
                .category(category)
                .build();

        Menu savedMenu = menuRepository.save(menu);

        return Response.<MenuDTO>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Menu created successfully")
                .data(modelMapper.map(savedMenu, MenuDTO.class))
                .build();
    }

    @Override
    public Response<MenuDTO> updateMenu(MenuDTO menuDTO)  {

        log.info("Inside updateMenu()");

        Menu existingMenu = menuRepository.findById(menuDTO.getId())
                .orElseThrow(() -> new NotFoundException("Menu not found"));

        Category category = categoryRepository.findById(menuDTO.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Category not found"));

        String imageUrl = existingMenu.getImageUrl();

        MultipartFile imageFile = menuDTO.getImageFile();

        if (imageFile != null && !imageFile.isEmpty()) {

            String uploadDir = "uploads/";

            File directory = new File(uploadDir);

            if (!directory.exists()) {

                directory.mkdirs();
            }

            String fileName =
                    UUID.randomUUID() + "_" +
                            imageFile.getOriginalFilename();

            Path filePath =
                    Paths.get(uploadDir, fileName);

            try {

                Files.copy(
                        imageFile.getInputStream(),
                        filePath,
                        StandardCopyOption.REPLACE_EXISTING
                );

            } catch (IOException e) {

                throw new RuntimeException(
                        "Failed to save image",
                        e
                );
            }

            imageUrl =
                    "http://localhost:8090/uploads/" + fileName;

            log.info("Updated menu image uploaded successfully");
        }


        if (menuDTO.getName() != null && !menuDTO.getName().isBlank()) {
            existingMenu.setName(menuDTO.getName());
        }

        if (menuDTO.getDescription() != null && !menuDTO.getDescription().isBlank()) {
            existingMenu.setDescription(menuDTO.getDescription());
        }

        if (menuDTO.getPrice() != null) {
            existingMenu.setPrice(menuDTO.getPrice());
        }

        existingMenu.setImageUrl(imageUrl);
        existingMenu.setCategory(category);

        Menu updatedMenu = menuRepository.save(existingMenu);

        return Response.<MenuDTO>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Menu updated successfully")
                .data(modelMapper.map(updatedMenu, MenuDTO.class))
                .build();
    }

    @Override
    public Response<MenuDTO> getMenuById(Long id) {

        log.info("Inside getMenuById()");

        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Menu not found"));

        MenuDTO menuDTO = modelMapper.map(menu, MenuDTO.class);

        if (menuDTO.getReviews() != null) {
            menuDTO.getReviews()
                    .sort(Comparator.comparing(ReviewDTO::getId).reversed());
        }

        return Response.<MenuDTO>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Menu retrieved successfully")
                .data(menuDTO)
                .build();
    }

    @Override
    public Response<?> deleteMenu(Long id) {

        log.info("Inside deleteMenu()");

        Menu menuToDelete = menuRepository.findById(id)
                .orElseThrow(() ->
                        new NotFoundException("Menu not found with ID: " + id));

        // Localhost version (No AWS S3)
        log.info("Deleting menu locally");

        menuRepository.deleteById(id);

        return Response.builder()
                .statusCode(HttpStatus.OK.value())
                .message("Menu deleted successfully")
                .build();
    }

    @Override
    public Response<List<MenuDTO>> getMenus(Long categoryId, String search) {

        log.info("Inside getMenus()");

        Specification<Menu> spec = buildSpecification(categoryId, search);

        Sort sort = Sort.by(Sort.Direction.DESC, "id");

        List<Menu> menuList = menuRepository.findAll(spec, sort);

        List<MenuDTO> menuDTOS = menuList.stream()
                .map(menu -> modelMapper.map(menu, MenuDTO.class))
                .toList();

        return Response.<List<MenuDTO>>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Menus retrieved")
                .data(menuDTOS)
                .build();
    }

    private Specification<Menu> buildSpecification(Long categoryId, String search) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (categoryId != null) {

                predicates.add(cb.equal(
                        root.get("category").get("id"),
                        categoryId
                ));
            }

            if (search != null && !search.isBlank()) {

                String searchTerm = "%" + search.toLowerCase() + "%";

                predicates.add(cb.or(
                        cb.like(
                                cb.lower(root.get("name")),
                                searchTerm
                        ),
                        cb.like(
                                cb.lower(root.get("description")),
                                searchTerm
                        )
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}