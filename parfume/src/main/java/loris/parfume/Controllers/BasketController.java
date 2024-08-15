package loris.parfume.Controllers;

import lombok.RequiredArgsConstructor;
import loris.parfume.Configurations.JWT.Authorization;
import loris.parfume.Services.BasketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/basket")
@RequiredArgsConstructor
@CrossOrigin(maxAge = 3600)
public class BasketController {

    private final BasketService basketService;

    @Authorization(requiredRoles = {"ADMIN", "USER"})
    @PostMapping("/add/{id}")
    public ResponseEntity<?> add(@PathVariable Long id, @RequestParam(required = false) Long sizeId,
                                 @RequestParam Long collectionId,
                                 @RequestParam Integer quantity) {

        return ResponseEntity.ok(basketService.add(id, sizeId, collectionId, quantity));
    }

    @Authorization(requiredRoles = {"ADMIN", "USER"})
    @PostMapping("/all")
    public ResponseEntity<?> all() {

        return ResponseEntity.ok(basketService.all());
    }

    @Authorization(requiredRoles = {"ADMIN", "USER"})
    @DeleteMapping("/remove/{id}")
    public ResponseEntity<?> remove(@PathVariable Long id, @RequestParam(required = false) Long sizeId) {

        return ResponseEntity.ok(basketService.remove(id, sizeId));
    }

    @Authorization(requiredRoles = {"ADMIN", "USER"})
    @DeleteMapping("/clear")
    public ResponseEntity<?> clear() {

        return ResponseEntity.ok(basketService.clear());
    }
}