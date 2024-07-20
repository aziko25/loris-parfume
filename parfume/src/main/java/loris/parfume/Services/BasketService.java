package loris.parfume.Services;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import loris.parfume.DTOs.returnDTOs.ItemsDTO;
import loris.parfume.Models.Basket;
import loris.parfume.Models.Items.Items;
import loris.parfume.Models.Items.Sizes;
import loris.parfume.Models.Users;
import loris.parfume.Repositories.BasketsRepository;
import loris.parfume.Repositories.Items.ItemsRepository;
import loris.parfume.Repositories.Items.SizesRepository;
import loris.parfume.Repositories.UsersRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import static loris.parfume.Configurations.JWT.AuthorizationMethods.USER_ID;
import static loris.parfume.Services.Items.SizesService.DEFAULT_NO_SIZE;

@Service
@RequiredArgsConstructor
public class BasketService {

    private final BasketsRepository basketsRepository;

    private final UsersRepository usersRepository;
    private final ItemsRepository itemsRepository;
    private final SizesRepository sizesRepository;

    @Value("${pageSize}")
    private Integer pageSize;

    public String add(Long itemId, Long sizeId, Integer quantity) {

        Users user = usersRepository.findById(USER_ID).orElseThrow(() -> new EntityNotFoundException("User Not Found"));
        Items item = itemsRepository.findById(itemId).orElseThrow(() -> new EntityNotFoundException("Item Not Found"));

        Sizes size = sizesRepository.findById(DEFAULT_NO_SIZE).orElseThrow(() -> new EntityNotFoundException("Default Size Not Found"));
        if (sizeId != null) {

            size = sizesRepository.findById(sizeId).orElseThrow(() -> new EntityNotFoundException("Size Not Found"));
        }

        basketsRepository.save(new Basket(user, item, size, quantity));

        return "Successfully Added To Basket";
    }

    public Page<ItemsDTO> all(Integer page) {

        Users user = usersRepository.findById(USER_ID).orElseThrow(() -> new EntityNotFoundException("User Not Found"));

        Pageable pageable = PageRequest.of(page - 1, pageSize);

        return basketsRepository.findAllByUser(user, pageable).map(basket -> new ItemsDTO(basket.getItem()));
    }

    @Transactional
    public String remove(Long itemId) {

        Users user = usersRepository.findById(USER_ID).orElseThrow(() -> new EntityNotFoundException("User Not Found"));
        Items item = itemsRepository.findById(itemId).orElseThrow(() -> new EntityNotFoundException("Item Not Found"));

        basketsRepository.deleteByUserAndItem(user, item);

        return "Successfully Deleted";
    }

    @Transactional
    public String clear() {

        Users user = usersRepository.findById(USER_ID).orElseThrow(() -> new EntityNotFoundException("User Not Found"));

        basketsRepository.deleteAllByUser(user);

        return "Successfully Cleared The Cart";
    }
}